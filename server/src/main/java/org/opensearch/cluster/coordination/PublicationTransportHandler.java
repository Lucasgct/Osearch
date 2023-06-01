/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
/*
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

package org.opensearch.cluster.coordination;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.opensearch.OpenSearchException;
import org.opensearch.Version;
import org.opensearch.action.ActionListener;
import org.opensearch.cluster.ClusterChangedEvent;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.Diff;
import org.opensearch.cluster.IncompatibleClusterStateVersionException;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.node.DiscoveryNodes;
import org.opensearch.common.bytes.BytesReference;
import org.opensearch.common.io.stream.NamedWriteableRegistry;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.util.io.IOUtils;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.BytesTransportRequest;
import org.opensearch.transport.TransportChannel;
import org.opensearch.transport.TransportException;
import org.opensearch.transport.TransportRequestOptions;
import org.opensearch.transport.TransportResponse;
import org.opensearch.transport.TransportResponseHandler;
import org.opensearch.transport.TransportService;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Transport handler for publication
 *
 * @opensearch.internal
 */
public class PublicationTransportHandler {

    private static final Logger logger = LogManager.getLogger(PublicationTransportHandler.class);

    public static final String PUBLISH_STATE_ACTION_NAME = "internal:cluster/coordination/publish_state";
    public static final String COMMIT_STATE_ACTION_NAME = "internal:cluster/coordination/commit_state";

    private final TransportService transportService;
    private final NamedWriteableRegistry namedWriteableRegistry;
    private final Function<PublishRequest, PublishWithJoinResponse> handlePublishRequest;

    private final AtomicReference<ClusterState> lastSeenClusterState = new AtomicReference<>();

    // the cluster-manager needs the original non-serialized state as the cluster state contains some volatile information that we
    // don't want to be replicated because it's not usable on another node (e.g. UnassignedInfo.unassignedTimeNanos) or
    // because it's mostly just debugging info that would unnecessarily blow up CS updates (I think there was one in
    // snapshot code).
    // TODO: look into these and check how to get rid of them
    private final AtomicReference<PublishRequest> currentPublishRequestToSelf = new AtomicReference<>();

    private final AtomicLong fullClusterStateReceivedCount = new AtomicLong();
    private final AtomicLong incompatibleClusterStateDiffReceivedCount = new AtomicLong();
    private final AtomicLong compatibleClusterStateDiffReceivedCount = new AtomicLong();
    // -> no need to put a timeout on the options here, because we want the response to eventually be received
    // and not log an error if it arrives after the timeout
    private final TransportRequestOptions stateRequestOptions = TransportRequestOptions.builder()
        .withType(TransportRequestOptions.Type.STATE)
        .build();

    public PublicationTransportHandler(
        TransportService transportService,
        NamedWriteableRegistry namedWriteableRegistry,
        Function<PublishRequest, PublishWithJoinResponse> handlePublishRequest,
        BiConsumer<ApplyCommitRequest, ActionListener<Void>> handleApplyCommit
    ) {
        this.transportService = transportService;
        this.namedWriteableRegistry = namedWriteableRegistry;
        this.handlePublishRequest = handlePublishRequest;

        transportService.registerRequestHandler(
            PUBLISH_STATE_ACTION_NAME,
            ThreadPool.Names.GENERIC,
            false,
            false,
            BytesTransportRequest::new,
            (request, channel, task) -> channel.sendResponse(handleIncomingPublishRequest(request))
        );

        transportService.registerRequestHandler(
            COMMIT_STATE_ACTION_NAME,
            ThreadPool.Names.GENERIC,
            false,
            false,
            ApplyCommitRequest::new,
            (request, channel, task) -> handleApplyCommit.accept(request, transportCommitCallback(channel))
        );
    }

