/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.search.query_group;

import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Setting;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;

/**
 * Main class to declare the QueryGroup feature related settings
 */
public class QueryGroupServiceSettings {
    private static final Long DEFAULT_RUN_INTERVAL_MILLIS = 1000l;
    private static final Double DEFAULT_NODE_LEVEL_MEMORY_REJECTION_THRESHOLD = 0.8;
    private static final Double DEFAULT_NODE_LEVEL_MEMORY_CANCELLATION_THRESHOLD = 0.9;
    private static final Double DEFAULT_NODE_LEVEL_CPU_REJECTION_THRESHOLD = 0.8;
    private static final Double DEFAULT_NODE_LEVEL_CPU_CANCELLATION_THRESHOLD = 0.9;
    /**
     * default max queryGroup count on any node at any given point in time
     */
    public static final int DEFAULT_MAX_QUERY_GROUP_COUNT_VALUE = 100;

    public static final String QUERY_GROUP_COUNT_SETTING_NAME = "node.query_group.max_count";
    public static final double NODE_LEVEL_MEMORY_CANCELLATION_THRESHOLD_MAX_VALUE = 0.95;
    public static final double NODE_LEVEL_MEMORY_REJECTION_THRESHOLD_MAX_VALUE = 0.90;
    public static final double NODE_LEVEL_CPU_CANCELLATION_THRESHOLD_MAX_VALUE = 0.95;
    public static final double NODE_LEVEL_CPU_REJECTION_THRESHOLD_MAX_VALUE = 0.90;

    private TimeValue runIntervalMillis;
    private Double nodeLevelMemoryCancellationThreshold;
    private Double nodeLevelMemoryRejectionThreshold;
    private Double nodeLevelCpuCancellationThreshold;
    private Double nodeLevelCpuRejectionThreshold;
    private volatile int maxQueryGroupCount;
    /**
     *  max QueryGroup count setting
     */
    public static final Setting<Integer> MAX_QUERY_GROUP_COUNT = Setting.intSetting(
        QUERY_GROUP_COUNT_SETTING_NAME,
        DEFAULT_MAX_QUERY_GROUP_COUNT_VALUE,
        0,
        (newVal) -> {
            if (newVal > 100 || newVal < 1) throw new IllegalArgumentException(
                QUERY_GROUP_COUNT_SETTING_NAME + " should be in range [1-100]"
            );
        },
        Setting.Property.Dynamic,
        Setting.Property.NodeScope
    );
    /**
     * Setting name for default QueryGroup count
     */
    public static final String SERVICE_RUN_INTERVAL_MILLIS_SETTING_NAME = "query_group.service.run_interval_millis";
    /**
     * Setting to control the run interval of QSB service
     */
    private static final Setting<Long> QUERY_GROUP_RUN_INTERVAL_SETTING = Setting.longSetting(
        SERVICE_RUN_INTERVAL_MILLIS_SETTING_NAME,
        DEFAULT_RUN_INTERVAL_MILLIS,
        1,
        Setting.Property.Dynamic,
        Setting.Property.NodeScope
    );

    /**
     * Setting name for node level memory rejection threshold for QSB
     */
    public static final String NODE_MEMORY_REJECTION_THRESHOLD_SETTING_NAME = "query_group.node.memory_rejection_threshold";
    /**
     * Setting to control the memory rejection threshold
     */
    public static final Setting<Double> NODE_LEVEL_MEMORY_REJECTION_THRESHOLD = Setting.doubleSetting(
        NODE_MEMORY_REJECTION_THRESHOLD_SETTING_NAME,
        DEFAULT_NODE_LEVEL_MEMORY_REJECTION_THRESHOLD,
        Setting.Property.Dynamic,
        Setting.Property.NodeScope
    );
    /**
     * Setting name for node level cpu rejection threshold for QSB
     */
    public static final String NODE_CPU_REJECTION_THRESHOLD_SETTING_NAME = "query_group.node.cpu_rejection_threshold";
    /**
     * Setting to control the cpu rejection threshold
     */
    public static final Setting<Double> NODE_LEVEL_CPU_REJECTION_THRESHOLD = Setting.doubleSetting(
        NODE_CPU_REJECTION_THRESHOLD_SETTING_NAME,
        DEFAULT_NODE_LEVEL_CPU_REJECTION_THRESHOLD,
        Setting.Property.Dynamic,
        Setting.Property.NodeScope
    );
    /**
     * Setting name for node level memory cancellation threshold
     */
    public static final String NODE_MEMORY_CANCELLATION_THRESHOLD_SETTING_NAME = "query_group.node.memory_cancellation_threshold";
    /**
     * Setting name for node level memory cancellation threshold
     */
    public static final Setting<Double> NODE_LEVEL_MEMORY_CANCELLATION_THRESHOLD = Setting.doubleSetting(
        NODE_MEMORY_CANCELLATION_THRESHOLD_SETTING_NAME,
        DEFAULT_NODE_LEVEL_MEMORY_CANCELLATION_THRESHOLD,
        Setting.Property.Dynamic,
        Setting.Property.NodeScope
    );
    /**
     * Setting name for node level cpu cancellation threshold
     */
    public static final String NODE_CPU_CANCELLATION_THRESHOLD_SETTING_NAME = "query_group.node.cpu_cancellation_threshold";
    /**
     * Setting name for node level cpu cancellation threshold
     */
    public static final Setting<Double> NODE_LEVEL_CPU_CANCELLATION_THRESHOLD = Setting.doubleSetting(
        NODE_CPU_CANCELLATION_THRESHOLD_SETTING_NAME,
        DEFAULT_NODE_LEVEL_CPU_CANCELLATION_THRESHOLD,
        Setting.Property.Dynamic,
        Setting.Property.NodeScope
    );

