/*
* SPDX-License-Identifier: Apache-2.0
*
* The OpenSearch Contributors require contributions made to
* this file be licensed under the Apache-2.0 license or a
* compatible open source license.
*/

package org.opensearch.identity.rest.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.ActionListener;
import org.opensearch.client.node.NodeClient;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.identity.rest.action.permission.add.AddPermissionResponse;
import org.opensearch.identity.rest.action.permission.check.CheckPermissionResponse;
import org.opensearch.identity.rest.action.permission.delete.DeletePermissionResponse;
import org.opensearch.transport.TransportService;

/**
 * Service class for Permission related functions
 */
public class PermissionService {

    private static final Logger logger = LogManager.getLogger(PermissionService.class);

    private final ClusterService clusterService;
    private final TransportService transportService;
    private final NodeClient nodeClient;

    @Inject
    public PermissionService(ClusterService clusterService, TransportService transportService, NodeClient nodeClient) {
        this.clusterService = clusterService;
        this.transportService = transportService;
        this.nodeClient = nodeClient;
    }

    public void addPermission(String username, String password, ActionListener<AddPermissionResponse> listener) {
        // TODO: Implement this
    }

    public void deletePermission(String username, String password, ActionListener<DeletePermissionResponse> listener) {
        // TODO: Implement this
    }

    public void checkPermission(String username, String password, ActionListener<CheckPermissionResponse> listener) {
        // TODO: Implement this
    }
}
