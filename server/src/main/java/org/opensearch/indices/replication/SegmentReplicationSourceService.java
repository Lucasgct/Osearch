/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.indices.replication;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.opensearch.action.support.ChannelActionListener;
import org.opensearch.cluster.ClusterChangedEvent;
import org.opensearch.cluster.ClusterStateListener;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.routing.ShardRouting;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.Nullable;
import org.opensearch.common.component.AbstractLifecycleComponent;
import org.opensearch.common.concurrent.GatedCloseable;
import org.opensearch.common.settings.Settings;
import org.opensearch.index.shard.IndexEventListener;
import org.opensearch.index.shard.IndexShard;
import org.opensearch.index.shard.ShardId;
import org.opensearch.index.shard.ShardNotInPrimaryModeException;
import org.opensearch.indices.IndicesService;
import org.opensearch.indices.recovery.RecoverySettings;
import org.opensearch.indices.recovery.RetryableTransportClient;
import org.opensearch.indices.replication.common.CopyState;
import org.opensearch.indices.replication.common.ReplicationTimer;
import org.opensearch.tasks.Task;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportChannel;
import org.opensearch.transport.TransportRequestHandler;
import org.opensearch.transport.TransportService;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service class that handles segment replication requests from replica shards.
 * Typically, the "source" is a primary shard. This code executes on the source node.
 *
 * @opensearch.internal
 */
public class SegmentReplicationSourceService extends AbstractLifecycleComponent implements ClusterStateListener, IndexEventListener {

    // Empty Implementation, only required while Segment Replication is under feature flag.
    public static final SegmentReplicationSourceService NO_OP = new SegmentReplicationSourceService() {
        @Override
        public void clusterChanged(ClusterChangedEvent event) {
            // NoOp;
        }

        @Override
        public void beforeIndexShardClosed(ShardId shardId, IndexShard indexShard, Settings indexSettings) {
            // NoOp;
        }

        @Override
        public void shardRoutingChanged(IndexShard indexShard, @Nullable ShardRouting oldRouting, ShardRouting newRouting) {
            // NoOp;
        }
    };

    private static final Logger logger = LogManager.getLogger(SegmentReplicationSourceService.class);
    private final RecoverySettings recoverySettings;
    private final TransportService transportService;
    private final IndicesService indicesService;

    /**
     * Internal actions used by the segment replication source service on the primary shard
     *
     * @opensearch.internal
     */
    public static class Actions {

        public static final String GET_CHECKPOINT_INFO = "internal:index/shard/replication/get_checkpoint_info";
        public static final String GET_SEGMENT_FILES = "internal:index/shard/replication/get_segment_files";
    }

    private final OngoingSegmentReplications ongoingSegmentReplications;

    // Used only for empty implementation.
    private SegmentReplicationSourceService() {
        recoverySettings = null;
        ongoingSegmentReplications = null;
        transportService = null;
        indicesService = null;
    }

    public SegmentReplicationSourceService(
        IndicesService indicesService,
        TransportService transportService,
        RecoverySettings recoverySettings
    ) {
        this.transportService = transportService;
        this.indicesService = indicesService;
        this.recoverySettings = recoverySettings;
        transportService.registerRequestHandler(
            Actions.GET_CHECKPOINT_INFO,
            ThreadPool.Names.GENERIC,
            CheckpointInfoRequest::new,
            new CheckpointInfoRequestHandler()
        );
        transportService.registerRequestHandler(
            Actions.GET_SEGMENT_FILES,
            ThreadPool.Names.GENERIC,
            GetSegmentFilesRequest::new,
            new GetSegmentFilesRequestHandler()
        );
        this.ongoingSegmentReplications = new OngoingSegmentReplications(indicesService, recoverySettings);
    }

