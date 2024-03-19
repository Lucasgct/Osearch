/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.remotemigration;

import org.opensearch.action.admin.cluster.settings.ClusterUpdateSettingsRequest;
import org.opensearch.action.admin.indices.shrink.ResizeType;
import org.opensearch.action.support.ActiveShardCount;
import org.opensearch.client.Client;
import org.opensearch.common.settings.Settings;
import org.opensearch.indices.replication.common.ReplicationType;
import org.opensearch.test.OpenSearchIntegTestCase;

import java.util.List;

import static org.opensearch.cluster.metadata.IndexMetadata.SETTING_REPLICATION_TYPE;
import static org.opensearch.node.remotestore.RemoteStoreNodeService.MIGRATION_DIRECTION_SETTING;
import static org.opensearch.node.remotestore.RemoteStoreNodeService.REMOTE_STORE_COMPATIBILITY_MODE_SETTING;
import static org.opensearch.test.hamcrest.OpenSearchAssertions.assertAcked;

@OpenSearchIntegTestCase.ClusterScope(scope = OpenSearchIntegTestCase.Scope.TEST, numDataNodes = 0, autoManageMasterNodes = false)
public class ResizeIndexMigrationTestCase extends MigrationBaseTestCase {
    private static final String TEST_INDEX = "test_index";
    private final static String REMOTE_STORE_DIRECTION = "remote_store";
    private final static String NONE_DIRECTION = "none";
    private final static String STRICT_MODE = "strict";
    private final static String MIXED_MODE = "mixed";

    public void testFailResizeIndexWhileMigration() throws Exception {

        internalCluster().setBootstrapClusterManagerNodeIndex(0);
        List<String> cmNodes = internalCluster().startNodes(1);
        Client client = internalCluster().client(cmNodes.get(0));
        ClusterUpdateSettingsRequest updateSettingsRequest = new ClusterUpdateSettingsRequest();
        updateSettingsRequest.persistentSettings(Settings.builder().put(REMOTE_STORE_COMPATIBILITY_MODE_SETTING.getKey(), MIXED_MODE));
        assertAcked(client().admin().cluster().updateSettings(updateSettingsRequest).actionGet());

        // Adding a non remote and a remote node
        addRemote = false;
        String nonRemoteNodeName = internalCluster().startNode();

        addRemote = true;
        String remoteNodeName = internalCluster().startNode();

        logger.info("-->Create index on non-remote node and SETTING_REMOTE_STORE_ENABLED is false. Resize should not happen");
        Settings.Builder builder = Settings.builder().put(SETTING_REPLICATION_TYPE, ReplicationType.SEGMENT);
        client.admin()
            .indices()
            .prepareCreate(TEST_INDEX)
            .setSettings(
                builder.put("index.number_of_shards", 10)
                    .put("index.number_of_replicas", 0)
                    .put("index.routing.allocation.include._name", nonRemoteNodeName)
                    .put("index.routing.allocation.exclude._name", remoteNodeName)
            )
            .setWaitForActiveShards(ActiveShardCount.ALL)
            .execute()
            .actionGet();

        updateSettingsRequest.persistentSettings(Settings.builder().put(MIGRATION_DIRECTION_SETTING.getKey(), REMOTE_STORE_DIRECTION));
        assertAcked(client.admin().cluster().updateSettings(updateSettingsRequest).actionGet());

        ResizeType resizeType;
        int resizeShardsNum;
        String cause;
        switch (randomIntBetween(0, 2)) {
            case 0:
                resizeType = ResizeType.SHRINK;
                resizeShardsNum = 5;
                cause = "shrink_index";
                break;
            case 1:
                resizeType = ResizeType.SPLIT;
                resizeShardsNum = 20;
                cause = "split_index";
                break;
            default:
                resizeType = ResizeType.CLONE;
                resizeShardsNum = 10;
                cause = "clone_index";
        }

        client.admin()
            .indices()
            .prepareUpdateSettings(TEST_INDEX)
            .setSettings(Settings.builder().put("index.blocks.write", true))
            .execute()
            .actionGet();

        ensureGreen(TEST_INDEX);

        Settings.Builder resizeSettingsBuilder = Settings.builder()
            .put("index.number_of_replicas", 0)
            .put("index.number_of_shards", resizeShardsNum)
            .putNull("index.blocks.write");

        IllegalStateException ex = expectThrows(
            IllegalStateException.class,
            () -> client().admin()
                .indices()
                .prepareResizeIndex(TEST_INDEX, "first_split")
                .setResizeType(resizeType)
                .setSettings(resizeSettingsBuilder.build())
                .get()
        );
        assertEquals(
            ex.getMessage(),
            "index Resizing for type ["
                + resizeType
                + "] is not allowed as Cluster mode is [Mixed]"
                + " and migration direction is [Remote Store]"
        );
    }
}
