/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.integrationtest.backend.tck.search.predicate;

import static org.hibernate.search.util.impl.integrationtest.common.assertion.SearchResultAssert.assertThat;
import static org.hibernate.search.util.impl.integrationtest.common.stub.mapper.StubMapperUtils.referenceProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.hibernate.search.engine.backend.document.DocumentElement;
import org.hibernate.search.engine.backend.document.IndexFieldAccessor;
import org.hibernate.search.engine.backend.document.model.dsl.IndexSchemaElement;
import org.hibernate.search.engine.backend.index.spi.IndexWorkPlan;
import org.hibernate.search.engine.backend.types.dsl.IndexFieldTypeFactoryContext;
import org.hibernate.search.engine.backend.types.dsl.StandardIndexFieldTypeContext;
import org.hibernate.search.engine.reporting.spi.EventContexts;
import org.hibernate.search.engine.search.DocumentReference;
import org.hibernate.search.engine.search.SearchQuery;
import org.hibernate.search.integrationtest.backend.tck.testsupport.configuration.DefaultAnalysisDefinitions;
import org.hibernate.search.integrationtest.backend.tck.testsupport.types.FieldModelConsumer;
import org.hibernate.search.integrationtest.backend.tck.testsupport.types.FieldTypeDescriptor;
import org.hibernate.search.integrationtest.backend.tck.testsupport.util.StandardFieldMapper;
import org.hibernate.search.integrationtest.backend.tck.testsupport.util.ValueWrapper;
import org.hibernate.search.integrationtest.backend.tck.testsupport.util.rule.SearchSetupHelper;
import org.hibernate.search.util.common.SearchException;
import org.hibernate.search.util.impl.integrationtest.common.FailureReportUtils;
import org.hibernate.search.util.impl.integrationtest.common.stub.mapper.StubMappingIndexManager;
import org.hibernate.search.util.impl.integrationtest.common.stub.mapper.StubMappingSearchTarget;
import org.hibernate.search.util.impl.test.SubTest;
import org.hibernate.search.util.impl.test.annotation.PortedFromSearch5;
import org.hibernate.search.util.impl.test.annotation.TestForIssue;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class SimpleQueryStringSearchPredicateIT {

	private static final String INDEX_NAME = "IndexName";
	private static final String COMPATIBLE_INDEX_NAME = "IndexWithCompatibleFields";
	private static final String RAW_FIELD_COMPATIBLE_INDEX_NAME = "IndexWithCompatibleRawFields";
	private static final String INCOMPATIBLE_INDEX_NAME = "IndexWithIncompatibleFields";

	private static final String DOCUMENT_1 = "document1";
	private static final String DOCUMENT_2 = "document2";
	private static final String DOCUMENT_3 = "document3";
	private static final String DOCUMENT_4 = "document4";
	private static final String DOCUMENT_5 = "document5";
	private static final String EMPTY = "empty";

	private static final String TERM_1 = "word";
	private static final String TERM_2 = "panda";
	private static final String TERM_3 = "room";
	private static final String TERM_4 = "elephant john";
	private static final String PHRASE_WITH_TERM_2 = "panda breeding";
	private static final String PHRASE_WITH_TERM_4 = "elephant john";
	private static final String TEXT_TERM_1_AND_TERM_2 = "Here I was, feeding my panda, and the crowd had no word.";
	private static final String TEXT_TERM_1_AND_TERM_3 = "Without a word, he went out of the room.";
	private static final String TEXT_TERM_2_IN_PHRASE = "I admired her for her panda breeding expertise.";
	private static final String TEXT_TERM_4_IN_PHRASE_SLOP_2 = "An elephant ran past John.";
	private static final String TEXT_TERM_1_EDIT_DISTANCE_1 = "I came to the world in a dumpster.";

	private static final String COMPATIBLE_INDEX_DOCUMENT_1 = "compatible_1";
	private static final String RAW_FIELD_COMPATIBLE_INDEX_DOCUMENT_1 = "raw_field_compatible_1";

	@Rule
	public SearchSetupHelper setupHelper = new SearchSetupHelper();

	private IndexMapping indexMapping;
	private StubMappingIndexManager indexManager;

	private OtherIndexMapping compatibleIndexMapping;
	private StubMappingIndexManager compatibleIndexManager;

	private OtherIndexMapping rawFieldCompatibleIndexMapping;
	private StubMappingIndexManager rawFieldCompatibleIndexManager;

	private StubMappingIndexManager incompatibleIndexManager;

	@Before
	public void setup() {
		setupHelper.withDefaultConfiguration()
				.withIndex(
						INDEX_NAME,
						ctx -> this.indexMapping = new IndexMapping( ctx.getSchemaElement() ),
						indexManager -> this.indexManager = indexManager
				)
				.withIndex(
						COMPATIBLE_INDEX_NAME,
						ctx -> this.compatibleIndexMapping =
								OtherIndexMapping.createCompatible( ctx.getSchemaElement() ),
						indexManager -> this.compatibleIndexManager = indexManager
				)
				.withIndex(
						RAW_FIELD_COMPATIBLE_INDEX_NAME,
						ctx -> this.rawFieldCompatibleIndexMapping =
								OtherIndexMapping.createRawFieldCompatible( ctx.getSchemaElement() ),
						indexManager -> this.rawFieldCompatibleIndexManager = indexManager
				)
				.withIndex(
						INCOMPATIBLE_INDEX_NAME,
						ctx -> OtherIndexMapping.createIncompatible( ctx.getSchemaElement() ),
						indexManager -> this.incompatibleIndexManager = indexManager
				)
				.setup();

		initData();
	}

	@Test
	@TestForIssue(jiraKey = "HSEARCH-2678")
	@PortedFromSearch5(original = "org.hibernate.search.test.dsl.SimpleQueryStringDSLTest.testSimpleQueryString")
	public void simpleQueryString() {
		StubMappingSearchTarget searchTarget = indexManager.createSearchTarget();
		String absoluteFieldPath = indexMapping.analyzedStringField1.relativeFieldName;
		Function<String, SearchQuery<DocumentReference>> createQuery = queryString -> searchTarget.query().asReference()
				.predicate( f -> f.simpleQueryString().onField( absoluteFieldPath ).matching( queryString ) )
				.build();

		assertThat( createQuery.apply( TERM_1 + " " + TERM_2 ) )
				.hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_1, DOCUMENT_2, DOCUMENT_3 );

		assertThat( createQuery.apply( TERM_1 + " | " + TERM_2 ) )
				.hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_1, DOCUMENT_2, DOCUMENT_3 );

		assertThat( createQuery.apply( TERM_1 + " + " + TERM_2 ) )
				.hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_1 );

		assertThat( createQuery.apply( "-" + TERM_1 + " + " + TERM_2 ) )
				.hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_3 );

		assertThat( createQuery.apply( TERM_1 + " + -" + TERM_2 ) )
				.hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_2 );
	}

	@Test
	@TestForIssue(jiraKey = "HSEARCH-2678")
	@PortedFromSearch5(original = "org.hibernate.search.test.dsl.SimpleQueryStringDSLTest.testSimpleQueryString")
	public void withAndAsDefaultOperator() {
		StubMappingSearchTarget searchTarget = indexManager.createSearchTarget();
		String absoluteFieldPath = indexMapping.analyzedStringField1.relativeFieldName;
		SearchQuery<DocumentReference> query;

		query = searchTarget.query().asReference()
				.predicate( f -> f.simpleQueryString().onField( absoluteFieldPath )
						.matching( TERM_1 + " " + TERM_2 ) )
				.build();
		assertThat( query )
				.hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_1, DOCUMENT_2, DOCUMENT_3 );

		query = searchTarget.query().asReference()
				.predicate( f -> f.simpleQueryString().onField( absoluteFieldPath ).withAndAsDefaultOperator()
						.matching( TERM_1 + " " + TERM_2 ) )
				.build();
		assertThat( query )
				.hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_1 );
	}

	/**
	 * Check that a simple query string predicate can be used on a field that has a DSL converter.
	 * The DSL converter should be ignored, and there shouldn't be any exception thrown
	 * (the field should be considered as a text field).
	 */
	@Test
	public void withDslConverter() {
		StubMappingSearchTarget searchTarget = indexManager.createSearchTarget();
		String absoluteFieldPath = indexMapping.analyzedStringFieldWithDslConverter.relativeFieldName;

		SearchQuery<DocumentReference> query = searchTarget.query()
				.asReference()
				.predicate( f -> f.simpleQueryString().onField( absoluteFieldPath ).matching( TERM_1 ) )
				.build();

		assertThat( query )
				.hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_1 );
	}

	@Test
	@TestForIssue(jiraKey = "HSEARCH-2700")
	@PortedFromSearch5(original = "org.hibernate.search.test.dsl.SimpleQueryStringDSLTest.testEmptyQueryString")
	public void emptyStringBeforeAnalysis() {
		StubMappingSearchTarget searchTarget = indexManager.createSearchTarget();
		MainFieldModel fieldModel = indexMapping.analyzedStringField1;

		SearchQuery<DocumentReference> query = searchTarget.query()
				.asReference()
				.predicate( f -> f.simpleQueryString().onField( fieldModel.relativeFieldName ).matching( "" ) )
				.build();

		assertThat( query )
				.hasNoHits();
	}

	@Test
	@TestForIssue(jiraKey = "HSEARCH-2700")
	@PortedFromSearch5(original = "org.hibernate.search.test.dsl.SimpleQueryStringDSLTest.testBlankQueryString")
	public void blankStringBeforeAnalysis() {
		StubMappingSearchTarget searchTarget = indexManager.createSearchTarget();
		MainFieldModel fieldModel = indexMapping.analyzedStringField1;

		SearchQuery<DocumentReference> query = searchTarget.query()
				.asReference()
				.predicate( f -> f.simpleQueryString().onField( fieldModel.relativeFieldName ).matching( "   " ) )
				.build();

		assertThat( query )
				.hasNoHits();
	}

	@Test
	public void noTokenAfterAnalysis() {
		StubMappingSearchTarget searchTarget = indexManager.createSearchTarget();
		MainFieldModel fieldModel = indexMapping.analyzedStringField1;

		SearchQuery<DocumentReference> query = searchTarget.query()
				.asReference()
				// Use stopwords, which should be removed by the analysis
				.predicate( f -> f.simpleQueryString().onField( fieldModel.relativeFieldName ).matching( "the a" ) )
				.build();

		assertThat( query )
				.hasNoHits();
	}

	@Test
	public void error_unsupportedFieldType() {
		StubMappingSearchTarget searchTarget = indexManager.createSearchTarget();

		for ( ByTypeFieldModel fieldModel : indexMapping.unsupportedFieldModels ) {
			String absoluteFieldPath = fieldModel.relativeFieldName;

			SubTest.expectException(
					"simpleQueryString() predicate with unsupported type on field " + absoluteFieldPath,
					() -> searchTarget.predicate().simpleQueryString().onField( absoluteFieldPath )
			)
					.assertThrown()
					.isInstanceOf( SearchException.class )
					.hasMessageContaining( "Text predicates" )
					.hasMessageContaining( "are not supported by" )
					.hasMessageContaining( "'" + absoluteFieldPath + "'" )
					.satisfies( FailureReportUtils.hasContext(
							EventContexts.fromIndexFieldAbsolutePath( absoluteFieldPath )
					) );
		}
	}

	@Test
	@TestForIssue(jiraKey = "HSEARCH-2700")
	@PortedFromSearch5(original = "org.hibernate.search.test.dsl.SimpleQueryStringDSLTest.testNullQueryString")
	public void error_null() {
		StubMappingSearchTarget searchTarget = indexManager.createSearchTarget();
		String absoluteFieldPath = indexMapping.analyzedStringField1.relativeFieldName;

		SubTest.expectException(
				"simpleQueryString() predicate with null value to match",
				() -> searchTarget.predicate().simpleQueryString().onField( absoluteFieldPath ).matching( null )
		)
				.assertThrown()
				.isInstanceOf( SearchException.class )
				.hasMessageContaining( "Invalid simple query string" )
				.hasMessageContaining( "must be non-null" )
				.hasMessageContaining( absoluteFieldPath );
	}

	@Test
	@TestForIssue(jiraKey = "HSEARCH-2678")
	@PortedFromSearch5(original = "org.hibernate.search.test.dsl.SimpleQueryStringDSLTest.testSimpleQueryString")
	public void phrase() {
		StubMappingSearchTarget searchTarget = indexManager.createSearchTarget();
		String absoluteFieldPath = indexMapping.analyzedStringField1.relativeFieldName;
		Function<String, SearchQuery<DocumentReference>> createQuery = queryString -> searchTarget.query().asReference()
				.predicate( f -> f.simpleQueryString().onField( absoluteFieldPath ).matching( queryString ) )
				.build();

		assertThat( createQuery.apply( "\"" + PHRASE_WITH_TERM_2 + "\"" ) )
				.hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_3 );

		assertThat( createQuery.apply( TERM_3 + " \"" + PHRASE_WITH_TERM_2 + "\"" ) )
				.hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_2, DOCUMENT_3 );

		// Slop
		assertThat( createQuery.apply( "\"" + PHRASE_WITH_TERM_4 + "\"" ) )
				.hasNoHits();
		assertThat( createQuery.apply( "\"" + PHRASE_WITH_TERM_4 + "\"~1" ) )
				.hasNoHits();
		assertThat( createQuery.apply( "\"" + PHRASE_WITH_TERM_4 + "\"~2" ) )
				.hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_4 );
		assertThat( createQuery.apply( "\"" + PHRASE_WITH_TERM_4 + "\"~3" ) )
				.hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_4 );
	}

	@Test
	@TestForIssue(jiraKey = "HSEARCH-2678")
	@PortedFromSearch5(original = "org.hibernate.search.test.dsl.SimpleQueryStringDSLTest.testBoost")
	public void fieldLevelBoost() {
		StubMappingSearchTarget searchTarget = indexManager.createSearchTarget();
		String absoluteFieldPath1 = indexMapping.analyzedStringField1.relativeFieldName;
		String absoluteFieldPath2 = indexMapping.analyzedStringField2.relativeFieldName;
		SearchQuery<DocumentReference> query;

		query = searchTarget.query().asReference()
				.predicate( f -> f.simpleQueryString()
						.onField( absoluteFieldPath1 ).boostedTo( 5f )
						.orField( absoluteFieldPath2 )
						.matching( TERM_3 )
				)
				.sort( f -> f.byScore() )
				.build();
		assertThat( query )
				.hasDocRefHitsExactOrder( INDEX_NAME, DOCUMENT_2, DOCUMENT_1 );

		query = searchTarget.query().asReference()
				.predicate( f -> f.simpleQueryString()
						.onField( absoluteFieldPath1 )
						.orField( absoluteFieldPath2 ).boostedTo( 5f )
						.matching( TERM_3 )
				)
				.sort( f -> f.byScore() )
				.build();
		assertThat( query )
				.hasDocRefHitsExactOrder( INDEX_NAME, DOCUMENT_1, DOCUMENT_2 );
	}

	@Test
	public void predicateLevelBoost() {
		StubMappingSearchTarget searchTarget = indexManager.createSearchTarget();
		String absoluteFieldPath1 = indexMapping.analyzedStringField1.relativeFieldName;
		String absoluteFieldPath2 = indexMapping.analyzedStringField2.relativeFieldName;

		SearchQuery<DocumentReference> query = searchTarget.query()
				.asReference()
				.predicate( f -> f.bool()
						.should( f.simpleQueryString().onField( absoluteFieldPath1 )
								.matching( TERM_3 )
						)
						.should( f.simpleQueryString().boostedTo( 7 ).onField( absoluteFieldPath2 )
								.matching( TERM_3 )
						)
				)
				.sort( c -> c.byScore() )
				.build();

		assertThat( query )
				.hasDocRefHitsExactOrder( INDEX_NAME, DOCUMENT_1, DOCUMENT_2 );

		query = searchTarget.query()
				.asReference()
				.predicate( f -> f.bool()
						.should( f.simpleQueryString().boostedTo( 39 ).onField( absoluteFieldPath1 )
								.matching( TERM_3 )
						)
						.should( f.simpleQueryString().onField( absoluteFieldPath2 )
								.matching( TERM_3 )
						)
				)
				.sort( c -> c.byScore() )
				.build();

		assertThat( query )
				.hasDocRefHitsExactOrder( INDEX_NAME, DOCUMENT_2, DOCUMENT_1 );
	}

	@Test
	public void predicateLevelBoost_withConstantScore() {
		StubMappingSearchTarget searchTarget = indexManager.createSearchTarget();
		String absoluteFieldPath1 = indexMapping.analyzedStringField1.relativeFieldName;
		String absoluteFieldPath2 = indexMapping.analyzedStringField2.relativeFieldName;

		SearchQuery<DocumentReference> query = searchTarget.query()
				.asReference()
				.predicate( f -> f.bool()
						.should( f.simpleQueryString().withConstantScore().boostedTo( 7 )
								.onField( absoluteFieldPath1 )
								.matching( TERM_3 )
						)
						.should( f.simpleQueryString().withConstantScore().boostedTo( 39 )
								.onField( absoluteFieldPath2 )
								.matching( TERM_3 )
						)
				)
				.sort( c -> c.byScore() )
				.build();

		assertThat( query )
				.hasDocRefHitsExactOrder( INDEX_NAME, DOCUMENT_1, DOCUMENT_2 );

		query = searchTarget.query()
				.asReference()
				.predicate( f -> f.bool()
						.should( f.simpleQueryString().withConstantScore().boostedTo( 39 )
								.onField( absoluteFieldPath1 )
								.matching( TERM_3 )
						)
						.should( f.simpleQueryString().withConstantScore().boostedTo( 7 )
								.onField( absoluteFieldPath2 )
								.matching( TERM_3 )
						)
				)
				.sort( c -> c.byScore() )
				.build();

		assertThat( query )
				.hasDocRefHitsExactOrder( INDEX_NAME, DOCUMENT_2, DOCUMENT_1 );
	}

	@Test
	@TestForIssue(jiraKey = "HSEARCH-2678")
	@PortedFromSearch5(original = "org.hibernate.search.test.dsl.SimpleQueryStringDSLTest.testFuzzy")
	public void fuzzy() {
		StubMappingSearchTarget searchTarget = indexManager.createSearchTarget();
		String absoluteFieldPath = indexMapping.analyzedStringField1.relativeFieldName;
		Function<String, SearchQuery<DocumentReference>> createQuery = queryString -> searchTarget.query().asReference()
				.predicate( f -> f.simpleQueryString().onField( absoluteFieldPath ).matching( queryString ) )
				.build();

		assertThat( createQuery.apply( TERM_1 ) )
				.hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_1, DOCUMENT_2 );

		assertThat( createQuery.apply( TERM_1 + "~1" ) )
				.hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_1, DOCUMENT_2, DOCUMENT_5 );

		assertThat( createQuery.apply( TERM_1 + "~2" ) )
				.hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_1, DOCUMENT_2, DOCUMENT_5 );
	}

	@Test
	@TestForIssue(jiraKey = "HSEARCH-2678")
	@PortedFromSearch5(original = "org.hibernate.search.test.dsl.SimpleQueryStringDSLTest.testFuzzy")
	public void analyzer() {
		// TODO HSEARCH-3312 implement this test once we allow to override the analyzer
		// See the original test in Search 5 for examples of use
		Assume.assumeTrue( "This feature is not implemented yet", false );
	}

	@Test
	public void multiFields() {
		StubMappingSearchTarget searchTarget = indexManager.createSearchTarget();
		String absoluteFieldPath1 = indexMapping.analyzedStringField1.relativeFieldName;
		String absoluteFieldPath2 = indexMapping.analyzedStringField2.relativeFieldName;
		String absoluteFieldPath3 = indexMapping.analyzedStringField3.relativeFieldName;
		Function<String, SearchQuery<DocumentReference>> createQuery;

		// onField(...)

		createQuery = query -> searchTarget.query().asReference()
				.predicate( f -> f.simpleQueryString().onField( absoluteFieldPath1 )
						.matching( query )
				)
				.build();

		assertThat( createQuery.apply( TERM_1 ) )
				.hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_1, DOCUMENT_2 );
		assertThat( createQuery.apply( TERM_2 ) )
				.hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_1, DOCUMENT_3 );
		assertThat( createQuery.apply( TERM_3 ) )
				.hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_2 );

		// onField(...).orField(...)

		createQuery = query -> searchTarget.query().asReference()
				.predicate( f -> f.simpleQueryString().onField( absoluteFieldPath1 )
						.orField( absoluteFieldPath2 )
						.matching( query )
				)
				.build();

		assertThat( createQuery.apply( TERM_1 ) )
				.hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_1, DOCUMENT_2 );
		assertThat( createQuery.apply( TERM_2 ) )
				.hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_1, DOCUMENT_3, DOCUMENT_4 );
		assertThat( createQuery.apply( TERM_3 ) )
				.hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_1, DOCUMENT_2 );

		// onField().orFields(...)

		createQuery = query -> searchTarget.query()
				.asReference()
				.predicate( f -> f.simpleQueryString().onField( absoluteFieldPath1 )
						.orFields( absoluteFieldPath2, absoluteFieldPath3 )
						.matching( query )
				)
				.build();

		assertThat( createQuery.apply( TERM_1 ) )
				.hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_1, DOCUMENT_2, DOCUMENT_5 );
		assertThat( createQuery.apply( TERM_2 ) )
				.hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_1, DOCUMENT_3, DOCUMENT_4, DOCUMENT_5 );
		assertThat( createQuery.apply( TERM_3 ) )
				.hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_1, DOCUMENT_2, DOCUMENT_5 );

		// onFields(...)

		createQuery = query -> searchTarget.query()
				.asReference()
				.predicate( f -> f.simpleQueryString().onFields( absoluteFieldPath1, absoluteFieldPath2 )
						.matching( query )
				)
				.build();

		assertThat( createQuery.apply( TERM_1 ) )
				.hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_1, DOCUMENT_2 );
		assertThat( createQuery.apply( TERM_2 ) )
				.hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_1, DOCUMENT_3, DOCUMENT_4 );
		assertThat( createQuery.apply( TERM_3 ) )
				.hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_1, DOCUMENT_2 );
	}

	@Test
	public void error_unknownField() {
		StubMappingSearchTarget searchTarget = indexManager.createSearchTarget();
		String absoluteFieldPath = indexMapping.analyzedStringField1.relativeFieldName;

		SubTest.expectException(
				"simpleQueryString() predicate with unknown field",
				() -> searchTarget.predicate().simpleQueryString().onField( "unknown_field" )
		)
				.assertThrown()
				.isInstanceOf( SearchException.class )
				.hasMessageContaining( "Unknown field" )
				.hasMessageContaining( "'unknown_field'" );

		SubTest.expectException(
				"simpleQueryString() predicate with unknown field",
				() -> searchTarget.predicate().simpleQueryString()
						.onFields( absoluteFieldPath, "unknown_field" )
		)
				.assertThrown()
				.isInstanceOf( SearchException.class )
				.hasMessageContaining( "Unknown field" )
				.hasMessageContaining( "'unknown_field'" );

		SubTest.expectException(
				"simpleQueryString() predicate with unknown field",
				() -> searchTarget.predicate().simpleQueryString().onField( absoluteFieldPath )
						.orField( "unknown_field" )
		)
				.assertThrown()
				.isInstanceOf( SearchException.class )
				.hasMessageContaining( "Unknown field" )
				.hasMessageContaining( "'unknown_field'" );

		SubTest.expectException(
				"simpleQueryString() predicate with unknown field",
				() -> searchTarget.predicate().simpleQueryString().onField( absoluteFieldPath )
						.orFields( "unknown_field" )
		)
				.assertThrown()
				.isInstanceOf( SearchException.class )
				.hasMessageContaining( "Unknown field" )
				.hasMessageContaining( "'unknown_field'" );
	}

	@Test
	public void multiIndex_withCompatibleIndexManager() {
		StubMappingSearchTarget searchTarget = indexManager.createSearchTarget(
				compatibleIndexManager
		);
		String absoluteFieldPath = indexMapping.analyzedStringField1.relativeFieldName;

		SearchQuery<DocumentReference> query = searchTarget.query()
				.asReference()
				.predicate( f -> f.simpleQueryString().onField( absoluteFieldPath ).matching( TERM_1 ) )
				.build();

		assertThat( query ).hasDocRefHitsAnyOrder( b -> {
			b.doc( INDEX_NAME, DOCUMENT_1, DOCUMENT_2 );
			b.doc( COMPATIBLE_INDEX_NAME, COMPATIBLE_INDEX_DOCUMENT_1 );
		} );
	}

	@Test
	public void multiIndex_withRawFieldCompatibleIndexManager() {
		StubMappingSearchTarget searchTarget = indexManager.createSearchTarget( rawFieldCompatibleIndexManager );
		String absoluteFieldPath = indexMapping.analyzedStringField1.relativeFieldName;

		SearchQuery<DocumentReference> query = searchTarget.query()
				.asReference()
				.predicate( f -> f.simpleQueryString().onField( absoluteFieldPath ).matching( TERM_1 ) )
				.build();

		assertThat( query ).hasDocRefHitsAnyOrder( b -> {
			b.doc( INDEX_NAME, DOCUMENT_1, DOCUMENT_2 );
			b.doc( RAW_FIELD_COMPATIBLE_INDEX_NAME, RAW_FIELD_COMPATIBLE_INDEX_DOCUMENT_1 );
		} );
	}

	@Test
	public void multiIndex_withIncompatibleIndexManager() {
		// TODO HSEARCH-3307 re-enable this test once we properly take analyzer/normalizer into account when testing field compatibility for predicates in Elasticsearch
		Assume.assumeTrue( "This feature is not implemented yet", false );
		String absoluteFieldPath = indexMapping.analyzedStringField1.relativeFieldName;

		SubTest.expectException(
				() -> {
					indexManager.createSearchTarget( incompatibleIndexManager )
							.predicate().simpleQueryString().onField( absoluteFieldPath );
				}
		)
				.assertThrown()
				.isInstanceOf( SearchException.class )
				.hasMessageContaining( "Multiple conflicting types to build a predicate" )
				.hasMessageContaining( "'" + absoluteFieldPath + "'" )
				.satisfies( FailureReportUtils.hasContext(
						EventContexts.fromIndexNames( INDEX_NAME, INCOMPATIBLE_INDEX_NAME )
				) );
	}

	private void initData() {
		IndexWorkPlan<? extends DocumentElement> workPlan = indexManager.createWorkPlan();
		workPlan.add( referenceProvider( DOCUMENT_1 ), document -> {
			indexMapping.analyzedStringField1.accessor.write( document, TEXT_TERM_1_AND_TERM_2 );
			indexMapping.analyzedStringFieldWithDslConverter.accessor.write( document, TEXT_TERM_1_AND_TERM_2 );
			indexMapping.analyzedStringField2.accessor.write( document, TEXT_TERM_1_AND_TERM_3 );
			indexMapping.analyzedStringField3.accessor.write( document, TERM_4 );
		} );
		workPlan.add( referenceProvider( DOCUMENT_2 ), document -> {
			indexMapping.analyzedStringField1.accessor.write( document, TEXT_TERM_1_AND_TERM_3 );
		} );
		workPlan.add( referenceProvider( DOCUMENT_3 ), document -> {
			indexMapping.analyzedStringField1.accessor.write( document, TEXT_TERM_2_IN_PHRASE );
		} );
		workPlan.add( referenceProvider( DOCUMENT_4 ), document -> {
			indexMapping.analyzedStringField1.accessor.write( document, TEXT_TERM_4_IN_PHRASE_SLOP_2 );
			indexMapping.analyzedStringField2.accessor.write( document, TEXT_TERM_2_IN_PHRASE );
		} );
		workPlan.add( referenceProvider( DOCUMENT_5 ), document -> {
			indexMapping.analyzedStringField1.accessor.write( document, TEXT_TERM_1_EDIT_DISTANCE_1 );
			indexMapping.analyzedStringField3.accessor.write( document, TEXT_TERM_2_IN_PHRASE );
			indexMapping.analyzedStringField3.accessor.write( document, TEXT_TERM_1_AND_TERM_3 );
		} );
		workPlan.add( referenceProvider( EMPTY ), document -> {
		} );
		workPlan.execute().join();

		workPlan = compatibleIndexManager.createWorkPlan();
		workPlan.add( referenceProvider( COMPATIBLE_INDEX_DOCUMENT_1 ), document -> {
			compatibleIndexMapping.analyzedStringField1.accessor.write( document, TEXT_TERM_1_AND_TERM_2 );
		} );
		workPlan.execute().join();

		workPlan = rawFieldCompatibleIndexManager.createWorkPlan();
		workPlan.add( referenceProvider( RAW_FIELD_COMPATIBLE_INDEX_DOCUMENT_1 ), document -> {
			rawFieldCompatibleIndexMapping.analyzedStringField1.accessor.write( document, TEXT_TERM_1_AND_TERM_2 );
		} );
		workPlan.execute().join();

		// Check that all documents are searchable
		StubMappingSearchTarget searchTarget = indexManager.createSearchTarget();
		SearchQuery<DocumentReference> query = searchTarget.query()
				.asReference()
				.predicate( f -> f.matchAll() )
				.build();
		assertThat( query )
				.hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_1, DOCUMENT_2, DOCUMENT_3, DOCUMENT_4, DOCUMENT_5, EMPTY );
		query = compatibleIndexManager.createSearchTarget().query()
				.asReference()
				.predicate( f -> f.matchAll() )
				.build();
		assertThat( query ).hasDocRefHitsAnyOrder( COMPATIBLE_INDEX_NAME, COMPATIBLE_INDEX_DOCUMENT_1 );
		query = rawFieldCompatibleIndexManager.createSearchTarget().query()
				.asReference()
				.predicate( f -> f.matchAll() )
				.build();
		assertThat( query ).hasDocRefHitsAnyOrder( RAW_FIELD_COMPATIBLE_INDEX_NAME, RAW_FIELD_COMPATIBLE_INDEX_DOCUMENT_1 );
	}

	private static void forEachTypeDescriptor(Consumer<FieldTypeDescriptor<?>> action) {
		FieldTypeDescriptor.getAll().stream()
				.filter( typeDescriptor -> typeDescriptor.getMatchPredicateExpectations().isPresent() )
				.forEach( action );
	}

	private static void mapByTypeFields(IndexSchemaElement parent, String prefix,
			FieldModelConsumer<Void, ByTypeFieldModel> consumer) {
		forEachTypeDescriptor( typeDescriptor -> {
			ByTypeFieldModel fieldModel = ByTypeFieldModel.mapper( typeDescriptor )
					.map( parent, prefix + typeDescriptor.getUniqueName() );
			consumer.accept( typeDescriptor, null, fieldModel );
		} );
	}

	private static class IndexMapping {
		final List<ByTypeFieldModel> unsupportedFieldModels = new ArrayList<>();

		final MainFieldModel analyzedStringField1;
		final MainFieldModel analyzedStringField2;
		final MainFieldModel analyzedStringField3;
		final MainFieldModel analyzedStringFieldWithDslConverter;

		IndexMapping(IndexSchemaElement root) {
			mapByTypeFields(
					root, "byType_",
					(typeDescriptor, ignored, model) -> {
						if ( !String.class.equals( typeDescriptor.getJavaType() ) ) {
							unsupportedFieldModels.add( model );
						}
					}
			);
			analyzedStringField1 = MainFieldModel.mapper(
					c -> c.asString().analyzer( DefaultAnalysisDefinitions.ANALYZER_STANDARD.name )
			)
					.map( root, "analyzedString1" );
			analyzedStringField2 = MainFieldModel.mapper(
					c -> c.asString().analyzer( DefaultAnalysisDefinitions.ANALYZER_STANDARD.name )
			)
					.map( root, "analyzedString2" );
			analyzedStringField3 = MainFieldModel.mapper(
					c -> c.asString().analyzer( DefaultAnalysisDefinitions.ANALYZER_STANDARD.name )
			)
					.map( root, "analyzedString3" );
			analyzedStringFieldWithDslConverter = MainFieldModel.mapper(
					c -> c.asString().analyzer( DefaultAnalysisDefinitions.ANALYZER_STANDARD.name )
							.dslConverter( ValueWrapper.toIndexFieldConverter() )
			)
					.map( root, "analyzedStringWithDslConverter" );
		}
	}

	private static class OtherIndexMapping {
		static OtherIndexMapping createCompatible(IndexSchemaElement root) {
			return new OtherIndexMapping(
					MainFieldModel.mapper(
							c -> c.asString().analyzer( DefaultAnalysisDefinitions.ANALYZER_STANDARD.name )
					)
							.map( root, "analyzedString1" )
			);
		}

		static OtherIndexMapping createRawFieldCompatible(IndexSchemaElement root) {
			return new OtherIndexMapping(
					MainFieldModel.mapper(
							c -> c.asString().analyzer( DefaultAnalysisDefinitions.ANALYZER_STANDARD.name )
									// Using a different DSL converter
									.dslConverter( ValueWrapper.toIndexFieldConverter() )
					)
							.map( root, "analyzedString1" )
			);
		}

		static OtherIndexMapping createIncompatible(IndexSchemaElement root) {
			return new OtherIndexMapping(
					MainFieldModel.mapper(
							// Using a different analyzer/normalizer
							c -> c.asString().normalizer( DefaultAnalysisDefinitions.NORMALIZER_LOWERCASE.name )
					)
							.map( root, "analyzedString1" )
			);
		}

		final MainFieldModel analyzedStringField1;

		private OtherIndexMapping(MainFieldModel analyzedStringField1) {
			this.analyzedStringField1 = analyzedStringField1;
		}
	}

	private static class MainFieldModel {
		static StandardFieldMapper<String, MainFieldModel> mapper(
				Function<IndexFieldTypeFactoryContext, StandardIndexFieldTypeContext<?, String>> configuration) {
			return StandardFieldMapper.of(
					configuration,
					(accessor, name) -> new MainFieldModel( accessor, name )
			);
		}

		final IndexFieldAccessor<String> accessor;
		final String relativeFieldName;

		private MainFieldModel(IndexFieldAccessor<String> accessor, String relativeFieldName) {
			this.accessor = accessor;
			this.relativeFieldName = relativeFieldName;
		}
	}

	private static class ByTypeFieldModel {
		static <F> StandardFieldMapper<F, ByTypeFieldModel> mapper(FieldTypeDescriptor<F> typeDescriptor) {
			return StandardFieldMapper.of(
					typeDescriptor::configure,
					(accessor, name) -> new ByTypeFieldModel( name )
			);
		}

		final String relativeFieldName;

		private ByTypeFieldModel(String relativeFieldName) {
			this.relativeFieldName = relativeFieldName;
		}
	}


}