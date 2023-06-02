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

package org.opensearch.cluster.metadata;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.opensearch.OpenSearchException;
import org.opensearch.ResourceAlreadyExistsException;
import org.opensearch.Version;
import org.opensearch.action.ActionListener;
import org.opensearch.action.admin.indices.alias.Alias;
import org.opensearch.action.admin.indices.create.CreateIndexClusterStateUpdateRequest;
import org.opensearch.action.admin.indices.shrink.ResizeType;
import org.opensearch.action.support.ActiveShardCount;
import org.opensearch.action.support.ActiveShardsObserver;
import org.opensearch.cluster.AckedClusterStateUpdateTask;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.ack.ClusterStateUpdateResponse;
import org.opensearch.cluster.ack.CreateIndexClusterStateUpdateResponse;
import org.opensearch.cluster.block.ClusterBlock;
import org.opensearch.cluster.block.ClusterBlockLevel;
import org.opensearch.cluster.block.ClusterBlocks;
import org.opensearch.cluster.node.DiscoveryNodes;
import org.opensearch.cluster.routing.IndexRoutingTable;
import org.opensearch.cluster.routing.RoutingTable;
import org.opensearch.cluster.routing.ShardRouting;
import org.opensearch.cluster.routing.ShardRoutingState;
import org.opensearch.cluster.routing.allocation.AllocationService;
import org.opensearch.cluster.routing.allocation.AwarenessReplicaBalance;
import org.opensearch.cluster.service.ClusterManagerTaskKeys;
import org.opensearch.cluster.service.ClusterManagerTaskThrottler;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.Nullable;
import org.opensearch.common.Priority;
import org.opensearch.common.UUIDs;
import org.opensearch.common.ValidationException;
import org.opensearch.common.compress.CompressedXContent;
import org.opensearch.common.io.PathUtils;
import org.opensearch.common.logging.DeprecationLogger;
import org.opensearch.common.settings.IndexScopedSettings;
import org.opensearch.common.settings.Setting;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentHelper;
import org.opensearch.core.common.Strings;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.env.Environment;
import org.opensearch.index.Index;
import org.opensearch.index.IndexModule;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.IndexService;
import org.opensearch.index.IndexSettings;
import org.opensearch.index.mapper.DocumentMapper;
import org.opensearch.index.mapper.MapperService;
import org.opensearch.index.mapper.MapperService.MergeReason;
import org.opensearch.index.query.QueryShardContext;
import org.opensearch.index.shard.IndexSettingProvider;
import org.opensearch.indices.IndexCreationException;
import org.opensearch.indices.IndicesService;
import org.opensearch.indices.InvalidIndexNameException;
import org.opensearch.indices.ShardLimitValidator;
import org.opensearch.indices.SystemIndices;
import org.opensearch.indices.replication.common.ReplicationType;
import org.opensearch.threadpool.ThreadPool;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static org.opensearch.cluster.metadata.IndexMetadata.INDEX_NUMBER_OF_REPLICAS_SETTING;
import static org.opensearch.cluster.metadata.IndexMetadata.INDEX_NUMBER_OF_SHARDS_SETTING;
import static org.opensearch.cluster.metadata.IndexMetadata.INDEX_REMOTE_STORE_ENABLED_SETTING;
import static org.opensearch.cluster.metadata.IndexMetadata.INDEX_REMOTE_STORE_REPOSITORY_SETTING;
import static org.opensearch.cluster.metadata.IndexMetadata.INDEX_REMOTE_TRANSLOG_REPOSITORY_SETTING;
import static org.opensearch.cluster.metadata.IndexMetadata.INDEX_REMOTE_TRANSLOG_STORE_ENABLED_SETTING;
import static org.opensearch.cluster.metadata.IndexMetadata.INDEX_REPLICATION_TYPE_SETTING;
import static org.opensearch.cluster.metadata.IndexMetadata.SETTING_AUTO_EXPAND_REPLICAS;
import static org.opensearch.cluster.metadata.IndexMetadata.SETTING_CREATION_DATE;
import static org.opensearch.cluster.metadata.IndexMetadata.SETTING_INDEX_UUID;
import static org.opensearch.cluster.metadata.IndexMetadata.SETTING_NUMBER_OF_REPLICAS;
import static org.opensearch.cluster.metadata.IndexMetadata.SETTING_NUMBER_OF_SHARDS;
import static org.opensearch.cluster.metadata.IndexMetadata.SETTING_REMOTE_STORE_ENABLED;
import static org.opensearch.cluster.metadata.IndexMetadata.SETTING_REMOTE_STORE_REPOSITORY;
import static org.opensearch.cluster.metadata.IndexMetadata.SETTING_REMOTE_TRANSLOG_STORE_ENABLED;
import static org.opensearch.cluster.metadata.IndexMetadata.SETTING_REMOTE_TRANSLOG_STORE_REPOSITORY;
import static org.opensearch.cluster.metadata.IndexMetadata.SETTING_REPLICATION_TYPE;
import static org.opensearch.cluster.metadata.Metadata.DEFAULT_REPLICA_COUNT_SETTING;
import static org.opensearch.indices.IndicesService.CLUSTER_REMOTE_STORE_REPOSITORY_SETTING;
import static org.opensearch.indices.IndicesService.CLUSTER_REMOTE_TRANSLOG_REPOSITORY_SETTING;
import static org.opensearch.indices.IndicesService.CLUSTER_REMOTE_STORE_ENABLED_SETTING;
import static org.opensearch.indices.IndicesService.CLUSTER_REMOTE_TRANSLOG_STORE_ENABLED_SETTING;
import static org.opensearch.indices.IndicesService.CLUSTER_REPLICATION_TYPE_SETTING;

/**
 * Service responsible for submitting create index requests
 *
 * @opensearch.internal
 */
public class MetadataCreateIndexService {
    private static final Logger logger = LogManager.getLogger(MetadataCreateIndexService.class);
    private static final DeprecationLogger DEPRECATION_LOGGER = DeprecationLogger.getLogger(MetadataCreateIndexService.class);

    public static final int MAX_INDEX_NAME_BYTES = 255;

    private final Settings settings;
    private final ClusterService clusterService;
    private final IndicesService indicesService;
    private final AllocationService allocationService;
    private final AliasValidator aliasValidator;
    private final Environment env;
    private final IndexScopedSettings indexScopedSettings;
    private final ActiveShardsObserver activeShardsObserver;
    private final NamedXContentRegistry xContentRegistry;
    private final SystemIndices systemIndices;
    private final ShardLimitValidator shardLimitValidator;
    private final boolean forbidPrivateIndexSettings;
    private final Set<IndexSettingProvider> indexSettingProviders = new HashSet<>();
    private final ClusterManagerTaskThrottler.ThrottlingKey createIndexTaskKey;
    private AwarenessReplicaBalance awarenessReplicaBalance;

    public MetadataCreateIndexService(
        final Settings settings,
        final ClusterService clusterService,
        final IndicesService indicesService,
        final AllocationService allocationService,
        final AliasValidator aliasValidator,
        final ShardLimitValidator shardLimitValidator,
        final Environment env,
        final IndexScopedSettings indexScopedSettings,
        final ThreadPool threadPool,
        final NamedXContentRegistry xContentRegistry,
        final SystemIndices systemIndices,
        final boolean forbidPrivateIndexSettings,
        final AwarenessReplicaBalance awarenessReplicaBalance
    ) {
        this.settings = settings;
        this.clusterService = clusterService;
        this.indicesService = indicesService;
        this.allocationService = allocationService;
        this.aliasValidator = aliasValidator;
        this.env = env;
        this.indexScopedSettings = indexScopedSettings;
        this.activeShardsObserver = new ActiveShardsObserver(clusterService, threadPool);
        this.xContentRegistry = xContentRegistry;
        this.systemIndices = systemIndices;
        this.forbidPrivateIndexSettings = forbidPrivateIndexSettings;
        this.shardLimitValidator = shardLimitValidator;
        this.awarenessReplicaBalance = awarenessReplicaBalance;

        // Task is onboarded for throttling, it will get retried from associated TransportClusterManagerNodeAction.
        createIndexTaskKey = clusterService.registerClusterManagerTask(ClusterManagerTaskKeys.CREATE_INDEX_KEY, true);
    }

