/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.search.approximate;

import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;
import org.opensearch.index.mapper.MappedFieldType;
import org.opensearch.search.internal.SearchContext;
import org.opensearch.search.sort.FieldSortBuilder;

import java.io.IOException;

public class ApproximateMatchAllQuery extends ApproximateQuery {
    private ApproximateQuery approximation = null;

    @Override
    protected boolean canApproximate(SearchContext context) {
        if (context == null) {
            return false;
        }
        if (context.aggregations() != null) {
            return false;
        }

        if (context.request() != null && context.request().source() != null) {
            FieldSortBuilder primarySortField = FieldSortBuilder.getPrimaryFieldSortOrNull(context.request().source());
            if (primarySortField != null && primarySortField.missing() == null) {
                MappedFieldType mappedFieldType = context.getQueryShardContext().fieldMapper(primarySortField.fieldName());
                Query rangeQuery = mappedFieldType.rangeQuery(null, null, false, false, null, null, null, context.getQueryShardContext());
                if (rangeQuery instanceof ApproximateScoreQuery) {
                    ApproximateScoreQuery approximateScoreQuery = (ApproximateScoreQuery) rangeQuery;
                    approximateScoreQuery.setContext(context);
                    if (approximateScoreQuery.resolvedQuery instanceof ApproximateQuery) {
                        approximation = (ApproximateQuery) approximateScoreQuery.resolvedQuery;
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public String toString(String field) {
        return "Approximate(*:*)";
    }

    @Override
    public void visit(QueryVisitor visitor) {
        visitor.visitLeaf(this);

    }

    @Override
    public boolean equals(Object o) {
        return sameClassAs(o);
    }

    @Override
    public int hashCode() {
        return classHash();
    }

    @Override
    public Query rewrite(IndexSearcher indexSearcher) throws IOException {
        if (approximation == null) {
            throw new IllegalStateException("rewrite called without setting context or query could not be approximated");
        }
        return approximation;
    }
}
