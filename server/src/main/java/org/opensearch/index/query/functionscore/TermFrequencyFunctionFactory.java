/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.index.query.functionscore;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.queries.function.valuesource.SumTotalTermFreqValueSource;
import org.apache.lucene.queries.function.valuesource.TFValueSource;
import org.apache.lucene.queries.function.valuesource.TermFreqValueSource;
import org.apache.lucene.queries.function.valuesource.TotalTermFreqValueSource;
import org.apache.lucene.search.IndexSearcher;
import org.opensearch.common.lucene.BytesRefs;

import java.io.IOException;
import java.util.Map;

/**
 * A factory class for creating instances of {@link TermFrequencyFunction}.
 * This class provides methods for creating different term frequency functions based on
 * the specified function name, field, and term. Each term frequency function is designed
 * to compute document scores based on specific term frequency calculations.
 *
 * @opensearch.internal
 */
public class TermFrequencyFunctionFactory {

    public static TermFrequencyFunction createFunction(
        TermFrequencyFunctionName functionName,
        Map<Object, Object> context,
        String field,
        String term,
        LeafReaderContext readerContext
    ) throws IOException {
        switch (functionName) {
            case TERM_FREQ:
                TermFreqValueSource termFreqValueSource = new TermFreqValueSource(field, term, field, BytesRefs.toBytesRef(term));
                return docId -> termFreqValueSource.getValues(null, readerContext).intVal(docId);
            case TF:
                TFValueSource tfValueSource = new TFValueSource(field, term, field, BytesRefs.toBytesRef(term));
                return docId -> tfValueSource.getValues(context, readerContext).floatVal(docId);
            case TOTAL_TERM_FREQ:
                TotalTermFreqValueSource totalTermFreqValueSource = new TotalTermFreqValueSource(
                    field,
                    term,
                    field,
                    BytesRefs.toBytesRef(term)
                );
                totalTermFreqValueSource.createWeight(context, (IndexSearcher) context.get("searcher"));
                return docId -> totalTermFreqValueSource.getValues(context, readerContext).longVal(docId);
            case SUM_TOTAL_TERM_FREQ:
                SumTotalTermFreqValueSource sumTotalTermFreqValueSource = new SumTotalTermFreqValueSource(field);
                sumTotalTermFreqValueSource.createWeight(context, (IndexSearcher) context.get("searcher"));
                return docId -> sumTotalTermFreqValueSource.getValues(context, readerContext).longVal(docId);
            default:
                throw new IllegalArgumentException("Unsupported function: " + functionName);
        }
    }

    /**
     * An enumeration representing the names of supported term frequency functions.
     */
    public enum TermFrequencyFunctionName {
        TERM_FREQ("termFreq"),
        TF("tf"),
        TOTAL_TERM_FREQ("totalTermFreq"),
        SUM_TOTAL_TERM_FREQ("sumTotalTermFreq");

        private final String termFrequencyFunctionName;

        TermFrequencyFunctionName(String termFrequencyFunctionName) {
            this.termFrequencyFunctionName = termFrequencyFunctionName;
        }

        public String getTermFrequencyFunctionName() {
            return termFrequencyFunctionName;
        }
    }
}