    /**
     * Add a provider to be invoked to get additional index settings prior to an index being created
     */
    public void addAdditionalIndexSettingProvider(IndexSettingProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("provider must not be null");
        }
        if (indexSettingProviders.contains(provider)) {
            throw new IllegalArgumentException("provider already added");
        }
        this.indexSettingProviders.add(provider);
    }

    /**
     * Validate the name for an index against some static rules and a cluster state.
     */
    public void validateIndexName(String index, ClusterState state) {
        validateIndexOrAliasName(index, InvalidIndexNameException::new);
        if (!index.toLowerCase(Locale.ROOT).equals(index)) {
            throw new InvalidIndexNameException(index, "must be lowercase");
        }

        // NOTE: dot-prefixed index names are validated after template application, not here

        if (state.routingTable().hasIndex(index)) {
            throw new ResourceAlreadyExistsException(state.routingTable().index(index).getIndex());
        }
        if (state.metadata().hasIndex(index)) {
            throw new ResourceAlreadyExistsException(state.metadata().index(index).getIndex());
        }
        if (state.metadata().hasAlias(index)) {
            throw new InvalidIndexNameException(index, "already exists as alias");
        }
    }

    /**
     * Validates (if this index has a dot-prefixed name) whether it follows the rules for dot-prefixed indices.
     * @param index The name of the index in question
     * @param isHidden Whether or not this is a hidden index
     */
    public boolean validateDotIndex(String index, @Nullable Boolean isHidden) {
        if (index.charAt(0) == '.') {
            if (systemIndices.validateSystemIndex(index)) {
                return true;
            } else if (isHidden) {
                logger.trace("index [{}] is a hidden index", index);
            } else {
                DEPRECATION_LOGGER.deprecate(
                    "index_name_starts_with_dot",
                    "index name [{}] starts with a dot '.', in the next major version, index names "
                        + "starting with a dot are reserved for hidden indices and system indices",
                    index
                );
            }
        }

        return false;
    }

    /**
     * Validate the name for an index or alias against some static rules.
     */
    public static void validateIndexOrAliasName(String index, BiFunction<String, String, ? extends RuntimeException> exceptionCtor) {
        if (org.opensearch.common.Strings.validFileName(index) == false) {
            throw exceptionCtor.apply(
                index,
                "must not contain the following characters " + org.opensearch.common.Strings.INVALID_FILENAME_CHARS
            );
        }
        if (index.isEmpty()) {
            throw exceptionCtor.apply(index, "must not be empty");
        }
        if (index.contains("#")) {
            throw exceptionCtor.apply(index, "must not contain '#'");
        }
        if (index.contains(":")) {
            throw exceptionCtor.apply(index, "must not contain ':'");
        }
        if (index.charAt(0) == '_' || index.charAt(0) == '-' || index.charAt(0) == '+') {
            throw exceptionCtor.apply(index, "must not start with '_', '-', or '+'");
        }
        int byteCount = 0;
        try {
            byteCount = index.getBytes("UTF-8").length;
        } catch (UnsupportedEncodingException e) {
            // UTF-8 should always be supported, but rethrow this if it is not for some reason
            throw new OpenSearchException("Unable to determine length of index name", e);
        }
        if (byteCount > MAX_INDEX_NAME_BYTES) {
            throw exceptionCtor.apply(index, "index name is too long, (" + byteCount + " > " + MAX_INDEX_NAME_BYTES + ")");
        }
        if (index.equals(".") || index.equals("..")) {
            throw exceptionCtor.apply(index, "must not be '.' or '..'");
        }
    }

    /**
     * Creates an index in the cluster state and waits for the specified number of shard copies to
     * become active (as specified in {@link CreateIndexClusterStateUpdateRequest#waitForActiveShards()})
     * before sending the response on the listener. If the index creation was successfully applied on
     * the cluster state, then {@link CreateIndexClusterStateUpdateResponse#isAcknowledged()} will return
     * true, otherwise it will return false and no waiting will occur for started shards
     * ({@link CreateIndexClusterStateUpdateResponse#isShardsAcknowledged()} will also be false).  If the index
     * creation in the cluster state was successful and the requisite shard copies were started before
     * the timeout, then {@link CreateIndexClusterStateUpdateResponse#isShardsAcknowledged()} will
     * return true, otherwise if the operation timed out, then it will return false.
     *
     * @param request the index creation cluster state update request
     * @param listener the listener on which to send the index creation cluster state update response
     */
    public void createIndex(
        final CreateIndexClusterStateUpdateRequest request,
        final ActionListener<CreateIndexClusterStateUpdateResponse> listener
    ) {
        onlyCreateIndex(request, ActionListener.wrap(response -> {
            if (response.isAcknowledged()) {
                activeShardsObserver.waitForActiveShards(
                    new String[] { request.index() },
                    request.waitForActiveShards(),
                    request.ackTimeout(),
                    shardsAcknowledged -> {
                        if (shardsAcknowledged == false) {
                            logger.debug(
                                "[{}] index created, but the operation timed out while waiting for " + "enough shards to be started.",
                                request.index()
                            );
                        }
                        listener.onResponse(new CreateIndexClusterStateUpdateResponse(response.isAcknowledged(), shardsAcknowledged));
                    },
                    listener::onFailure
                );
            } else {
                listener.onResponse(new CreateIndexClusterStateUpdateResponse(false, false));
            }
        }, listener::onFailure));
    }

    private void onlyCreateIndex(
        final CreateIndexClusterStateUpdateRequest request,
        final ActionListener<ClusterStateUpdateResponse> listener
    ) {
        normalizeRequestSetting(request);
        clusterService.submitStateUpdateTask(
            "create-index [" + request.index() + "], cause [" + request.cause() + "]",
            new AckedClusterStateUpdateTask<ClusterStateUpdateResponse>(Priority.URGENT, request, listener) {
                @Override
                protected ClusterStateUpdateResponse newResponse(boolean acknowledged) {
                    return new ClusterStateUpdateResponse(acknowledged);
                }

                @Override
                public ClusterManagerTaskThrottler.ThrottlingKey getClusterManagerThrottlingKey() {
                    return createIndexTaskKey;
                }

                @Override
                public ClusterState execute(ClusterState currentState) throws Exception {
                    return applyCreateIndexRequest(currentState, request, false);
                }

                @Override
                public void onFailure(String source, Exception e) {
                    if (e instanceof ResourceAlreadyExistsException) {
                        logger.trace(() -> new ParameterizedMessage("[{}] failed to create", request.index()), e);
                    } else {
                        logger.debug(() -> new ParameterizedMessage("[{}] failed to create", request.index()), e);
                    }
                    super.onFailure(source, e);
                }
            }
        );
    }

    private void normalizeRequestSetting(CreateIndexClusterStateUpdateRequest createIndexClusterStateRequest) {
        Settings.Builder updatedSettingsBuilder = Settings.builder();
        Settings build = updatedSettingsBuilder.put(createIndexClusterStateRequest.settings())
            .normalizePrefix(IndexMetadata.INDEX_SETTING_PREFIX)
            .build();
        indexScopedSettings.validate(build, true);
        createIndexClusterStateRequest.settings(build);
    }

    /**
     * Handles the cluster state transition to a version that reflects the {@link CreateIndexClusterStateUpdateRequest}.
     * All the requested changes are firstly validated before mutating the {@link ClusterState}.
     */
    public ClusterState applyCreateIndexRequest(
        ClusterState currentState,
        CreateIndexClusterStateUpdateRequest request,
        boolean silent,
        BiConsumer<Metadata.Builder, IndexMetadata> metadataTransformer
    ) throws Exception {

        normalizeRequestSetting(request);
        logger.trace("executing IndexCreationTask for [{}] against cluster state version [{}]", request, currentState.version());

        validate(request, currentState);

        final Index recoverFromIndex = request.recoverFrom();
        final IndexMetadata sourceMetadata = recoverFromIndex == null ? null : currentState.metadata().getIndexSafe(recoverFromIndex);

        if (sourceMetadata != null) {
            // If source metadata was provided, it means we're recovering from an existing index,
            // in which case templates don't apply, so create the index from the source metadata
            return applyCreateIndexRequestWithExistingMetadata(currentState, request, silent, sourceMetadata, metadataTransformer);
        } else {
            // Hidden indices apply templates slightly differently (ignoring wildcard '*'
            // templates), so we need to check to see if the request is creating a hidden index
            // prior to resolving which templates it matches
            final Boolean isHiddenFromRequest = IndexMetadata.INDEX_HIDDEN_SETTING.exists(request.settings())
                ? IndexMetadata.INDEX_HIDDEN_SETTING.get(request.settings())
                : null;

            // The backing index may have a different name or prefix than the data stream name.
            final String name = request.dataStreamName() != null ? request.dataStreamName() : request.index();
            // Check to see if a v2 template matched
            final String v2Template = MetadataIndexTemplateService.findV2Template(
                currentState.metadata(),
                name,
                isHiddenFromRequest == null ? false : isHiddenFromRequest
            );

            if (v2Template != null) {
                // If a v2 template was found, it takes precedence over all v1 templates, so create
                // the index using that template and the request's specified settings
                return applyCreateIndexRequestWithV2Template(currentState, request, silent, v2Template, metadataTransformer);
            } else {
                // A v2 template wasn't found, check the v1 templates, in the event no templates are
                // found creation still works using the request's specified index settings
                final List<IndexTemplateMetadata> v1Templates = MetadataIndexTemplateService.findV1Templates(
                    currentState.metadata(),
                    request.index(),
                    isHiddenFromRequest
                );

                if (v1Templates.size() > 1) {
                    DEPRECATION_LOGGER.deprecate(
                        "index_template_multiple_match",
                        "index [{}] matches multiple legacy templates [{}], composable templates will only match a single template",
                        request.index(),
                        v1Templates.stream().map(IndexTemplateMetadata::name).sorted().collect(Collectors.joining(", "))
                    );
                }

                return applyCreateIndexRequestWithV1Templates(currentState, request, silent, v1Templates, metadataTransformer);
            }
        }
    }

    public ClusterState applyCreateIndexRequest(ClusterState currentState, CreateIndexClusterStateUpdateRequest request, boolean silent)
        throws Exception {
        return applyCreateIndexRequest(currentState, request, silent, null);
    }

    /**
     * Given the state and a request as well as the metadata necessary to build a new index,
     * validate the configuration with an actual index service as return a new cluster state with
     * the index added (and rerouted)
     * @param currentState the current state to base the new state off of
     * @param request the create index request
     * @param silent a boolean for whether logging should be at a lower or higher level
     * @param sourceMetadata when recovering from an existing index, metadata that should be copied to the new index
     * @param temporaryIndexMeta metadata for the new index built from templates, source metadata, and request settings
     * @param mappings a list of all mapping definitions to apply, in order
     * @param aliasSupplier a function that takes the real {@link IndexService} and returns a list of {@link AliasMetadata} aliases
     * @param templatesApplied a list of the names of the templates applied, for logging
     * @param metadataTransformer if provided, a function that may alter cluster metadata in the same cluster state update that
     *                            creates the index
     * @return a new cluster state with the index added
     */
    private ClusterState applyCreateIndexWithTemporaryService(
        final ClusterState currentState,
        final CreateIndexClusterStateUpdateRequest request,
        final boolean silent,
        final IndexMetadata sourceMetadata,
        final IndexMetadata temporaryIndexMeta,
        final List<Map<String, Object>> mappings,
        final Function<IndexService, List<AliasMetadata>> aliasSupplier,
        final List<String> templatesApplied,
        final BiConsumer<Metadata.Builder, IndexMetadata> metadataTransformer
    ) throws Exception {
        // create the index here (on the master) to validate it can be created, as well as adding the mapping
        return indicesService.<ClusterState, Exception>withTempIndexService(temporaryIndexMeta, indexService -> {
            try {
                updateIndexMappingsAndBuildSortOrder(indexService, request, mappings, sourceMetadata);
            } catch (Exception e) {
                logger.log(silent ? Level.DEBUG : Level.INFO, "failed on parsing mappings on index creation [{}]", request.index(), e);
                throw e;
            }

            final List<AliasMetadata> aliases = aliasSupplier.apply(indexService);

            final IndexMetadata indexMetadata;
            try {
                indexMetadata = buildIndexMetadata(
                    request.index(),
                    aliases,
                    indexService.mapperService()::documentMapper,
                    temporaryIndexMeta.getSettings(),
                    temporaryIndexMeta.getRoutingNumShards(),
                    sourceMetadata,
                    temporaryIndexMeta.isSystem()
                );
            } catch (Exception e) {
                logger.info("failed to build index metadata [{}]", request.index());
                throw e;
            }

            logger.log(
                silent ? Level.DEBUG : Level.INFO,
                "[{}] creating index, cause [{}], templates {}, shards [{}]/[{}]",
                request.index(),
                request.cause(),
                templatesApplied,
                indexMetadata.getNumberOfShards(),
                indexMetadata.getNumberOfReplicas()
            );

            indexService.getIndexEventListener().beforeIndexAddedToCluster(indexMetadata.getIndex(), indexMetadata.getSettings());
            return clusterStateCreateIndex(currentState, request.blocks(), indexMetadata, allocationService::reroute, metadataTransformer);
        });
    }

    /**
     * Given a state and index settings calculated after applying templates, validate metadata for
     * the new index, returning an {@link IndexMetadata} for the new index
     */
    private IndexMetadata buildAndValidateTemporaryIndexMetadata(
        final ClusterState currentState,
        final Settings aggregatedIndexSettings,
        final CreateIndexClusterStateUpdateRequest request,
        final int routingNumShards
    ) {

        final boolean isHiddenAfterTemplates = IndexMetadata.INDEX_HIDDEN_SETTING.get(aggregatedIndexSettings);
        final boolean isSystem = validateDotIndex(request.index(), isHiddenAfterTemplates);

        // remove the setting it's temporary and is only relevant once we create the index
        final Settings.Builder settingsBuilder = Settings.builder().put(aggregatedIndexSettings);
        settingsBuilder.remove(IndexMetadata.INDEX_NUMBER_OF_ROUTING_SHARDS_SETTING.getKey());
        final Settings indexSettings = settingsBuilder.build();

        final IndexMetadata.Builder tmpImdBuilder = IndexMetadata.builder(request.index());
        tmpImdBuilder.setRoutingNumShards(routingNumShards);
        tmpImdBuilder.settings(indexSettings);
        tmpImdBuilder.system(isSystem);

        // Set up everything, now locally create the index to see that things are ok, and apply
        IndexMetadata tempMetadata = tmpImdBuilder.build();
        validateActiveShardCount(request.waitForActiveShards(), tempMetadata);

        return tempMetadata;
    }

    private ClusterState applyCreateIndexRequestWithV1Templates(
        final ClusterState currentState,
        final CreateIndexClusterStateUpdateRequest request,
        final boolean silent,
        final List<IndexTemplateMetadata> templates,
        final BiConsumer<Metadata.Builder, IndexMetadata> metadataTransformer
    ) throws Exception {
        logger.debug(
            "applying create index request using legacy templates {}",
            templates.stream().map(IndexTemplateMetadata::name).collect(Collectors.toList())
        );

        final Map<String, Object> mappings = Collections.unmodifiableMap(
            parseV1Mappings(
                request.mappings(),
                templates.stream().map(IndexTemplateMetadata::getMappings).collect(toList()),
                xContentRegistry
            )
        );
        final Settings aggregatedIndexSettings = aggregateIndexSettings(
            currentState,
            request,
            MetadataIndexTemplateService.resolveSettings(templates),
            null,
            settings,
            indexScopedSettings,
            shardLimitValidator,
            indexSettingProviders,
            systemIndices.validateSystemIndex(request.index())
        );
        int routingNumShards = getIndexNumberOfRoutingShards(aggregatedIndexSettings, null);
        IndexMetadata tmpImd = buildAndValidateTemporaryIndexMetadata(currentState, aggregatedIndexSettings, request, routingNumShards);

        return applyCreateIndexWithTemporaryService(
            currentState,
            request,
            silent,
            null,
            tmpImd,
            Collections.singletonList(mappings),
            indexService -> resolveAndValidateAliases(
                request.index(),
                request.aliases(),
                MetadataIndexTemplateService.resolveAliases(templates),
                currentState.metadata(),
                aliasValidator,
                // the context is only used for validation so it's fine to pass fake values for the
                // shard id and the current timestamp
                xContentRegistry,
                indexService.newQueryShardContext(0, null, () -> 0L, null)
            ),
            templates.stream().map(IndexTemplateMetadata::getName).collect(toList()),
            metadataTransformer
        );
    }

    private ClusterState applyCreateIndexRequestWithV2Template(
        final ClusterState currentState,
        final CreateIndexClusterStateUpdateRequest request,
        final boolean silent,
        final String templateName,
        final BiConsumer<Metadata.Builder, IndexMetadata> metadataTransformer
    ) throws Exception {
        logger.debug("applying create index request using composable template [{}]", templateName);

        ComposableIndexTemplate template = currentState.getMetadata().templatesV2().get(templateName);
        if (request.dataStreamName() == null && template.getDataStreamTemplate() != null) {
            throw new IllegalArgumentException(
                "cannot create index with name ["
                    + request.index()
                    + "], because it matches with template ["
                    + templateName
                    + "] that creates data streams only, "
                    + "use create data stream api instead"
            );
        }

        final List<Map<String, Object>> mappings = collectV2Mappings(
            request.mappings(),
            currentState,
            templateName,
            xContentRegistry,
            request.index()
        );
        final Settings aggregatedIndexSettings = aggregateIndexSettings(
            currentState,
            request,
            MetadataIndexTemplateService.resolveSettings(currentState.metadata(), templateName),
            null,
            settings,
            indexScopedSettings,
            shardLimitValidator,
            indexSettingProviders,
            systemIndices.validateSystemIndex(request.index())
        );
        int routingNumShards = getIndexNumberOfRoutingShards(aggregatedIndexSettings, null);
        IndexMetadata tmpImd = buildAndValidateTemporaryIndexMetadata(currentState, aggregatedIndexSettings, request, routingNumShards);

        return applyCreateIndexWithTemporaryService(
            currentState,
            request,
            silent,
            null,
            tmpImd,
            mappings,
            indexService -> resolveAndValidateAliases(
                request.index(),
                request.aliases(),
                MetadataIndexTemplateService.resolveAliases(currentState.metadata(), templateName),
                currentState.metadata(),
                aliasValidator,
                // the context is only used for validation so it's fine to pass fake values for the
                // shard id and the current timestamp
                xContentRegistry,
                indexService.newQueryShardContext(0, null, () -> 0L, null)
            ),
            Collections.singletonList(templateName),
            metadataTransformer
        );
    }

    public static List<Map<String, Object>> collectV2Mappings(
        final String requestMappings,
        final ClusterState currentState,
        final String templateName,
        final NamedXContentRegistry xContentRegistry,
        final String indexName
    ) throws Exception {
        List<CompressedXContent> templateMappings = MetadataIndexTemplateService.collectMappings(currentState, templateName, indexName);
        return collectV2Mappings(requestMappings, templateMappings, xContentRegistry);
    }

    public static List<Map<String, Object>> collectV2Mappings(
        final String requestMappings,
        final List<CompressedXContent> templateMappings,
        final NamedXContentRegistry xContentRegistry
    ) throws Exception {
        List<Map<String, Object>> result = new ArrayList<>();

        for (CompressedXContent templateMapping : templateMappings) {
            Map<String, Object> parsedTemplateMapping = MapperService.parseMapping(xContentRegistry, templateMapping.string());
            result.add(parsedTemplateMapping);
        }

        Map<String, Object> parsedRequestMappings = MapperService.parseMapping(xContentRegistry, requestMappings);
        result.add(parsedRequestMappings);
        return result;
    }

    private ClusterState applyCreateIndexRequestWithExistingMetadata(
        final ClusterState currentState,
        final CreateIndexClusterStateUpdateRequest request,
        final boolean silent,
        final IndexMetadata sourceMetadata,
        final BiConsumer<Metadata.Builder, IndexMetadata> metadataTransformer
    ) throws Exception {
        logger.info("applying create index request using existing index [{}] metadata", sourceMetadata.getIndex().getName());

        final Map<String, Object> mappings = MapperService.parseMapping(xContentRegistry, request.mappings());
        if (mappings.isEmpty() == false) {
            throw new IllegalArgumentException(
                "mappings are not allowed when creating an index from a source index, " + "all mappings are copied from the source index"
            );
        }

        final Settings aggregatedIndexSettings = aggregateIndexSettings(
            currentState,
            request,
            Settings.EMPTY,
            sourceMetadata,
            settings,
            indexScopedSettings,
            shardLimitValidator,
            indexSettingProviders,
            sourceMetadata.isSystem()
        );
        final int routingNumShards = getIndexNumberOfRoutingShards(aggregatedIndexSettings, sourceMetadata);
        IndexMetadata tmpImd = buildAndValidateTemporaryIndexMetadata(currentState, aggregatedIndexSettings, request, routingNumShards);

        return applyCreateIndexWithTemporaryService(
            currentState,
            request,
            silent,
            sourceMetadata,
            tmpImd,
            Collections.singletonList(mappings),
            indexService -> resolveAndValidateAliases(
                request.index(),
                request.aliases(),
                Collections.emptyList(),
                currentState.metadata(),
                aliasValidator,
                xContentRegistry,
                // the context is only used for validation so it's fine to pass fake values for the
                // shard id and the current timestamp
                indexService.newQueryShardContext(0, null, () -> 0L, null)
            ),
            List.of(),
            metadataTransformer
        );
    }

    /**
     * Parses the provided mappings json and the inheritable mappings from the templates (if any)
     * into a map.
     *
     * The template mappings are applied in the order they are encountered in the list (clients
     * should make sure the lower index, closer to the head of the list, templates have the highest
     * {@link IndexTemplateMetadata#order()}). This merging makes no distinction between field
     * definitions, as may result in an invalid field definition
     */
    static Map<String, Object> parseV1Mappings(
        String requestMappings,
        List<CompressedXContent> templateMappings,
        NamedXContentRegistry xContentRegistry
    ) throws Exception {
        Map<String, Object> mappings = MapperService.parseMapping(xContentRegistry, requestMappings);
        // apply templates, merging the mappings into the request mapping if exists
        for (CompressedXContent mapping : templateMappings) {
            if (mapping != null) {
                Map<String, Object> templateMapping = MapperService.parseMapping(xContentRegistry, mapping.string());
                if (templateMapping.isEmpty()) {
                    // Someone provided an empty '{}' for mappings, which is okay, but to avoid
                    // tripping the below assertion, we can safely ignore it
                    continue;
                }
                assert templateMapping.size() == 1 : "expected exactly one mapping value, got: " + templateMapping;
                // pre-8x templates may have a wrapper type other than _doc, so we re-wrap things here
                templateMapping = Collections.singletonMap(MapperService.SINGLE_MAPPING_NAME, templateMapping.values().iterator().next());
                if (mappings.isEmpty()) {
                    mappings = templateMapping;
                } else {
                    XContentHelper.mergeDefaults(mappings, templateMapping);
                }
            }
        }
        return mappings;
    }

    /**
     * Validates and creates the settings for the new index based on the explicitly configured settings via the
     * {@link CreateIndexClusterStateUpdateRequest}, inherited from templates and, if recovering from another index (ie. split, shrink,
     * clone), the resize settings.
     *
     * The template mappings are applied in the order they are encountered in the list (clients should make sure the lower index, closer
     * to the head of the list, templates have the highest {@link IndexTemplateMetadata#order()})
     *
     * @return the aggregated settings for the new index
     */
    static Settings aggregateIndexSettings(
        ClusterState currentState,
        CreateIndexClusterStateUpdateRequest request,
        Settings combinedTemplateSettings,
        @Nullable IndexMetadata sourceMetadata,
        Settings settings,
        IndexScopedSettings indexScopedSettings,
        ShardLimitValidator shardLimitValidator,
        Set<IndexSettingProvider> indexSettingProviders,
        boolean isSystemIndex
    ) {
        // Create builders for the template and request settings. We transform these into builders
        // because we may want settings to be "removed" from these prior to being set on the new
        // index (see more comments below)
        final Settings.Builder templateSettings = Settings.builder().put(combinedTemplateSettings);
        final Settings.Builder requestSettings = Settings.builder().put(request.settings());

        final Settings.Builder indexSettingsBuilder = Settings.builder();
        if (sourceMetadata == null) {
            final Settings.Builder additionalIndexSettings = Settings.builder();
            final Settings templateAndRequestSettings = Settings.builder().put(combinedTemplateSettings).put(request.settings()).build();

            final boolean isDataStreamIndex = request.dataStreamName() != null;
            // Loop through all the explicit index setting providers, adding them to the
            // additionalIndexSettings map
            for (IndexSettingProvider provider : indexSettingProviders) {
                additionalIndexSettings.put(
                    provider.getAdditionalIndexSettings(request.index(), isDataStreamIndex, templateAndRequestSettings)
                );
            }

            // For all the explicit settings, we go through the template and request level settings
            // and see if either a template or the request has "cancelled out" an explicit default
            // setting. For example, if a plugin had as an explicit setting:
            // "index.mysetting": "blah
            // And either a template or create index request had:
            // "index.mysetting": null
            // We want to remove the explicit setting not only from the explicitly set settings, but
            // also from the template and request settings, so that from the newly create index's
            // perspective it is as though the setting has not been set at all (using the default
            // value).
            for (String explicitSetting : additionalIndexSettings.keys()) {
                if (templateSettings.keys().contains(explicitSetting) && templateSettings.get(explicitSetting) == null) {
                    logger.debug(
                        "removing default [{}] setting as it in set to null in a template for [{}] creation",
                        explicitSetting,
                        request.index()
                    );
                    additionalIndexSettings.remove(explicitSetting);
                    templateSettings.remove(explicitSetting);
                }
                if (requestSettings.keys().contains(explicitSetting) && requestSettings.get(explicitSetting) == null) {
                    logger.debug(
                        "removing default [{}] setting as it in set to null in the request for [{}] creation",
                        explicitSetting,
                        request.index()
                    );
                    additionalIndexSettings.remove(explicitSetting);
                    requestSettings.remove(explicitSetting);
                }
            }

            // Finally, we actually add the explicit defaults prior to the template settings and the
            // request settings, so that the precedence goes:
            // Explicit Defaults -> Template -> Request -> Necessary Settings (# of shards, uuid, etc)
            indexSettingsBuilder.put(additionalIndexSettings.build());
            indexSettingsBuilder.put(templateSettings.build());
        }

        // now, put the request settings, so they override templates
        indexSettingsBuilder.put(requestSettings.build());

        if (indexSettingsBuilder.get(IndexMetadata.SETTING_INDEX_VERSION_CREATED.getKey()) == null) {
            final DiscoveryNodes nodes = currentState.nodes();
            final Version createdVersion = Version.min(Version.CURRENT, nodes.getSmallestNonClientNodeVersion());
            indexSettingsBuilder.put(IndexMetadata.SETTING_INDEX_VERSION_CREATED.getKey(), createdVersion);
        }
        if (INDEX_NUMBER_OF_SHARDS_SETTING.exists(indexSettingsBuilder) == false) {
            indexSettingsBuilder.put(SETTING_NUMBER_OF_SHARDS, INDEX_NUMBER_OF_SHARDS_SETTING.get(settings));
        }
        if (INDEX_NUMBER_OF_REPLICAS_SETTING.exists(indexSettingsBuilder) == false) {
            indexSettingsBuilder.put(SETTING_NUMBER_OF_REPLICAS, DEFAULT_REPLICA_COUNT_SETTING.get(currentState.metadata().settings()));
        }
        if (settings.get(SETTING_AUTO_EXPAND_REPLICAS) != null && indexSettingsBuilder.get(SETTING_AUTO_EXPAND_REPLICAS) == null) {
            indexSettingsBuilder.put(SETTING_AUTO_EXPAND_REPLICAS, settings.get(SETTING_AUTO_EXPAND_REPLICAS));
        }

        if (indexSettingsBuilder.get(SETTING_CREATION_DATE) == null) {
            indexSettingsBuilder.put(SETTING_CREATION_DATE, Instant.now().toEpochMilli());
        }
        indexSettingsBuilder.put(IndexMetadata.SETTING_INDEX_PROVIDED_NAME, request.getProvidedName());
        indexSettingsBuilder.put(SETTING_INDEX_UUID, UUIDs.randomBase64UUID());

        updateReplicationStrategy(indexSettingsBuilder, request.settings(), settings, isSystemIndex);
        updateRemoteStoreSettings(indexSettingsBuilder, request.settings(), settings);

        if (sourceMetadata != null) {
            assert request.resizeType() != null;
            prepareResizeIndexSettings(
                currentState,
                indexSettingsBuilder,
                request.recoverFrom(),
                request.index(),
                request.resizeType(),
                request.copySettings(),
                indexScopedSettings
            );
        }

        Settings indexSettings = indexSettingsBuilder.build();
        /*
         * We can not validate settings until we have applied templates, otherwise we do not know the actual settings
         * that will be used to create this index.
         */
        shardLimitValidator.validateShardLimit(request.index(), indexSettings, currentState);
        if (IndexSettings.INDEX_SOFT_DELETES_SETTING.get(indexSettings) == false
            && IndexMetadata.SETTING_INDEX_VERSION_CREATED.get(indexSettings).onOrAfter(Version.V_2_0_0)) {
            throw new IllegalArgumentException(
                "Creating indices with soft-deletes disabled is no longer supported. "
                    + "Please do not specify a value for setting [index.soft_deletes.enabled]."
            );
        }
        validateTranslogRetentionSettings(indexSettings);
        validateStoreTypeSettings(indexSettings);

        return indexSettings;
    }

    /**
     * Updates index settings to set replication strategy by default based on cluster level settings
     * @param settingsBuilder index settings builder to be updated with relevant settings
     * @param requestSettings settings passed in during index create request
     * @param clusterSettings cluster level settings
     */
    private static void updateReplicationStrategy(
        Settings.Builder settingsBuilder,
        Settings requestSettings,
        Settings clusterSettings,
        boolean isSystemIndex
    ) {
        if (isSystemIndex || IndexMetadata.INDEX_HIDDEN_SETTING.get(requestSettings)) {
            settingsBuilder.put(SETTING_REPLICATION_TYPE, ReplicationType.DOCUMENT);
            return;
        }
        if (CLUSTER_REPLICATION_TYPE_SETTING.exists(clusterSettings) && INDEX_REPLICATION_TYPE_SETTING.exists(requestSettings) == false) {
            settingsBuilder.put(SETTING_REPLICATION_TYPE, CLUSTER_REPLICATION_TYPE_SETTING.get(clusterSettings));
        }
    }

    /**
     * Updates index settings to enable remote store by default based on cluster level settings
     * @param settingsBuilder index settings builder to be updated with relevant settings
     * @param requestSettings settings passed in during index create request
     * @param clusterSettings cluster level settings
     */
    private static void updateRemoteStoreSettings(Settings.Builder settingsBuilder, Settings requestSettings, Settings clusterSettings) {
        if (CLUSTER_REMOTE_STORE_ENABLED_SETTING.get(clusterSettings)) {
            // Verify if we can create a remote store based index based on user provided settings
            if (canCreateRemoteStoreIndex(requestSettings) == false) {
                return;
            }

            // Verify REPLICATION_TYPE cluster level setting is not conflicting with Remote Store
            if (INDEX_REPLICATION_TYPE_SETTING.exists(requestSettings) == false
                && CLUSTER_REPLICATION_TYPE_SETTING.get(clusterSettings).equals(ReplicationType.DOCUMENT)) {
                throw new IllegalArgumentException(
                    "Cannot enable ["
                        + SETTING_REMOTE_STORE_ENABLED
                        + "] when ["
                        + CLUSTER_REPLICATION_TYPE_SETTING.getKey()
                        + "] is "
                        + ReplicationType.DOCUMENT
                );
            }

            settingsBuilder.put(SETTING_REPLICATION_TYPE, ReplicationType.SEGMENT).put(SETTING_REMOTE_STORE_ENABLED, true);

            String remoteStoreRepo;
            if (Objects.equals(requestSettings.get(INDEX_REMOTE_STORE_ENABLED_SETTING.getKey()), "true")) {
                remoteStoreRepo = requestSettings.get(INDEX_REMOTE_STORE_REPOSITORY_SETTING.getKey());
            } else {
                remoteStoreRepo = CLUSTER_REMOTE_STORE_REPOSITORY_SETTING.get(clusterSettings);
            }
            settingsBuilder.put(SETTING_REMOTE_STORE_REPOSITORY, remoteStoreRepo);

            boolean translogStoreEnabled = false;
            String translogStoreRepo = null;
            if (Objects.equals(requestSettings.get(INDEX_REMOTE_TRANSLOG_STORE_ENABLED_SETTING.getKey()), "true")) {
                translogStoreEnabled = true;
                translogStoreRepo = requestSettings.get(INDEX_REMOTE_TRANSLOG_REPOSITORY_SETTING.getKey());
            } else if (Objects.equals(requestSettings.get(INDEX_REMOTE_TRANSLOG_STORE_ENABLED_SETTING.getKey()), "false") == false
                && CLUSTER_REMOTE_TRANSLOG_STORE_ENABLED_SETTING.get(clusterSettings)) {
                    translogStoreEnabled = true;
                    translogStoreRepo = CLUSTER_REMOTE_TRANSLOG_REPOSITORY_SETTING.get(clusterSettings);
                }
            if (translogStoreEnabled) {
                settingsBuilder.put(SETTING_REMOTE_TRANSLOG_STORE_ENABLED, translogStoreEnabled)
                    .put(SETTING_REMOTE_TRANSLOG_STORE_REPOSITORY, translogStoreRepo);
            }
        }
    }

    private static boolean canCreateRemoteStoreIndex(Settings requestSettings) {
        return (INDEX_REPLICATION_TYPE_SETTING.exists(requestSettings) == false
            || INDEX_REPLICATION_TYPE_SETTING.get(requestSettings).equals(ReplicationType.SEGMENT))
            && (INDEX_REMOTE_STORE_ENABLED_SETTING.exists(requestSettings) == false
                || INDEX_REMOTE_STORE_ENABLED_SETTING.get(requestSettings));
    }

    public static void validateStoreTypeSettings(Settings settings) {
        // deprecate simplefs store type:
        if (IndexModule.Type.SIMPLEFS.match(IndexModule.INDEX_STORE_TYPE_SETTING.get(settings))) {
            DEPRECATION_LOGGER.deprecate(
                "store_type_setting",
                "[simplefs] is deprecated and will be removed in 2.0. Use [niofs], which offers equal or better performance, "
                    + "or other file systems instead."
            );
        }
    }

    /**
     * Calculates the number of routing shards based on the configured value in indexSettings or if recovering from another index
     * it will return the value configured for that index.
     */
    static int getIndexNumberOfRoutingShards(Settings indexSettings, @Nullable IndexMetadata sourceMetadata) {
        final int numTargetShards = INDEX_NUMBER_OF_SHARDS_SETTING.get(indexSettings);
        final Version indexVersionCreated = IndexMetadata.SETTING_INDEX_VERSION_CREATED.get(indexSettings);
        final int routingNumShards;
        if (sourceMetadata == null || sourceMetadata.getNumberOfShards() == 1) {
            // in this case we either have no index to recover from or
            // we have a source index with 1 shard and without an explicit split factor
            // or one that is valid in that case we can split into whatever and auto-generate a new factor.
            if (IndexMetadata.INDEX_NUMBER_OF_ROUTING_SHARDS_SETTING.exists(indexSettings)) {
                routingNumShards = IndexMetadata.INDEX_NUMBER_OF_ROUTING_SHARDS_SETTING.get(indexSettings);
            } else {
                routingNumShards = calculateNumRoutingShards(numTargetShards, indexVersionCreated);
            }
        } else {
            assert IndexMetadata.INDEX_NUMBER_OF_ROUTING_SHARDS_SETTING.exists(indexSettings) == false
                : "index.number_of_routing_shards should not be present on the target index on resize";
            routingNumShards = sourceMetadata.getRoutingNumShards();
        }
        return routingNumShards;
    }

    /**
     * Validate and resolve the aliases explicitly set for the index, together with the ones inherited from the specified
     * templates.
     *
     * The template mappings are applied in the order they are encountered in the list (clients should make sure the lower index, closer
     * to the head of the list, templates have the highest {@link IndexTemplateMetadata#order()})
     *
     * @return the list of resolved aliases, with the explicitly provided aliases occurring first (having a higher priority) followed by
     * the ones inherited from the templates
     */
    public static List<AliasMetadata> resolveAndValidateAliases(
        String index,
        Set<Alias> aliases,
        List<Map<String, AliasMetadata>> templateAliases,
        Metadata metadata,
        AliasValidator aliasValidator,
        NamedXContentRegistry xContentRegistry,
        QueryShardContext queryShardContext
    ) {
        List<AliasMetadata> resolvedAliases = new ArrayList<>();
        for (Alias alias : aliases) {
            aliasValidator.validateAlias(alias, index, metadata);
            if (Strings.hasLength(alias.filter())) {
                aliasValidator.validateAliasFilter(alias.name(), alias.filter(), queryShardContext, xContentRegistry);
            }
            AliasMetadata aliasMetadata = AliasMetadata.builder(alias.name())
                .filter(alias.filter())
                .indexRouting(alias.indexRouting())
                .searchRouting(alias.searchRouting())
                .writeIndex(alias.writeIndex())
                .isHidden(alias.isHidden())
                .build();
            resolvedAliases.add(aliasMetadata);
        }

        Map<String, AliasMetadata> templatesAliases = new HashMap<>();
        for (Map<String, AliasMetadata> templateAliasConfig : templateAliases) {
            // handle aliases
            for (Map.Entry<String, AliasMetadata> entry : templateAliasConfig.entrySet()) {
                AliasMetadata aliasMetadata = entry.getValue();
                // if an alias with same name came with the create index request itself,
                // ignore this one taken from the index template
                if (aliases.contains(new Alias(aliasMetadata.alias()))) {
                    continue;
                }
                // if an alias with same name was already processed, ignore this one
                if (templatesAliases.containsKey(entry.getKey())) {
                    continue;
                }

                // Allow templatesAliases to be templated by replacing a token with the
                // name of the index that we are applying it to
                if (aliasMetadata.alias().contains("{index}")) {
                    String templatedAlias = aliasMetadata.alias().replace("{index}", index);
                    aliasMetadata = AliasMetadata.newAliasMetadata(aliasMetadata, templatedAlias);
                }

                aliasValidator.validateAliasMetadata(aliasMetadata, index, metadata);
                if (aliasMetadata.filter() != null) {
                    aliasValidator.validateAliasFilter(
                        aliasMetadata.alias(),
                        aliasMetadata.filter().uncompressed(),
                        queryShardContext,
                        xContentRegistry
                    );
                }
                templatesAliases.put(aliasMetadata.alias(), aliasMetadata);
                resolvedAliases.add((aliasMetadata));
            }
        }
        return resolvedAliases;
    }

    /**
     * Creates the index into the cluster state applying the provided blocks. The final cluster state will contain an updated routing
     * table based on the live nodes.
     */
    static ClusterState clusterStateCreateIndex(
        ClusterState currentState,
        Set<ClusterBlock> clusterBlocks,
        IndexMetadata indexMetadata,
        BiFunction<ClusterState, String, ClusterState> rerouteRoutingTable,
        BiConsumer<Metadata.Builder, IndexMetadata> metadataTransformer
    ) {
        Metadata.Builder builder = Metadata.builder(currentState.metadata()).put(indexMetadata, false);
        if (metadataTransformer != null) {
            metadataTransformer.accept(builder, indexMetadata);
        }
        Metadata newMetadata = builder.build();

        String indexName = indexMetadata.getIndex().getName();
        ClusterBlocks.Builder blocks = createClusterBlocksBuilder(currentState, indexName, clusterBlocks);
        blocks.updateBlocks(indexMetadata);

        ClusterState updatedState = ClusterState.builder(currentState).blocks(blocks).metadata(newMetadata).build();

        RoutingTable.Builder routingTableBuilder = RoutingTable.builder(updatedState.routingTable())
            .addAsNew(updatedState.metadata().index(indexName));
        updatedState = ClusterState.builder(updatedState).routingTable(routingTableBuilder.build()).build();
        return rerouteRoutingTable.apply(updatedState, "index [" + indexName + "] created");
    }

    static IndexMetadata buildIndexMetadata(
        String indexName,
        List<AliasMetadata> aliases,
        Supplier<DocumentMapper> documentMapperSupplier,
        Settings indexSettings,
        int routingNumShards,
        @Nullable IndexMetadata sourceMetadata,
        boolean isSystem
    ) {
        IndexMetadata.Builder indexMetadataBuilder = createIndexMetadataBuilder(indexName, sourceMetadata, indexSettings, routingNumShards);
        indexMetadataBuilder.system(isSystem);
        // now, update the mappings with the actual source
        Map<String, MappingMetadata> mappingsMetadata = new HashMap<>();
        DocumentMapper mapper = documentMapperSupplier.get();
        if (mapper != null) {
            MappingMetadata mappingMd = new MappingMetadata(mapper);
            mappingsMetadata.put(mapper.type(), mappingMd);
        }

        for (MappingMetadata mappingMd : mappingsMetadata.values()) {
            indexMetadataBuilder.putMapping(mappingMd);
        }

        // apply the aliases in reverse order as the lower index ones have higher order
        for (int i = aliases.size() - 1; i >= 0; i--) {
            indexMetadataBuilder.putAlias(aliases.get(i));
        }

        indexMetadataBuilder.state(IndexMetadata.State.OPEN);
        return indexMetadataBuilder.build();
    }

    /**
     * Creates an {@link IndexMetadata.Builder} for the provided index and sets a valid primary term for all the shards if a source
     * index meta data is provided (this represents the case where we're shrinking/splitting an index and the primary term for the newly
     * created index needs to be gte than the maximum term in the source index).
     */
    private static IndexMetadata.Builder createIndexMetadataBuilder(
        String indexName,
        @Nullable IndexMetadata sourceMetadata,
        Settings indexSettings,
        int routingNumShards
    ) {
        final IndexMetadata.Builder builder = IndexMetadata.builder(indexName);
        builder.setRoutingNumShards(routingNumShards);
        builder.settings(indexSettings);

        if (sourceMetadata != null) {
            /*
             * We need to arrange that the primary term on all the shards in the shrunken index is at least as large as
             * the maximum primary term on all the shards in the source index. This ensures that we have correct
             * document-level semantics regarding sequence numbers in the shrunken index.
             */
            final long primaryTerm = IntStream.range(0, sourceMetadata.getNumberOfShards())
                .mapToLong(sourceMetadata::primaryTerm)
                .max()
                .getAsLong();
            for (int shardId = 0; shardId < builder.numberOfShards(); shardId++) {
                builder.primaryTerm(shardId, primaryTerm);
            }
        }
        return builder;
    }

    private static ClusterBlocks.Builder createClusterBlocksBuilder(ClusterState currentState, String index, Set<ClusterBlock> blocks) {
        ClusterBlocks.Builder blocksBuilder = ClusterBlocks.builder().blocks(currentState.blocks());
        if (!blocks.isEmpty()) {
            for (ClusterBlock block : blocks) {
                blocksBuilder.addIndexBlock(index, block);
            }
        }
        return blocksBuilder;
    }

    private static void updateIndexMappingsAndBuildSortOrder(
        IndexService indexService,
        CreateIndexClusterStateUpdateRequest request,
        List<Map<String, Object>> mappings,
        @Nullable IndexMetadata sourceMetadata
    ) throws IOException {
        MapperService mapperService = indexService.mapperService();
        for (Map<String, Object> mapping : mappings) {
            if (mapping.isEmpty() == false) {
                mapperService.merge(MapperService.SINGLE_MAPPING_NAME, mapping, MergeReason.INDEX_TEMPLATE);
            }
        }

        if (sourceMetadata == null) {
            // now that the mapping is merged we can validate the index sort.
            // we cannot validate for index shrinking since the mapping is empty
            // at this point. The validation will take place later in the process
            // (when all shards are copied in a single place).
            indexService.getIndexSortSupplier().get();
        }
        if (request.dataStreamName() != null) {
            MetadataCreateDataStreamService.validateTimestampFieldMapping(mapperService);
        }
    }

    private static void validateActiveShardCount(ActiveShardCount waitForActiveShards, IndexMetadata indexMetadata) {
        if (waitForActiveShards == ActiveShardCount.DEFAULT) {
            waitForActiveShards = indexMetadata.getWaitForActiveShards();
        }
        if (waitForActiveShards.validate(indexMetadata.getNumberOfReplicas()) == false) {
            throw new IllegalArgumentException(
                "invalid wait_for_active_shards["
                    + waitForActiveShards
                    + "]: cannot be greater than number of shard copies ["
                    + (indexMetadata.getNumberOfReplicas() + 1)
                    + "]"
            );
        }
    }

    private void validate(CreateIndexClusterStateUpdateRequest request, ClusterState state) {
        validateIndexName(request.index(), state);
        validateIndexSettings(request.index(), request.settings(), forbidPrivateIndexSettings);
    }

    public void validateIndexSettings(String indexName, final Settings settings, final boolean forbidPrivateIndexSettings)
        throws IndexCreationException {
        List<String> validationErrors = getIndexSettingsValidationErrors(settings, forbidPrivateIndexSettings, indexName);

        if (validationErrors.isEmpty() == false) {
            ValidationException validationException = new ValidationException();
            validationException.addValidationErrors(validationErrors);
            throw new IndexCreationException(indexName, validationException);
        }
    }

    List<String> getIndexSettingsValidationErrors(final Settings settings, final boolean forbidPrivateIndexSettings, String indexName) {
        List<String> validationErrors = getIndexSettingsValidationErrors(settings, forbidPrivateIndexSettings, Optional.of(indexName));
        return validationErrors;
    }

    List<String> getIndexSettingsValidationErrors(
        final Settings settings,
        final boolean forbidPrivateIndexSettings,
        Optional<String> indexName
    ) {
        List<String> validationErrors = validateIndexCustomPath(settings, env.sharedDataDir());
        if (forbidPrivateIndexSettings) {
            validationErrors.addAll(validatePrivateSettingsNotExplicitlySet(settings, indexScopedSettings));
        }
        if (indexName.isEmpty() || indexName.get().charAt(0) != '.') {
            // Apply aware replica balance validation only to non system indices
            int replicaCount = settings.getAsInt(
                IndexMetadata.SETTING_NUMBER_OF_REPLICAS,
                DEFAULT_REPLICA_COUNT_SETTING.get(this.clusterService.state().metadata().settings())
            );
            AutoExpandReplicas autoExpandReplica = AutoExpandReplicas.SETTING.get(settings);
            Optional<String> error = awarenessReplicaBalance.validate(replicaCount, autoExpandReplica);
            if (error.isPresent()) {
                validationErrors.add(error.get());
            }
        }
        return validationErrors;
    }

    private static List<String> validatePrivateSettingsNotExplicitlySet(Settings settings, IndexScopedSettings indexScopedSettings) {
        List<String> validationErrors = new ArrayList<>();
        for (final String key : settings.keySet()) {
            final Setting<?> setting = indexScopedSettings.get(key);
            if (setting == null) {
                // see: https://github.com/opensearch-project/OpenSearch/issues/1019
                if (!indexScopedSettings.isPrivateSetting(key)) {
                    validationErrors.add("expected [" + key + "] to be private but it was not");
                }
            } else if (setting.isPrivateIndex()) {
                validationErrors.add("private index setting [" + key + "] can not be set explicitly");
            }
        }
        return validationErrors;
    }

    /**
     * Validates that the configured index data path (if any) is a sub-path of the configured shared data path (if any)
     *
     * @param settings the index configured settings
     * @param sharedDataPath the configured `path.shared_data` (if any)
     * @return a list containing validaton errors or an empty list if there aren't any errors
     */
    private static List<String> validateIndexCustomPath(Settings settings, @Nullable Path sharedDataPath) {
        String customPath = IndexMetadata.INDEX_DATA_PATH_SETTING.get(settings);
        List<String> validationErrors = new ArrayList<>();
        if (Strings.isEmpty(customPath) == false) {
            if (sharedDataPath == null) {
                validationErrors.add("path.shared_data must be set in order to use custom data paths");
            } else {
                Path resolvedPath = PathUtils.get(new Path[] { sharedDataPath }, customPath);
                if (resolvedPath == null) {
                    validationErrors.add("custom path [" + customPath + "] is not a sub-path of path.shared_data [" + sharedDataPath + "]");
                }
            }
        }
        return validationErrors;
    }

    /**
     * Validates the settings and mappings for shrinking an index.
     *
     * @return the list of nodes at least one instance of the source index shards are allocated
     */
    static List<String> validateShrinkIndex(ClusterState state, String sourceIndex, String targetIndexName, Settings targetIndexSettings) {
        IndexMetadata sourceMetadata = validateResize(state, sourceIndex, targetIndexName, targetIndexSettings);
        assert IndexMetadata.INDEX_NUMBER_OF_SHARDS_SETTING.exists(targetIndexSettings);
        IndexMetadata.selectShrinkShards(0, sourceMetadata, IndexMetadata.INDEX_NUMBER_OF_SHARDS_SETTING.get(targetIndexSettings));

        if (sourceMetadata.getNumberOfShards() == 1) {
            throw new IllegalArgumentException("can't shrink an index with only one shard");
        }

        // now check that index is all on one node
        final IndexRoutingTable table = state.routingTable().index(sourceIndex);
        Map<String, AtomicInteger> nodesToNumRouting = new HashMap<>();
        int numShards = sourceMetadata.getNumberOfShards();
        for (ShardRouting routing : table.shardsWithState(ShardRoutingState.STARTED)) {
            nodesToNumRouting.computeIfAbsent(routing.currentNodeId(), (s) -> new AtomicInteger(0)).incrementAndGet();
        }
        List<String> nodesToAllocateOn = new ArrayList<>();
        for (Map.Entry<String, AtomicInteger> entries : nodesToNumRouting.entrySet()) {
            int numAllocations = entries.getValue().get();
            assert numAllocations <= numShards : "wait what? " + numAllocations + " is > than num shards " + numShards;
            if (numAllocations == numShards) {
                nodesToAllocateOn.add(entries.getKey());
            }
        }
        if (nodesToAllocateOn.isEmpty()) {
            throw new IllegalStateException("index " + sourceIndex + " must have all shards allocated on the same node to shrink index");
        }
        return nodesToAllocateOn;
    }

    static void validateSplitIndex(ClusterState state, String sourceIndex, String targetIndexName, Settings targetIndexSettings) {
        IndexMetadata sourceMetadata = validateResize(state, sourceIndex, targetIndexName, targetIndexSettings);
        IndexMetadata.selectSplitShard(0, sourceMetadata, IndexMetadata.INDEX_NUMBER_OF_SHARDS_SETTING.get(targetIndexSettings));
    }

    static void validateCloneIndex(ClusterState state, String sourceIndex, String targetIndexName, Settings targetIndexSettings) {
        IndexMetadata sourceMetadata = validateResize(state, sourceIndex, targetIndexName, targetIndexSettings);
        IndexMetadata.selectCloneShard(0, sourceMetadata, IndexMetadata.INDEX_NUMBER_OF_SHARDS_SETTING.get(targetIndexSettings));
    }

    static IndexMetadata validateResize(ClusterState state, String sourceIndex, String targetIndexName, Settings targetIndexSettings) {
        if (state.metadata().hasIndex(targetIndexName)) {
            throw new ResourceAlreadyExistsException(state.metadata().index(targetIndexName).getIndex());
        }
        final IndexMetadata sourceMetadata = state.metadata().index(sourceIndex);
        if (sourceMetadata == null) {
            throw new IndexNotFoundException(sourceIndex);
        }

        IndexAbstraction source = state.metadata().getIndicesLookup().get(sourceIndex);
        assert source != null;
        if (source.getParentDataStream() != null
            && source.getParentDataStream().getWriteIndex().getIndex().equals(sourceMetadata.getIndex())) {
            throw new IllegalArgumentException(
                String.format(
                    Locale.ROOT,
                    "cannot resize the write index [%s] for data stream [%s]",
                    sourceIndex,
                    source.getParentDataStream().getName()
                )
            );
        }

        // ensure write operations on the source index is blocked
        if (state.blocks().indexBlocked(ClusterBlockLevel.WRITE, sourceIndex) == false) {
            throw new IllegalStateException(
                "index " + sourceIndex + " must block write operations to resize index. use \"index.blocks.write=true\""
            );
        }

        if (IndexMetadata.INDEX_NUMBER_OF_SHARDS_SETTING.exists(targetIndexSettings)) {
            // this method applies all necessary checks ie. if the target shards are less than the source shards
            // of if the source shards are divisible by the number of target shards
            IndexMetadata.getRoutingFactor(
                sourceMetadata.getNumberOfShards(),
                IndexMetadata.INDEX_NUMBER_OF_SHARDS_SETTING.get(targetIndexSettings)
            );
        }
        return sourceMetadata;
    }

    static void prepareResizeIndexSettings(
        final ClusterState currentState,
        final Settings.Builder indexSettingsBuilder,
        final Index resizeSourceIndex,
        final String resizeIntoName,
        final ResizeType type,
        final boolean copySettings,
        final IndexScopedSettings indexScopedSettings
    ) {
        // we use "i.r.a.initial_recovery" rather than "i.r.a.require|include" since we want the replica to allocate right away
        // once we are allocated.
        final String initialRecoveryIdFilter = IndexMetadata.INDEX_ROUTING_INITIAL_RECOVERY_GROUP_SETTING.getKey() + "_id";

        final IndexMetadata sourceMetadata = currentState.metadata().index(resizeSourceIndex.getName());
        if (type == ResizeType.SHRINK) {
            final List<String> nodesToAllocateOn = validateShrinkIndex(
                currentState,
                resizeSourceIndex.getName(),
                resizeIntoName,
                indexSettingsBuilder.build()
            );
            indexSettingsBuilder.put(initialRecoveryIdFilter, Strings.arrayToCommaDelimitedString(nodesToAllocateOn.toArray()));
        } else if (type == ResizeType.SPLIT) {
            validateSplitIndex(currentState, resizeSourceIndex.getName(), resizeIntoName, indexSettingsBuilder.build());
            indexSettingsBuilder.putNull(initialRecoveryIdFilter);
        } else if (type == ResizeType.CLONE) {
            validateCloneIndex(currentState, resizeSourceIndex.getName(), resizeIntoName, indexSettingsBuilder.build());
            indexSettingsBuilder.putNull(initialRecoveryIdFilter);
        } else {
            throw new IllegalStateException("unknown resize type is " + type);
        }

        final Settings.Builder builder = Settings.builder();
        if (copySettings) {
            // copy all settings and non-copyable settings and settings that have already been set (e.g., from the request)
            for (final String key : sourceMetadata.getSettings().keySet()) {
                final Setting<?> setting = indexScopedSettings.get(key);
                if (setting == null) {
                    assert indexScopedSettings.isPrivateSetting(key) : key;
                } else if (setting.getProperties().contains(Setting.Property.NotCopyableOnResize)) {
                    continue;
                }
                // do not override settings that have already been set (for example, from the request)
                if (indexSettingsBuilder.keys().contains(key)) {
                    continue;
                }
                builder.copy(key, sourceMetadata.getSettings());
            }
        } else {
            final Predicate<String> sourceSettingsPredicate = (s) -> (s.startsWith("index.similarity.")
                || s.startsWith("index.analysis.")
                || s.startsWith("index.sort.")
                || s.equals("index.soft_deletes.enabled")) && indexSettingsBuilder.keys().contains(s) == false;
            builder.put(sourceMetadata.getSettings().filter(sourceSettingsPredicate));
        }

        indexSettingsBuilder.put(IndexMetadata.SETTING_INDEX_VERSION_CREATED.getKey(), sourceMetadata.getCreationVersion())
            .put(IndexMetadata.SETTING_VERSION_UPGRADED, sourceMetadata.getUpgradedVersion())
            .put(builder.build())
            .put(IndexMetadata.SETTING_ROUTING_PARTITION_SIZE, sourceMetadata.getRoutingPartitionSize())
            .put(IndexMetadata.INDEX_RESIZE_SOURCE_NAME.getKey(), resizeSourceIndex.getName())
            .put(IndexMetadata.INDEX_RESIZE_SOURCE_UUID.getKey(), resizeSourceIndex.getUUID());
    }

    /**
     * Returns a default number of routing shards based on the number of shards of the index. The default number of routing shards will
     * allow any index to be split at least once and at most 10 times by a factor of two. The closer the number or shards gets to 1024
     * the less default split operations are supported
     */
    public static int calculateNumRoutingShards(int numShards, Version indexVersionCreated) {
        // only select this automatically for indices that are created on or after 7.0 this will prevent this new behaviour
        // until we have a fully upgraded cluster. Additionally it will make integratin testing easier since mixed clusters
        // will always have the behavior of the min node in the cluster.
        //
        // We use as a default number of routing shards the higher number that can be expressed
        // as {@code numShards * 2^x`} that is less than or equal to the maximum number of shards: 1024.
        int log2MaxNumShards = 10; // logBase2(1024)
        int log2NumShards = 32 - Integer.numberOfLeadingZeros(numShards - 1); // ceil(logBase2(numShards))
        int numSplits = log2MaxNumShards - log2NumShards;
        numSplits = Math.max(1, numSplits); // Ensure the index can be split at least once
        return numShards * 1 << numSplits;
    }

    public static void validateTranslogRetentionSettings(Settings indexSettings) {
        if (IndexSettings.INDEX_SOFT_DELETES_SETTING.get(indexSettings)) {
            if (IndexSettings.INDEX_TRANSLOG_RETENTION_AGE_SETTING.exists(indexSettings)
                || IndexSettings.INDEX_TRANSLOG_RETENTION_SIZE_SETTING.exists(indexSettings)) {
                DEPRECATION_LOGGER.deprecate(
                    "translog_retention",
                    "Translog retention settings "
                        + "[index.translog.retention.age] "
                        + "and [index.translog.retention.size] are deprecated and effectively ignored. "
                        + "They will be removed in a future version."
                );
            }
        }
    }
}