    private ActionListener<Void> transportCommitCallback(TransportChannel channel) {
        return new ActionListener<Void>() {

            @Override
            public void onResponse(Void aVoid) {
                try {
                    channel.sendResponse(TransportResponse.Empty.INSTANCE);
                } catch (IOException e) {
                    logger.debug("failed to send response on commit", e);
                }
            }

            @Override
            public void onFailure(Exception e) {
                try {
                    channel.sendResponse(e);
                } catch (IOException ie) {
                    e.addSuppressed(ie);
                    logger.debug("failed to send response on commit", e);
                }
            }
        };
    }

    public PublishClusterStateStats stats() {
        return new PublishClusterStateStats(
            fullClusterStateReceivedCount.get(),
            incompatibleClusterStateDiffReceivedCount.get(),
            compatibleClusterStateDiffReceivedCount.get()
        );
    }

    private PublishWithJoinResponse handleIncomingPublishRequest(BytesTransportRequest request) throws IOException {
        StreamInput in = null;
        try {
            in = ClusterStateUtils.decompressClusterState(request, namedWriteableRegistry);
            ClusterState incomingState;
            if (in.readBoolean()) {
                incomingState = ClusterStateUtils.deserializeFullClusterState(in, transportService.getLocalNode());
                fullClusterStateReceivedCount.incrementAndGet();
                logger.debug("received full cluster state version [{}] with size [{}]", incomingState.version(), request.bytes().length());
                final PublishWithJoinResponse response = acceptState(incomingState);
                lastSeenClusterState.set(incomingState);
                return response;
            } else {
                final ClusterState lastSeen = lastSeenClusterState.get();
                if (lastSeen == null) {
                    logger.debug("received diff for but don't have any local cluster state - requesting full state");
                    incompatibleClusterStateDiffReceivedCount.incrementAndGet();
                    throw new IncompatibleClusterStateVersionException("have no local cluster state");
                } else {
                    try {
                        Diff<ClusterState> diff = ClusterStateUtils.deserializeClusterStateDiff(in, lastSeen.getNodes().getLocalNode());
                        incomingState = diff.apply(lastSeen); // might throw IncompatibleClusterStateVersionException
                    } catch (IncompatibleClusterStateVersionException e) {
                        incompatibleClusterStateDiffReceivedCount.incrementAndGet();
                        throw e;
                    }
                    compatibleClusterStateDiffReceivedCount.incrementAndGet();
                    logger.debug(
                        "received diff cluster state version [{}] with uuid [{}], diff size [{}]",
                        incomingState.version(),
                        incomingState.stateUUID(),
                        request.bytes().length()
                    );
                    final PublishWithJoinResponse response = acceptState(incomingState);
                    lastSeenClusterState.compareAndSet(lastSeen, incomingState);
                    return response;
                }
            }
        } finally {
            IOUtils.close(in);
        }
    }

    private PublishWithJoinResponse acceptState(ClusterState incomingState) {
        // if the state is coming from the current node, use original request instead (see currentPublishRequestToSelf for explanation)
        if (transportService.getLocalNode().equals(incomingState.nodes().getClusterManagerNode())) {
            final PublishRequest publishRequest = currentPublishRequestToSelf.get();
            if (publishRequest == null || publishRequest.getAcceptedState().stateUUID().equals(incomingState.stateUUID()) == false) {
                throw new IllegalStateException("publication to self failed for " + publishRequest);
            } else {
                return handlePublishRequest.apply(publishRequest);
            }
        }
        return handlePublishRequest.apply(new PublishRequest(incomingState));
    }

    public PublicationContext newPublicationContext(ClusterChangedEvent clusterChangedEvent) {
        final PublicationContext publicationContext = new PublicationContext(clusterChangedEvent);

        // Build the serializations we expect to need now, early in the process, so that an error during serialization fails the publication
        // straight away. This isn't watertight since we send diffs on a best-effort basis and may fall back to sending a full state (and
        // therefore serializing it) if the diff-based publication fails.
        publicationContext.buildDiffAndSerializeStates();
        return publicationContext;
    }