    /**
     * QueryGroup service settings constructor
     * @param settings - QueryGroup service settings
     * @param clusterSettings - QueryGroup cluster settings
     */
    public QueryGroupServiceSettings(Settings settings, ClusterSettings clusterSettings) {
        runIntervalMillis = new TimeValue(QUERY_GROUP_RUN_INTERVAL_SETTING.get(settings));
        nodeLevelMemoryCancellationThreshold = NODE_LEVEL_MEMORY_CANCELLATION_THRESHOLD.get(settings);
        nodeLevelMemoryRejectionThreshold = NODE_LEVEL_MEMORY_REJECTION_THRESHOLD.get(settings);
        nodeLevelCpuCancellationThreshold = NODE_LEVEL_CPU_CANCELLATION_THRESHOLD.get(settings);
        nodeLevelCpuRejectionThreshold = NODE_LEVEL_CPU_REJECTION_THRESHOLD.get(settings);
        maxQueryGroupCount = MAX_QUERY_GROUP_COUNT.get(settings);

        ensureMemoryRejectionThresholdIsLessThanCancellation(nodeLevelMemoryRejectionThreshold, nodeLevelMemoryCancellationThreshold);
        ensureCpuRejectionThresholdIsLessThanCancellation(nodeLevelCpuRejectionThreshold, nodeLevelCpuCancellationThreshold);

        clusterSettings.addSettingsUpdateConsumer(MAX_QUERY_GROUP_COUNT, this::setMaxQueryGroupCount);
        clusterSettings.addSettingsUpdateConsumer(NODE_LEVEL_MEMORY_CANCELLATION_THRESHOLD, this::setNodeLevelMemoryCancellationThreshold);
        clusterSettings.addSettingsUpdateConsumer(NODE_LEVEL_MEMORY_REJECTION_THRESHOLD, this::setNodeLevelMemoryRejectionThreshold);
        clusterSettings.addSettingsUpdateConsumer(NODE_LEVEL_CPU_CANCELLATION_THRESHOLD, this::setNodeLevelCpuCancellationThreshold);
        clusterSettings.addSettingsUpdateConsumer(NODE_LEVEL_CPU_REJECTION_THRESHOLD, this::setNodeLevelCpuRejectionThreshold);
    }

    /**
     * Method to get runInterval for QSB
     * @return runInterval in milliseconds for QSB Service
     */
    public TimeValue getRunIntervalMillis() {
        return runIntervalMillis;
    }

    /**
     * Method to set the new QueryGroup count
     * @param newMaxQueryGroupCount is the new maxQueryGroupCount per node
     */
    public void setMaxQueryGroupCount(int newMaxQueryGroupCount) {
        if (newMaxQueryGroupCount < 0) {
            throw new IllegalArgumentException("node.node.query_group.max_count can't be negative");
        }
        this.maxQueryGroupCount = newMaxQueryGroupCount;
    }

    /**
     * Method to get the node level memory cancellation threshold
     * @return current node level memory cancellation threshold
     */
    public Double getNodeLevelMemoryCancellationThreshold() {
        return nodeLevelMemoryCancellationThreshold;
    }

    /**
     * Method to set the node level memory cancellation threshold
     * @param nodeLevelMemoryCancellationThreshold sets the new node level memory cancellation threshold
     * @throws IllegalArgumentException if the value is &gt; 0.95 and cancellation &lt; rejection threshold
     */
    public void setNodeLevelMemoryCancellationThreshold(Double nodeLevelMemoryCancellationThreshold) {
        if (Double.compare(nodeLevelMemoryCancellationThreshold, NODE_LEVEL_MEMORY_CANCELLATION_THRESHOLD_MAX_VALUE) > 0) {
            throw new IllegalArgumentException(
                NODE_MEMORY_CANCELLATION_THRESHOLD_SETTING_NAME + " value should not be greater than 0.95 as it pose a threat of node drop"
            );
        }

        ensureMemoryRejectionThresholdIsLessThanCancellation(nodeLevelMemoryRejectionThreshold, nodeLevelMemoryCancellationThreshold);

        this.nodeLevelMemoryCancellationThreshold = nodeLevelMemoryCancellationThreshold;
    }

