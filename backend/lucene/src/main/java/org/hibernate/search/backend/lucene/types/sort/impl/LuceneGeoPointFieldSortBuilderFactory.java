/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.backend.lucene.types.sort.impl;

import java.lang.invoke.MethodHandles;

import org.hibernate.search.backend.lucene.logging.impl.Log;
import org.hibernate.search.backend.lucene.search.impl.LuceneSearchContext;
import org.hibernate.search.backend.lucene.search.sort.impl.LuceneSearchSortBuilder;
import org.hibernate.search.engine.reporting.spi.EventContexts;
import org.hibernate.search.engine.search.predicate.spi.DslConverter;
import org.hibernate.search.engine.search.sort.spi.DistanceSortBuilder;
import org.hibernate.search.engine.search.sort.spi.FieldSortBuilder;
import org.hibernate.search.engine.spatial.GeoPoint;
import org.hibernate.search.util.common.logging.impl.LoggerFactory;

public class LuceneGeoPointFieldSortBuilderFactory implements LuceneFieldSortBuilderFactory {

	private static final Log log = LoggerFactory.make( Log.class, MethodHandles.lookup() );

	private final boolean sortable;

	public LuceneGeoPointFieldSortBuilderFactory(boolean sortable) {
		this.sortable = sortable;
	}

	@Override
	public FieldSortBuilder<LuceneSearchSortBuilder> createFieldSortBuilder(
			LuceneSearchContext searchContext, String absoluteFieldPath, DslConverter dslConverter) {
		throw log.traditionalSortNotSupportedByGeoPoint(
				EventContexts.fromIndexFieldAbsolutePath( absoluteFieldPath ) );
	}

	@Override
	public DistanceSortBuilder<LuceneSearchSortBuilder> createDistanceSortBuilder(String absoluteFieldPath,
			GeoPoint center) {
		checkSortable( absoluteFieldPath );

		return new LuceneGeoPointDistanceSortBuilder( absoluteFieldPath, center );
	}

	@Override
	public boolean isDslCompatibleWith(LuceneFieldSortBuilderFactory obj, DslConverter dslConverter) {
		if ( obj.getClass() != this.getClass() ) {
			return false;
		}

		LuceneGeoPointFieldSortBuilderFactory other = (LuceneGeoPointFieldSortBuilderFactory) obj;

		return other.sortable == this.sortable;
	}

	protected void checkSortable(String absoluteFieldPath) {
		if ( !sortable ) {
			throw log.unsortableField( absoluteFieldPath,
					EventContexts.fromIndexFieldAbsolutePath( absoluteFieldPath ) );
		}
	}
}