    /**
     * Publishing a cluster state typically involves sending the same cluster state (or diff) to every node, so the work of diffing,
     * serializing, and compressing the state can be done once and the results shared across publish requests. The
     * {@code PublicationContext} implements this sharing.
     *
     * @opensearch.internal
     */
    public class PublicationContext {

        private final DiscoveryNodes discoveryNodes;
        private final ClusterState newState;
        private final ClusterState previousState;
        private final boolean sendFullVersion;
        private final Map<Version, BytesReference> serializedStates = new HashMap<>();
        private final Map<Version, BytesReference> serializedDiffs = new HashMap<>();

        PublicationContext(ClusterChangedEvent clusterChangedEvent) {
            discoveryNodes = clusterChangedEvent.state().nodes();
            newState = clusterChangedEvent.state();
            previousState = clusterChangedEvent.previousState();
            sendFullVersion = previousState.getBlocks().disableStatePersistence();
        }

        void buildDiffAndSerializeStates() {
            Diff<ClusterState> diff = null;
            for (DiscoveryNode node : discoveryNodes) {
                try {
                    if (sendFullVersion || previousState.nodes().nodeExists(node) == false) {
                        if (serializedStates.containsKey(node.getVersion()) == false) {
                            serializedStates.put(node.getVersion(), ClusterStateUtils.serializeClusterState(newState, node));
                        }
                    } else {
                        // will send a diff
                        if (diff == null) {
                            diff = newState.diff(previousState);
                        }
                        if (serializedDiffs.containsKey(node.getVersion()) == false) {
                            final BytesReference serializedDiff = ClusterStateUtils.serializeClusterState(diff, node);
                            serializedDiffs.put(node.getVersion(), serializedDiff);
                            logger.trace(
                                "serialized cluster state diff for version [{}] in for node version [{}] with size [{}]",
                                newState.version(),
                                node.getVersion(),
                                serializedDiff.length()
                            );
                        }
                    }
                } catch (IOException e) {
                    throw new OpenSearchException("failed to serialize cluster state for publishing to node {}", e, node);
                }
            }
        }

        public void sendPublishRequest(
            DiscoveryNode destination,
            PublishRequest publishRequest,
            ActionListener<PublishWithJoinResponse> listener
        ) {
            assert publishRequest.getAcceptedState() == newState : "state got switched on us";
            assert transportService.getThreadPool().getThreadContext().isSystemContext();
            final ActionListener<PublishWithJoinResponse> responseActionListener;
            if (destination.equals(discoveryNodes.getLocalNode())) {
                // if publishing to self, use original request instead (see currentPublishRequestToSelf for explanation)
                final PublishRequest previousRequest = currentPublishRequestToSelf.getAndSet(publishRequest);
                // we might override an in-flight publication to self in case where we failed as cluster-manager and
                // became cluster-manager again, and the new publication started before the previous one completed
                // (which fails anyhow because of higher current term)
                assert previousRequest == null || previousRequest.getAcceptedState().term() < publishRequest.getAcceptedState().term();
                responseActionListener = new ActionListener<PublishWithJoinResponse>() {
                    @Override
                    public void onResponse(PublishWithJoinResponse publishWithJoinResponse) {
                        currentPublishRequestToSelf.compareAndSet(publishRequest, null); // only clean-up our mess
                        listener.onResponse(publishWithJoinResponse);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        currentPublishRequestToSelf.compareAndSet(publishRequest, null); // only clean-up our mess
                        listener.onFailure(e);
                    }
                };
            } else {
                responseActionListener = listener;
            }
            if (sendFullVersion || previousState.nodes().nodeExists(destination) == false) {
                logger.trace("sending full cluster state version [{}] to [{}]", newState.version(), destination);
                sendFullClusterState(destination, responseActionListener);
            } else {
                logger.trace("sending cluster state diff for version [{}] to [{}]", newState.version(), destination);
                sendClusterStateDiff(destination, responseActionListener);
            }
        }

