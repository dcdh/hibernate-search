/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.engine.search.dsl.query;


import java.util.Optional;

import org.hibernate.search.engine.search.dsl.query.spi.AbstractDelegatingSearchQueryContext;
import org.hibernate.search.engine.search.dsl.query.spi.SearchQueryContextImplementor;
import org.hibernate.search.engine.search.query.spi.SearchQueryBuilder;

/**

 * An extension to the search query DSL, allowing to add non-standard predicates to a query.
 * <p>
 * <strong>WARNING:</strong> while this type is API, because instances should be manipulated by users,
 * all of its methods are considered SPIs and therefore should never be called or implemented directly by users.
 * In short, users are only expected to get instances of this type from an API ({@code SomeExtension.get()})
 * and pass it to another API.
 *
 * @param <T2> The type of extended search query contexts. Should generally extend
 * {@link SearchQueryResultContext}.
 * @param <T> The type of hits for the created query.
 *
 * @see SearchQueryResultContext#extension(SearchQueryContextExtension)
 * @see AbstractDelegatingSearchQueryContext
 */
public interface SearchQueryContextExtension<T2, T> {

	/**
	 * Attempt to extend a given context, returning an empty {@link Optional} in case of failure.
	 * <p>
	 * <strong>WARNING:</strong> this method is not API, see comments at the type level.
	 *
	 * @param original The original, non-extended {@link SearchQueryResultContext}.
	 * @param builder A {@link SearchQueryBuilder}.
	 * @return An optional containing the extended search query context ({@link T2}) in case
	 * of success, or an empty optional otherwise.
	 */
	Optional<T2> extendOptional(SearchQueryContextImplementor<?, T, ?, ?> original,
			SearchQueryBuilder<T, ?> builder);

}