/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.tracing;

/**
 * An interface that represents a tracing span.
 * Spans are created by the Tracer.startSpan method.
 * Span must be ended by calling Tracer.endSpan which internally calls Span's endSpan.
 */
public interface Span {

    /**
     * Returns span's parent span
     */
    Span getParentSpan();

    /**
     * Returns the name of the {@link Span}
     */
    String getSpanName();

    /**
     * Returns {@link Level} of the {@link Span}
     */
    Level getLevel();

}