        public void sendApplyCommit(
            DiscoveryNode destination,
            ApplyCommitRequest applyCommitRequest,
            ActionListener<TransportResponse.Empty> listener
        ) {
            assert transportService.getThreadPool().getThreadContext().isSystemContext();
            transportService.sendRequest(
                destination,
                COMMIT_STATE_ACTION_NAME,
                applyCommitRequest,
                stateRequestOptions,
                new TransportResponseHandler<TransportResponse.Empty>() {

                    @Override
                    public TransportResponse.Empty read(StreamInput in) {
                        return TransportResponse.Empty.INSTANCE;
                    }

                    @Override
                    public void handleResponse(TransportResponse.Empty response) {
                        listener.onResponse(response);
                    }

                    @Override
                    public void handleException(TransportException exp) {
                        listener.onFailure(exp);
                    }

                    @Override
                    public String executor() {
                        return ThreadPool.Names.GENERIC;
                    }
                }
            );
        }

        private void sendFullClusterState(DiscoveryNode destination, ActionListener<PublishWithJoinResponse> listener) {
            BytesReference bytes = serializedStates.get(destination.getVersion());
            if (bytes == null) {
                try {
                    bytes = ClusterStateUtils.serializeClusterState(newState, destination);
                    serializedStates.put(destination.getVersion(), bytes);
                } catch (Exception e) {
                    logger.warn(
                        () -> new ParameterizedMessage("failed to serialize cluster state before publishing it to node {}", destination),
                        e
                    );
                    listener.onFailure(e);
                    return;
                }
            }
            sendClusterState(destination, bytes, false, listener);
        }

        private void sendClusterStateDiff(DiscoveryNode destination, ActionListener<PublishWithJoinResponse> listener) {
            final BytesReference bytes = serializedDiffs.get(destination.getVersion());
            assert bytes != null : "failed to find serialized diff for node "
                + destination
                + " of version ["
                + destination.getVersion()
                + "]";
            sendClusterState(destination, bytes, true, listener);
        }

        private void sendClusterState(
            DiscoveryNode destination,
            BytesReference bytes,
            boolean retryWithFullClusterStateOnFailure,
            ActionListener<PublishWithJoinResponse> listener
        ) {
            try {
                final BytesTransportRequest request = new BytesTransportRequest(bytes, destination.getVersion());
                final Consumer<TransportException> transportExceptionHandler = exp -> {
                    if (retryWithFullClusterStateOnFailure && exp.unwrapCause() instanceof IncompatibleClusterStateVersionException) {
                        logger.debug("resending full cluster state to node {} reason {}", destination, exp.getDetailedMessage());
                        sendFullClusterState(destination, listener);
                    } else {
                        logger.debug(() -> new ParameterizedMessage("failed to send cluster state to {}", destination), exp);
                        listener.onFailure(exp);
                    }
                };
                final TransportResponseHandler<PublishWithJoinResponse> responseHandler = new TransportResponseHandler<
                    PublishWithJoinResponse>() {

                    @Override
                    public PublishWithJoinResponse read(StreamInput in) throws IOException {
                        return new PublishWithJoinResponse(in);
                    }

                    @Override
                    public void handleResponse(PublishWithJoinResponse response) {
                        listener.onResponse(response);
                    }

                    @Override
                    public void handleException(TransportException exp) {
                        transportExceptionHandler.accept(exp);
                    }

                    @Override
                    public String executor() {
                        return ThreadPool.Names.GENERIC;
                    }
                };
                transportService.sendRequest(destination, PUBLISH_STATE_ACTION_NAME, request, stateRequestOptions, responseHandler);
            } catch (Exception e) {
                logger.warn(() -> new ParameterizedMessage("error sending cluster state to {}", destination), e);
                listener.onFailure(e);
            }
        }
    }

}