    private class CheckpointInfoRequestHandler implements TransportRequestHandler<CheckpointInfoRequest> {
        @Override
        public void messageReceived(CheckpointInfoRequest request, TransportChannel channel, Task task) throws Exception {
            final ReplicationTimer timer = new ReplicationTimer();
            assert ongoingSegmentReplications != null;
            timer.start();
            final GatedCloseable<CopyState> copyStateGatedCloseable = ongoingSegmentReplications.getLatestCopyState(
                request.getCheckpoint().getShardId()
            );
            final CopyState copyState = copyStateGatedCloseable.get();
            final IndexShard primaryShard = copyState.getShard();
            if (primaryShard.verifyPrimaryMode() == false) {
                throw new ShardNotInPrimaryModeException(primaryShard.shardId(), primaryShard.state());
            } else {
                if (request.getCheckpoint().isAheadOf(copyState.getCheckpoint()) || copyState.getMetadataMap().isEmpty()) {
                    // if there are no files to send, or the replica is already at this checkpoint, send the infos but do not hold snapshotted
                    // infos.
                    // During recovery of an empty cluster it is possible we have no files to send but the primary has flushed to set userData,
                    // in this case we still want to send over infos.
                    channel.sendResponse(
                        new CheckpointInfoResponse(copyState.getCheckpoint(), Collections.emptyMap(), copyState.getInfosBytes())
                    );
                    copyState.decRef();
                } else {
                    final RemoteSegmentFileChunkWriter segmentSegmentFileChunkWriter = buildFileChunkWriter(request);
                    ongoingSegmentReplications.prepareForReplication(request, segmentSegmentFileChunkWriter, copyStateGatedCloseable);
                    channel.sendResponse(
                        new CheckpointInfoResponse(copyState.getCheckpoint(), copyState.getMetadataMap(), copyState.getInfosBytes())
                    );
                    timer.stop();
                    logger.info(
                        new ParameterizedMessage(
                            "[replication id {}] Source node sent checkpoint info [{}] to target node [{}], timing: {}",
                            request.getReplicationId(),
                            copyState.getCheckpoint(),
                            request.getTargetNode().getName(),
                            timer.time()
                        )
                    );
                }
            }
        }

        private RemoteSegmentFileChunkWriter buildFileChunkWriter(CheckpointInfoRequest request) {
            return new RemoteSegmentFileChunkWriter(
                request.getReplicationId(),
                recoverySettings,
                new RetryableTransportClient(
                    transportService,
                    request.getTargetNode(),
                    recoverySettings.internalActionRetryTimeout(),
                    logger
                ),
                request.getCheckpoint().getShardId(),
                SegmentReplicationTargetService.Actions.FILE_CHUNK,
                new AtomicLong(0),
                (throttleTime) -> {}
            );
        }
    }

    private class GetSegmentFilesRequestHandler implements TransportRequestHandler<GetSegmentFilesRequest> {
        @Override
        public void messageReceived(GetSegmentFilesRequest request, TransportChannel channel, Task task) throws Exception {
            ongoingSegmentReplications.startSegmentCopy(request, new ChannelActionListener<>(channel, Actions.GET_SEGMENT_FILES, request));
        }
    }

    @Override
    public void clusterChanged(ClusterChangedEvent event) {
        if (event.nodesRemoved()) {
            for (DiscoveryNode removedNode : event.nodesDelta().removedNodes()) {
                ongoingSegmentReplications.cancelReplication(removedNode);
            }
        }
    }

    @Override
    protected void doStart() {
        final ClusterService clusterService = indicesService.clusterService();
        if (DiscoveryNode.isDataNode(clusterService.getSettings())) {
            clusterService.addListener(this);
        }
    }

    @Override
    protected void doStop() {
        final ClusterService clusterService = indicesService.clusterService();
        if (DiscoveryNode.isDataNode(clusterService.getSettings())) {
            indicesService.clusterService().removeListener(this);
        }
    }

    @Override
    protected void doClose() throws IOException {
        ongoingSegmentReplications.close();
    }

    /**
     * Cancels any replications on this node for a primary shard that is about to be closed.
     */
    @Override
    public void beforeIndexShardClosed(ShardId shardId, @Nullable IndexShard indexShard, Settings indexSettings) {
        if (indexShard != null) {
            ongoingSegmentReplications.cancel(indexShard, "shard is closed");
        }
    }

    /**
     * Cancels any replications on this node to a replica that has been promoted as primary.
     */
    @Override
    public void shardRoutingChanged(IndexShard indexShard, @Nullable ShardRouting oldRouting, ShardRouting newRouting) {
        if (indexShard != null && oldRouting.primary() == false && newRouting.primary()) {
            ongoingSegmentReplications.cancel(indexShard.routingEntry().allocationId().getId(), "Relocating primary shard.");
        }
        if (indexShard != null && oldRouting.primary() && newRouting.primary() == false) {
            ongoingSegmentReplications.clearCopyStateForShard(indexShard.shardId());
        }
    }

    public void onPrimaryRefresh(IndexShard indexShard) {
        ongoingSegmentReplications.setCopyState(indexShard);
    }

}
