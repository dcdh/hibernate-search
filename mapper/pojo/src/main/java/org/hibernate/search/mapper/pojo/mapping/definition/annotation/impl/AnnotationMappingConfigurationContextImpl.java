/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.mapper.pojo.mapping.definition.annotation.impl;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import org.hibernate.search.engine.mapper.mapping.spi.MappingBuildContext;
import org.hibernate.search.engine.mapper.mapping.building.spi.MappingConfigurationCollector;
import org.hibernate.search.engine.mapper.mapping.building.spi.TypeMetadataDiscoverer;
import org.hibernate.search.engine.mapper.model.spi.MappableTypeModel;
import org.hibernate.search.mapper.pojo.reporting.impl.PojoEventContexts;
import org.hibernate.search.mapper.pojo.mapping.building.spi.PojoTypeMetadataContributor;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.AnnotationMappingConfigurationContext;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.hibernate.search.mapper.pojo.mapping.spi.PojoMappingConfigurationContributor;
import org.hibernate.search.mapper.pojo.model.spi.PojoBootstrapIntrospector;
import org.hibernate.search.mapper.pojo.model.spi.PojoRawTypeModel;
import org.hibernate.search.engine.reporting.spi.FailureCollector;

public class AnnotationMappingConfigurationContextImpl implements AnnotationMappingConfigurationContext,
		PojoMappingConfigurationContributor {

	private final PojoBootstrapIntrospector introspector;
	// Use a LinkedHashSet for deterministic iteration
	private final Set<Class<?>> annotatedTypes = new LinkedHashSet<>();

	private boolean annotatedTypeDiscoveryEnabled = false;

	public AnnotationMappingConfigurationContextImpl(PojoBootstrapIntrospector introspector) {
		this.introspector = introspector;
	}

	public void setAnnotatedTypeDiscoveryEnabled(boolean annotatedTypeDiscoveryEnabled) {
		this.annotatedTypeDiscoveryEnabled = annotatedTypeDiscoveryEnabled;
	}

	@Override
	public AnnotationMappingConfigurationContext add(Class<?> annotatedType) {
		this.annotatedTypes.add( annotatedType );
		return this;
	}

	@Override
	public AnnotationMappingConfigurationContext add(Set<Class<?>> annotatedTypes) {
		this.annotatedTypes.addAll( annotatedTypes );
		return this;
	}

	@Override
	public void configure(MappingBuildContext buildContext,
			MappingConfigurationCollector<PojoTypeMetadataContributor> collector) {
		FailureCollector failureCollector = buildContext.getFailureCollector();
		AnnotationProcessorProvider annotationProcessorProvider =
				new AnnotationProcessorProvider( failureCollector );
		AnnotationPojoTypeMetadataContributorFactory contributorFactory =
				new AnnotationPojoTypeMetadataContributorFactory( annotationProcessorProvider );

		/*
		 * For types that were explicitly requested for annotation scanning and their supertypes,
		 * map the types to indexes if necessary, and add a metadata contributor.
		 */
		Set<PojoRawTypeModel<?>> alreadyContributedTypes = new HashSet<>();
		annotatedTypes.stream()
				.map( introspector::getTypeModel )
				/*
				 * Take super types into account
				 * Note: the order of super types (ascending or descending) does not matter here.
				 */
				.flatMap( PojoRawTypeModel::getAscendingSuperTypes )
				// Ignore types that were already contributed
				.filter( alreadyContributedTypes::add )
				// TODO optimize by completely ignoring standard Java types, e.g. Object or standard Java interfaces such as Serializable?
				.forEach( typeModel -> {
					Optional<Indexed> indexedAnnotation = typeModel.getAnnotationByType( Indexed.class );
					if ( indexedAnnotation.isPresent() ) {
						String backendName = indexedAnnotation.get().backend();
						String defaultedIndexName = indexedAnnotation.get().index();
						if ( defaultedIndexName.isEmpty() ) {
							defaultedIndexName = typeModel.getJavaClass().getName();
						}
						try {
							collector.mapToIndex( typeModel, backendName, defaultedIndexName );
						}
						catch (RuntimeException e) {
							failureCollector.withContext( PojoEventContexts.fromType( typeModel ) )
									.withContext( PojoEventContexts.fromAnnotation( indexedAnnotation.get() ) )
									.add( e );
						}
					}

					Optional<PojoTypeMetadataContributor> contributorOptional =
							contributorFactory.createIfAnnotated( typeModel );
					if ( contributorOptional.isPresent() ) {
						collector.collectContributor( typeModel, contributorOptional.get() );
					}
				} );

		/*
		 * If automatic discovery of annotated types is enabled,
		 * also add a discoverer for new types (e.g. types encountered in an @IndexedEmbedded).
		 */
		if ( annotatedTypeDiscoveryEnabled ) {
			PojoAnnotationTypeMetadataDiscoverer discoverer =
					new PojoAnnotationTypeMetadataDiscoverer( contributorFactory, alreadyContributedTypes );
			collector.collectDiscoverer( discoverer );
		}
	}

	/**
	 * A type metadata discoverer that will provide annotation-based metadata
	 * for types that were not explicitly requested .
	 */
	private static class PojoAnnotationTypeMetadataDiscoverer implements TypeMetadataDiscoverer<PojoTypeMetadataContributor> {
		private final AnnotationPojoTypeMetadataContributorFactory contributorFactory;
		private final Set<PojoRawTypeModel<?>> alreadyContributedTypes;

		PojoAnnotationTypeMetadataDiscoverer(AnnotationPojoTypeMetadataContributorFactory contributorFactory,
				Set<PojoRawTypeModel<?>> alreadyContributedTypes) {
			this.contributorFactory = contributorFactory;
			this.alreadyContributedTypes = alreadyContributedTypes;
		}

		@Override
		public Optional<PojoTypeMetadataContributor> discover(MappableTypeModel typeModel) {
			PojoRawTypeModel<?> pojoTypeModel = (PojoRawTypeModel<?>) typeModel;
			/*
			 * Take care of not adding duplicate contributors: this could lead to mapping errors,
			 * for instance a field being declared twice.
			 */
			boolean neverContributed = alreadyContributedTypes.add( pojoTypeModel );
			if ( neverContributed ) {
				// TODO optimize by completely ignoring standard Java types, e.g. Object or standard Java interfaces such as Serializable?
				return contributorFactory.createIfAnnotated( pojoTypeModel );
			}
			else {
				return Optional.empty();
			}
		}
	}
}
