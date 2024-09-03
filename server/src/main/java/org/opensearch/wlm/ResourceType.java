/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.wlm;

import org.opensearch.common.annotation.PublicApi;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.tasks.resourcetracker.ResourceStats;
import org.opensearch.tasks.Task;

import java.io.IOException;
import java.util.function.Function;

/**
 * Enum to hold the resource type
 *
 * @opensearch.api
 */
@PublicApi(since = "2.17.0")
public enum ResourceType {
    CPU("cpu", true),
    MEMORY("memory", true);

    private final String name;
    private final boolean statsEnabled;

    ResourceType(String name, boolean statsEnabled) {
        this.name = name;
        this.statsEnabled = statsEnabled;
    }

    /**
     * The string match here is case-sensitive
     * @param s name matching the resource type name
     * @return a {@link ResourceType}
     */
    public static ResourceType fromName(String s) {
        for (ResourceType resourceType : values()) {
            if (resourceType.getName().equals(s)) {
                return resourceType;
            }
        }
        throw new IllegalArgumentException("Unknown resource type: [" + s + "]");
    }

    public static void writeTo(StreamOutput out, ResourceType resourceType) throws IOException {
        out.writeString(resourceType.getName());
    }

    public String getName() {
        return name;
    }

    public boolean hasStatsEnabled() {
        return statsEnabled;
    }
}
