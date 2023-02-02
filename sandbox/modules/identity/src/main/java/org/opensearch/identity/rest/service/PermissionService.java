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
import org.opensearch.identity.ConfigConstants;
import org.opensearch.identity.authz.OpenSearchPermission;
import org.opensearch.identity.authz.PermissionFactory;
import org.opensearch.identity.authz.PermissionStorage;
import org.opensearch.identity.rest.action.permission.put.PutPermissionResponse;
import org.opensearch.identity.rest.action.permission.put.PutPermissionResponseInfo;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.transport.TransportService;
import org.opensearch.identity.utils.ErrorType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

/**
 * Service class for Permission related functions
 */
@SuppressWarnings("unchecked")
public class PermissionService {

    private static final Logger logger = LogManager.getLogger(PermissionService.class);

    private final ClusterService clusterService;
    private final TransportService transportService;
    private final NodeClient nodeClient;

    private final String identityIndex = ConfigConstants.IDENTITY_CONFIG_INDEX_NAME;

    @Inject
    public PermissionService(ClusterService clusterService, TransportService transportService, NodeClient nodeClient) {
        this.clusterService = clusterService;
        this.transportService = transportService;
        this.nodeClient = nodeClient;
    }

    protected boolean ensureIndexExists() {
        if (!this.clusterService.state().metadata().hasConcreteIndex(this.identityIndex)) {
            return false;
        }
        return true;
    }

    public void putPermission(String principal, String permissionString, ActionListener<PutPermissionResponse> listener) {

        if (!ensureIndexExists()) {
            listener.onFailure(new IndexNotFoundException(ErrorType.IDENTITY_NOT_INITIALIZED.getMessage()));
            return;
        }

        OpenSearchPermission newPermission = PermissionFactory.createPermission(permissionString);
        List<OpenSearchPermission> permissionList = new ArrayList<OpenSearchPermission>(
            (Collection<? extends OpenSearchPermission>) newPermission
        );
        PermissionStorage.put(principal, permissionList);
        PutPermissionResponseInfo responseInfo = new PutPermissionResponseInfo(true, permissionString, principal);
        PutPermissionResponse response = new PutPermissionResponse(unmodifiableList(asList(responseInfo)));
        listener.onResponse(response);
    }
}
