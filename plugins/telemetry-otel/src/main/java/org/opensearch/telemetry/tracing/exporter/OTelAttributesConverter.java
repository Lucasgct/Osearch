/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.telemetry.tracing.exporter;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import java.util.Locale;

/**
 * Converts {@link org.opensearch.telemetry.tracing.attributes.Attributes} to OTel {@link Attributes}
 */
public class OTelAttributesConverter {

    /**
     * Constructor.
     */
    public OTelAttributesConverter() {}

    /**
     * Attribute converter.
     * @param attributes attributes
     * @return otel attributes.
     */
    public Attributes convert(org.opensearch.telemetry.tracing.attributes.Attributes attributes) {
        AttributesBuilder attributesBuilder = Attributes.builder();
        if (attributes != null) {
            attributes.getAttributesMap().forEach((x, y) -> addSpanAttribute(x, y, attributesBuilder));
        }
        return attributesBuilder.build();
    }

    private void addSpanAttribute(String key, Object value, AttributesBuilder attributesBuilder) {
        if (value instanceof Boolean) {
            attributesBuilder.put(key, (Boolean) value);
        } else if (value instanceof Long) {
            attributesBuilder.put(key, (Long) value);
        } else if (value instanceof Double) {
            attributesBuilder.put(key, (Double) value);
        } else if (value instanceof String) {
            attributesBuilder.put(key, (String) value);
        } else {
            throw new IllegalArgumentException(String.format(Locale.ROOT, "Span attribute value %s type not supported", value));
        }
    }
}
