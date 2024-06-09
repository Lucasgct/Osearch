/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.cluster.routing.remote;

import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.repositories.RepositoriesService;

import java.util.function.Supplier;

import static org.opensearch.node.remotestore.RemoteStoreNodeAttribute.isRemoteRoutingTableEnabled;

/**
 * Factory to provide impl for RemoteRoutingTableService based on settings.
 */
public class RemoteRoutingTableServiceFactory {

    /**
     * Returns {@code DefaultRemoteRoutingTableService} if the feature is enabled, otherwise {@code NoopRemoteRoutingTableService}
     * @param repositoriesService repositoriesService
     * @param settings settings
     * @param clusterSettings clusterSettings
     * @return RemoteRoutingTableService
     */
    public static RemoteRoutingTableService getService(
        Supplier<RepositoriesService> repositoriesService,
        Settings settings,
        ClusterSettings clusterSettings
    ) {
        if (isRemoteRoutingTableEnabled(settings)) {
            return new DefaultRemoteRoutingTableService(repositoriesService, settings, clusterSettings);
        }
        return new NoopRemoteRoutingTableService();
    }
}