    /**
     * Method to get the node level cpu cancellation threshold
     * @return current node level cpu cancellation threshold
     */
    public Double getNodeLevelCpuCancellationThreshold() {
        return nodeLevelCpuCancellationThreshold;
    }

    /**
     * Method to set the node level cpu cancellation threshold
     * @param nodeLevelCpuCancellationThreshold sets the new node level cpu cancellation threshold
     * @throws IllegalArgumentException if the value is &gt; 0.95 and cancellation &lt; rejection threshold
     */
    public void setNodeLevelCpuCancellationThreshold(Double nodeLevelCpuCancellationThreshold) {
        if (Double.compare(nodeLevelCpuCancellationThreshold, NODE_LEVEL_CPU_CANCELLATION_THRESHOLD_MAX_VALUE) > 0) {
            throw new IllegalArgumentException(
                NODE_CPU_CANCELLATION_THRESHOLD_SETTING_NAME + " value should not be greater than 0.95 as it pose a threat of node drop"
            );
        }

        ensureCpuRejectionThresholdIsLessThanCancellation(nodeLevelCpuRejectionThreshold, nodeLevelCpuCancellationThreshold);

        this.nodeLevelCpuCancellationThreshold = nodeLevelCpuCancellationThreshold;
    }

    /**
     * Method to get the memory node level rejection threshold
     * @return the current memory node level rejection threshold
     */
    public Double getNodeLevelMemoryRejectionThreshold() {
        return nodeLevelMemoryRejectionThreshold;
    }

    /**
     * Method to set the node level memory rejection threshold
     * @param nodeLevelMemoryRejectionThreshold sets the new memory rejection threshold
     * @throws IllegalArgumentException if rejection &gt; 0.90 and rejection &lt; cancellation threshold
     */
    public void setNodeLevelMemoryRejectionThreshold(Double nodeLevelMemoryRejectionThreshold) {
        if (Double.compare(nodeLevelMemoryRejectionThreshold, NODE_LEVEL_MEMORY_REJECTION_THRESHOLD_MAX_VALUE) > 0) {
            throw new IllegalArgumentException(
                NODE_MEMORY_REJECTION_THRESHOLD_SETTING_NAME + " value not be greater than 0.90 as it pose a threat of node drop"
            );
        }

        ensureMemoryRejectionThresholdIsLessThanCancellation(nodeLevelMemoryRejectionThreshold, nodeLevelMemoryCancellationThreshold);

        this.nodeLevelMemoryRejectionThreshold = nodeLevelMemoryRejectionThreshold;
    }

    /**
     * Method to get the cpu node level rejection threshold
     * @return the current cpu node level rejection threshold
     */
    public Double getNodeLevelCpuRejectionThreshold() {
        return nodeLevelCpuRejectionThreshold;
    }

    /**
     * Method to set the node level cpu rejection threshold
     * @param nodeLevelCpuRejectionThreshold sets the new cpu rejection threshold
     * @throws IllegalArgumentException if rejection &gt; 0.90 and rejection &lt; cancellation threshold
     */
    public void setNodeLevelCpuRejectionThreshold(Double nodeLevelCpuRejectionThreshold) {
        if (Double.compare(nodeLevelCpuRejectionThreshold, NODE_LEVEL_CPU_REJECTION_THRESHOLD_MAX_VALUE) > 0) {
            throw new IllegalArgumentException(
                NODE_CPU_REJECTION_THRESHOLD_SETTING_NAME + " value not be greater than 0.90 as it pose a threat of node drop"
            );
        }

        ensureCpuRejectionThresholdIsLessThanCancellation(nodeLevelCpuRejectionThreshold, nodeLevelCpuCancellationThreshold);

        this.nodeLevelCpuRejectionThreshold = nodeLevelCpuRejectionThreshold;
    }

    private void ensureMemoryRejectionThresholdIsLessThanCancellation(
        Double nodeLevelMemoryRejectionThreshold,
        Double nodeLevelMemoryCancellationThreshold
    ) {
        if (Double.compare(nodeLevelMemoryCancellationThreshold, nodeLevelMemoryRejectionThreshold) < 0) {
            throw new IllegalArgumentException(
                NODE_MEMORY_CANCELLATION_THRESHOLD_SETTING_NAME
                    + " value should not be less than "
                    + NODE_MEMORY_REJECTION_THRESHOLD_SETTING_NAME
            );
        }
    }

    private void ensureCpuRejectionThresholdIsLessThanCancellation(
        Double nodeLevelCpuRejectionThreshold,
        Double nodeLevelCpuCancellationThreshold
    ) {
        if (Double.compare(nodeLevelCpuCancellationThreshold, nodeLevelCpuRejectionThreshold) < 0) {
            throw new IllegalArgumentException(
                NODE_CPU_CANCELLATION_THRESHOLD_SETTING_NAME + " value should not be less than " + NODE_CPU_REJECTION_THRESHOLD_SETTING_NAME
            );
        }
    }

    /**
     * Method to get the current QueryGroup count
     * @return the current max QueryGroup count
     */
    public int getMaxQueryGroupCount() {
        return maxQueryGroupCount;
    }
}
