/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.integrationtest.backend.elasticsearch.testsupport.dialect;

import java.util.Optional;

import org.hibernate.search.backend.elasticsearch.client.impl.Paths;
import org.hibernate.search.backend.elasticsearch.util.spi.URLEncodedString;

@SuppressWarnings("deprecation") // We use Paths.DOC on purpose
public class Elasticsearch6TestDialect implements ElasticsearchTestDialect {
	@Override
	public boolean isEmptyMappingPossible() {
		return true;
	}

	@Override
	public URLEncodedString getTypeKeywordForNonMappingApi() {
		return Paths.DOC;
	}

	@Override
	public Optional<URLEncodedString> getTypeNameForMappingApi() {
		return Optional.of( Paths.DOC );
	}
}