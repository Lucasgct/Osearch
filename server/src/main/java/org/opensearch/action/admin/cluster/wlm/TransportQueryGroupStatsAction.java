/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.action.admin.cluster.wlm;

import org.opensearch.action.FailedNodeException;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.nodes.TransportNodesAction;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.common.io.stream.StreamInput;
<<<<<<< HEAD
import org.opensearch.threadpool.ThreadPool;
=======
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportRequest;
>>>>>>> b5cbfa4de9e (changelog)
import org.opensearch.transport.TransportService;
import org.opensearch.wlm.QueryGroupService;
import org.opensearch.wlm.stats.QueryGroupStats;

import java.io.IOException;
import java.util.List;

/**
 * Transport action for obtaining QueryGroupStats
 *
 * @opensearch.experimental
 */
public class TransportQueryGroupStatsAction extends TransportNodesAction<
    QueryGroupStatsRequest,
    QueryGroupStatsResponse,
<<<<<<< HEAD
    QueryGroupStatsRequest,
    QueryGroupStats> {

    final QueryGroupService queryGroupService;
=======
    TransportQueryGroupStatsAction.NodeQueryGroupStatsRequest,
    QueryGroupStats> {

    QueryGroupService queryGroupService;
>>>>>>> b5cbfa4de9e (changelog)

    @Inject
    public TransportQueryGroupStatsAction(
        ThreadPool threadPool,
        ClusterService clusterService,
        TransportService transportService,
        QueryGroupService queryGroupService,
        ActionFilters actionFilters
    ) {
        super(
            QueryGroupStatsAction.NAME,
            threadPool,
            clusterService,
            transportService,
            actionFilters,
            QueryGroupStatsRequest::new,
<<<<<<< HEAD
            QueryGroupStatsRequest::new,
=======
            NodeQueryGroupStatsRequest::new,
>>>>>>> b5cbfa4de9e (changelog)
            ThreadPool.Names.MANAGEMENT,
            QueryGroupStats.class
        );
        this.queryGroupService = queryGroupService;
    }

    @Override
    protected QueryGroupStatsResponse newResponse(
        QueryGroupStatsRequest request,
        List<QueryGroupStats> queryGroupStats,
        List<FailedNodeException> failures
    ) {
        return new QueryGroupStatsResponse(clusterService.getClusterName(), queryGroupStats, failures);
    }

    @Override
<<<<<<< HEAD
    protected QueryGroupStatsRequest newNodeRequest(QueryGroupStatsRequest request) {
        return request;
=======
    protected NodeQueryGroupStatsRequest newNodeRequest(QueryGroupStatsRequest request) {
        return new NodeQueryGroupStatsRequest(request);
>>>>>>> b5cbfa4de9e (changelog)
    }

    @Override
    protected QueryGroupStats newNodeResponse(StreamInput in) throws IOException {
        return new QueryGroupStats(in);
    }

    @Override
<<<<<<< HEAD
    protected QueryGroupStats nodeOperation(QueryGroupStatsRequest queryGroupStatsRequest) {
        return queryGroupService.nodeStats(queryGroupStatsRequest.getQueryGroupIds(), queryGroupStatsRequest.isBreach());
=======
    protected QueryGroupStats nodeOperation(NodeQueryGroupStatsRequest nodeQueryGroupStatsRequest) {
        QueryGroupStatsRequest request = nodeQueryGroupStatsRequest.request;
        return queryGroupService.nodeStats(request.getQueryGroupIds(), request.isBreach());
    }

    /**
     * Inner QueryGroupStatsRequest
     *
     * @opensearch.experimental
     */
    public static class NodeQueryGroupStatsRequest extends TransportRequest {

        protected QueryGroupStatsRequest request;

        public NodeQueryGroupStatsRequest(StreamInput in) throws IOException {
            super(in);
            request = new QueryGroupStatsRequest(in);
        }

        NodeQueryGroupStatsRequest(QueryGroupStatsRequest request) {
            this.request = request;
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            super.writeTo(out);
            request.writeTo(out);
        }
<<<<<<< HEAD
>>>>>>> b5cbfa4de9e (changelog)
=======

        public DiscoveryNode[] getDiscoveryNodes() {
            return this.request.concreteNodes();
        }
>>>>>>> ffe0d7fa2cd (address comments)
    }
}
