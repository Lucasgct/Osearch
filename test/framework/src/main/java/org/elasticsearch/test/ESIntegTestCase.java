/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.test;

import com.carrotsearch.randomizedtesting.RandomizedContext;
import com.carrotsearch.randomizedtesting.annotations.TestGroup;
import com.carrotsearch.randomizedtesting.generators.RandomNumbers;
import com.carrotsearch.randomizedtesting.generators.RandomPicks;
import org.apache.http.HttpHost;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TotalHits;
import org.apache.lucene.util.LuceneTestCase;
import org.elasticsearch.OpenSearchException;
import org.elasticsearch.ExceptionsHelper;
import org.opensearch.action.ActionListener;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.admin.cluster.health.ClusterHealthRequest;
import org.opensearch.action.admin.cluster.health.ClusterHealthResponse;
import org.opensearch.action.admin.cluster.node.hotthreads.NodeHotThreads;
import org.opensearch.action.admin.cluster.node.info.NodeInfo;
import org.opensearch.action.admin.cluster.node.info.NodesInfoResponse;
import org.opensearch.action.admin.cluster.state.ClusterStateResponse;
import org.opensearch.action.admin.cluster.tasks.PendingClusterTasksResponse;
import org.opensearch.action.admin.indices.create.CreateIndexRequestBuilder;
import org.opensearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.opensearch.action.admin.indices.flush.FlushResponse;
import org.opensearch.action.admin.indices.forcemerge.ForceMergeResponse;
import org.opensearch.action.admin.indices.get.GetIndexResponse;
import org.opensearch.action.admin.indices.refresh.RefreshResponse;
import org.opensearch.action.admin.indices.segments.IndexSegments;
import org.opensearch.action.admin.indices.segments.IndexShardSegments;
import org.opensearch.action.admin.indices.segments.IndicesSegmentResponse;
import org.opensearch.action.admin.indices.segments.ShardSegments;
import org.opensearch.action.admin.indices.template.put.PutIndexTemplateRequestBuilder;
import org.opensearch.action.bulk.BulkRequestBuilder;
import org.opensearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.ClearScrollResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.opensearch.action.support.DefaultShardOperationFailedException;
import org.opensearch.action.support.IndicesOptions;
import org.elasticsearch.bootstrap.JavaVersion;
import org.opensearch.client.AdminClient;
import org.opensearch.client.Client;
import org.opensearch.client.ClusterAdminClient;
import org.opensearch.client.Requests;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.opensearch.client.transport.TransportClient;
import org.opensearch.cluster.ClusterModule;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.RestoreInProgress;
import org.opensearch.cluster.SnapshotDeletionsInProgress;
import org.opensearch.cluster.SnapshotsInProgress;
import org.opensearch.cluster.coordination.OpenSearchNodeCommand;
import org.opensearch.cluster.health.ClusterHealthStatus;
import org.opensearch.cluster.metadata.IndexGraveyard;
import org.opensearch.cluster.metadata.IndexMetadata;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.metadata.RepositoriesMetadata;
import org.elasticsearch.cluster.routing.IndexRoutingTable;
import org.elasticsearch.cluster.routing.IndexShardRoutingTable;
import org.elasticsearch.cluster.routing.ShardRouting;
import org.elasticsearch.cluster.routing.UnassignedInfo;
import org.elasticsearch.cluster.routing.allocation.DiskThresholdSettings;
import org.elasticsearch.cluster.routing.allocation.decider.EnableAllocationDecider;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.Priority;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.collect.Tuple;
import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.common.network.NetworkAddress;
import org.elasticsearch.common.network.NetworkModule;
import org.elasticsearch.common.regex.Regex;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Setting.Property;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.common.unit.ByteSizeUnit;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.util.concurrent.EsRejectedExecutionException;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.common.xcontent.smile.SmileXContent;
import org.elasticsearch.core.internal.io.IOUtils;
import org.elasticsearch.discovery.Discovery;
import org.elasticsearch.discovery.zen.ElectMasterService;
import org.elasticsearch.discovery.zen.ZenDiscovery;
import org.elasticsearch.env.Environment;
import org.elasticsearch.env.TestEnvironment;
import org.opensearch.http.HttpInfo;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.IndexModule;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.MergePolicyConfig;
import org.elasticsearch.index.MergeSchedulerConfig;
import org.elasticsearch.index.MockEngineFactoryPlugin;
import org.elasticsearch.index.codec.CodecService;
import org.elasticsearch.index.engine.Segment;
import org.elasticsearch.index.mapper.MockFieldFilterPlugin;
import org.elasticsearch.index.store.Store;
import org.elasticsearch.index.translog.Translog;
import org.opensearch.indices.IndicesQueryCache;
import org.opensearch.indices.IndicesRequestCache;
import org.opensearch.indices.store.IndicesStore;
import org.opensearch.ingest.IngestMetadata;
import org.opensearch.monitor.os.OsInfo;
import org.elasticsearch.node.NodeMocksPlugin;
import org.opensearch.plugins.NetworkPlugin;
import org.opensearch.plugins.Plugin;
import org.opensearch.rest.RestStatus;
import org.opensearch.rest.action.RestCancellableNodeClient;
import org.opensearch.script.MockScriptService;
import org.opensearch.script.ScriptMetadata;
import org.elasticsearch.search.MockSearchService;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchService;
import org.elasticsearch.test.client.RandomizingClient;
import org.elasticsearch.test.disruption.NetworkDisruption;
import org.elasticsearch.test.disruption.ServiceDisruptionScheme;
import org.elasticsearch.test.store.MockFSIndexStore;
import org.elasticsearch.test.transport.MockTransportService;
import org.elasticsearch.transport.TransportInterceptor;
import org.elasticsearch.transport.TransportRequest;
import org.elasticsearch.transport.TransportRequestHandler;
import org.elasticsearch.transport.TransportService;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.opensearch.client.Requests.syncedFlushRequest;
import static org.opensearch.cluster.metadata.IndexMetadata.SETTING_NUMBER_OF_REPLICAS;
import static org.opensearch.cluster.metadata.IndexMetadata.SETTING_NUMBER_OF_SHARDS;
import static org.elasticsearch.common.unit.TimeValue.timeValueMillis;
import static org.elasticsearch.common.util.CollectionUtils.eagerPartition;
import static org.elasticsearch.discovery.DiscoveryModule.DISCOVERY_SEED_PROVIDERS_SETTING;
import static org.elasticsearch.discovery.SettingsBasedSeedHostsProvider.DISCOVERY_SEED_HOSTS_SETTING;
import static org.elasticsearch.index.IndexSettings.INDEX_SOFT_DELETES_RETENTION_LEASE_PERIOD_SETTING;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.test.XContentTestUtils.convertToMap;
import static org.elasticsearch.test.XContentTestUtils.differenceBetweenMapsIgnoringArrayOrder;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertNoFailures;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertNoTimeout;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.emptyArray;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.startsWith;

/**
 * {@link ESIntegTestCase} is an abstract base class to run integration
 * tests against a JVM private OpenSearch Cluster. The test class supports 2 different
 * cluster scopes.
 * <ul>
 * <li>{@link Scope#TEST} - uses a new cluster for each individual test method.</li>
 * <li>{@link Scope#SUITE} - uses a cluster shared across all test methods in the same suite</li>
 * </ul>
 * <p>
 * The most common test scope is {@link Scope#SUITE} which shares a cluster per test suite.
 * <p>
 * If the test methods need specific node settings or change persistent and/or transient cluster settings {@link Scope#TEST}
 * should be used. To configure a scope for the test cluster the {@link ClusterScope} annotation
 * should be used, here is an example:
 * <pre>
 *
 * {@literal @}NodeScope(scope=Scope.TEST) public class SomeIT extends ESIntegTestCase {
 * public void testMethod() {}
 * }
 * </pre>
 * <p>
 * If no {@link ClusterScope} annotation is present on an integration test the default scope is {@link Scope#SUITE}
 * <p>
 * A test cluster creates a set of nodes in the background before the test starts. The number of nodes in the cluster is
 * determined at random and can change across tests. The {@link ClusterScope} allows configuring the initial number of nodes
 * that are created before the tests start.
 *  <pre>
 * {@literal @}NodeScope(scope=Scope.SUITE, numDataNodes=3)
 * public class SomeIT extends ESIntegTestCase {
 * public void testMethod() {}
 * }
 * </pre>
 * <p>
 * Note, the {@link ESIntegTestCase} uses randomized settings on a cluster and index level. For instance
 * each test might use different directory implementation for each test or will return a random client to one of the
 * nodes in the cluster for each call to {@link #client()}. Test failures might only be reproducible if the correct
 * system properties are passed to the test execution environment.
 * <p>
 * This class supports the following system properties (passed with -Dkey=value to the application)
 * <ul>
 * <li>-D{@value #TESTS_CLIENT_RATIO} - a double value in the interval [0..1] which defines the ration between node
 * and transport clients used</li>
 * <li>-D{@value #TESTS_ENABLE_MOCK_MODULES} - a boolean value to enable or disable mock modules. This is
 * useful to test the system without asserting modules that to make sure they don't hide any bugs in production.</li>
 * <li> - a random seed used to initialize the index random context.
 * </ul>
 */
@LuceneTestCase.SuppressFileSystems("ExtrasFS") // doesn't work with potential multi data path from test cluster yet
public abstract class ESIntegTestCase extends ESTestCase {

    /**
     * Property that controls whether ThirdParty Integration tests are run (not the default).
     */
    public static final String SYSPROP_THIRDPARTY = "tests.thirdparty";

    /**
     * Annotation for third-party integration tests.
     * <p>
     * These are tests the require a third-party service in order to run. They
     * may require the user to manually configure an external process (such as rabbitmq),
     * or may additionally require some external configuration (e.g. AWS credentials)
     * via the {@code tests.config} system property.
     */
    @Inherited
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @TestGroup(enabled = false, sysProperty = ESIntegTestCase.SYSPROP_THIRDPARTY)
    public @interface ThirdParty {
    }

    /** node names of the corresponding clusters will start with these prefixes */
    public static final String SUITE_CLUSTER_NODE_PREFIX = "node_s";
    public static final String TEST_CLUSTER_NODE_PREFIX = "node_t";

    /**
     * Key used to set the transport client ratio via the commandline -D{@value #TESTS_CLIENT_RATIO}
     */
    public static final String TESTS_CLIENT_RATIO = "tests.client.ratio";

    /**
     * Key used to eventually switch to using an external cluster and provide its transport addresses
     */
    public static final String TESTS_CLUSTER = "tests.cluster";

    /**
     * Key used to retrieve the index random seed from the index settings on a running node.
     * The value of this seed can be used to initialize a random context for a specific index.
     * It's set once per test via a generic index template.
     */
    public static final Setting<Long> INDEX_TEST_SEED_SETTING =
        Setting.longSetting("index.tests.seed", 0, Long.MIN_VALUE, Property.IndexScope);

    /**
     * A boolean value to enable or disable mock modules. This is useful to test the
     * system without asserting modules that to make sure they don't hide any bugs in
     * production.
     *
     * @see ESIntegTestCase
     */
    public static final String TESTS_ENABLE_MOCK_MODULES = "tests.enable_mock_modules";

    private static final boolean MOCK_MODULES_ENABLED = "true".equals(System.getProperty(TESTS_ENABLE_MOCK_MODULES, "true"));
    /**
     * Threshold at which indexing switches from frequently async to frequently bulk.
     */
    private static final int FREQUENT_BULK_THRESHOLD = 300;

    /**
     * Threshold at which bulk indexing will always be used.
     */
    private static final int ALWAYS_BULK_THRESHOLD = 3000;

    /**
     * Maximum number of async operations that indexRandom will kick off at one time.
     */
    private static final int MAX_IN_FLIGHT_ASYNC_INDEXES = 150;

    /**
     * Maximum number of documents in a single bulk index request.
     */
    private static final int MAX_BULK_INDEX_REQUEST_SIZE = 1000;

    /**
     * Default minimum number of shards for an index
     */
    protected static final int DEFAULT_MIN_NUM_SHARDS = 1;

    /**
     * Default maximum number of shards for an index
     */
    protected static final int DEFAULT_MAX_NUM_SHARDS = 10;

    /**
     * The current cluster depending on the configured {@link Scope}.
     * By default if no {@link ClusterScope} is configured this will hold a reference to the suite cluster.
     */
    private static TestCluster currentCluster;
    private static RestClient restClient = null;

    private static final double TRANSPORT_CLIENT_RATIO = transportClientRatio();

    private static final Map<Class<?>, TestCluster> clusters = new IdentityHashMap<>();

    private static ESIntegTestCase INSTANCE = null; // see @SuiteScope
    private static Long SUITE_SEED = null;

    @BeforeClass
    public static void beforeClass() throws Exception {
        SUITE_SEED = randomLong();
        initializeSuiteScope();
    }

    @Override
    protected final boolean enableWarningsCheck() {
        //In an integ test it doesn't make sense to keep track of warnings: if the cluster is external the warnings are in another jvm,
        //if the cluster is internal the deprecation logger is shared across all nodes
        return false;
    }

    protected final void beforeInternal() throws Exception {
        final Scope currentClusterScope = getCurrentClusterScope();
        Callable<Void> setup = () -> {
            cluster().beforeTest(random(), getPerTestTransportClientRatio());
            cluster().wipe(excludeTemplates());
            randomIndexTemplate();
            return null;
        };
        switch (currentClusterScope) {
            case SUITE:
                assert SUITE_SEED != null : "Suite seed was not initialized";
                currentCluster = buildAndPutCluster(currentClusterScope, SUITE_SEED);
                RandomizedContext.current().runWithPrivateRandomness(SUITE_SEED, setup);
                break;
            case TEST:
                currentCluster = buildAndPutCluster(currentClusterScope, randomLong());
                setup.call();
                break;
        }

    }

    private void printTestMessage(String message) {
        if (isSuiteScopedTest(getClass()) && (getTestName().equals("<unknown>"))) {
            logger.info("[{}]: {} suite", getTestClass().getSimpleName(), message);
        } else {
            logger.info("[{}#{}]: {} test", getTestClass().getSimpleName(), getTestName(), message);
        }
    }

    /**
     * Creates a randomized index template. This template is used to pass in randomized settings on a
     * per index basis. Allows to enable/disable the randomization for number of shards and replicas
     */
    private void randomIndexTemplate() {

        // TODO move settings for random directory etc here into the index based randomized settings.
        if (cluster().size() > 0) {
            Settings.Builder randomSettingsBuilder =
                setRandomIndexSettings(random(), Settings.builder());
            if (isInternalCluster()) {
                // this is only used by mock plugins and if the cluster is not internal we just can't set it
                randomSettingsBuilder.put(INDEX_TEST_SEED_SETTING.getKey(), random().nextLong());
            }

            randomSettingsBuilder.put(SETTING_NUMBER_OF_SHARDS, numberOfShards())
                .put(SETTING_NUMBER_OF_REPLICAS, numberOfReplicas());

            // if the test class is annotated with SuppressCodecs("*"), it means don't use lucene's codec randomization
            // otherwise, use it, it has assertions and so on that can find bugs.
            SuppressCodecs annotation = getClass().getAnnotation(SuppressCodecs.class);
            if (annotation != null && annotation.value().length == 1 && "*".equals(annotation.value()[0])) {
                randomSettingsBuilder.put("index.codec", randomFrom(CodecService.DEFAULT_CODEC, CodecService.BEST_COMPRESSION_CODEC));
            } else {
                randomSettingsBuilder.put("index.codec", CodecService.LUCENE_DEFAULT_CODEC);
            }

            for (String setting : randomSettingsBuilder.keys()) {
                assertThat("non index. prefix setting set on index template, its a node setting...", setting, startsWith("index."));
            }
            // always default delayed allocation to 0 to make sure we have tests are not delayed
            randomSettingsBuilder.put(UnassignedInfo.INDEX_DELAYED_NODE_LEFT_TIMEOUT_SETTING.getKey(), 0);
            if (randomBoolean()) {
                randomSettingsBuilder.put(IndexModule.INDEX_QUERY_CACHE_ENABLED_SETTING.getKey(), randomBoolean());
            }
            PutIndexTemplateRequestBuilder putTemplate = client().admin().indices()
                .preparePutTemplate("random_index_template")
                .setPatterns(Collections.singletonList("*"))
                .setOrder(0)
                .setSettings(randomSettingsBuilder);
            assertAcked(putTemplate.execute().actionGet());
        }
    }

    protected Settings.Builder setRandomIndexSettings(Random random, Settings.Builder builder) {
        setRandomIndexMergeSettings(random, builder);
        setRandomIndexTranslogSettings(random, builder);

        if (random.nextBoolean()) {
            builder.put(MergeSchedulerConfig.AUTO_THROTTLE_SETTING.getKey(), false);
        }

        if (random.nextBoolean()) {
            builder.put(IndicesRequestCache.INDEX_CACHE_REQUEST_ENABLED_SETTING.getKey(), random.nextBoolean());
        }

        if (random.nextBoolean()) {
            builder.put(IndexSettings.INDEX_CHECK_ON_STARTUP.getKey(), randomFrom(random, "false", "checksum", "true"));
        }

        if (random.nextBoolean()) {
            // keep this low so we don't stall tests
            builder.put(UnassignedInfo.INDEX_DELAYED_NODE_LEFT_TIMEOUT_SETTING.getKey(),
                RandomNumbers.randomIntBetween(random, 1, 15) + "ms");
        }

        if (random.nextBoolean()) {
            builder.put(Store.FORCE_RAM_TERM_DICT.getKey(), true);
        }

        return builder;
    }

    private static Settings.Builder setRandomIndexMergeSettings(Random random, Settings.Builder builder) {
        if (random.nextBoolean()) {
            builder.put(MergePolicyConfig.INDEX_COMPOUND_FORMAT_SETTING.getKey(),
                (random.nextBoolean() ? random.nextDouble() : random.nextBoolean()).toString());
        }
        switch (random.nextInt(4)) {
            case 3:
                final int maxThreadCount = RandomNumbers.randomIntBetween(random, 1, 4);
                final int maxMergeCount = RandomNumbers.randomIntBetween(random, maxThreadCount, maxThreadCount + 4);
                builder.put(MergeSchedulerConfig.MAX_MERGE_COUNT_SETTING.getKey(), maxMergeCount);
                builder.put(MergeSchedulerConfig.MAX_THREAD_COUNT_SETTING.getKey(), maxThreadCount);
                break;
        }

        return builder;
    }

    private static Settings.Builder setRandomIndexTranslogSettings(Random random, Settings.Builder builder) {
        if (random.nextBoolean()) {
            builder.put(IndexSettings.INDEX_TRANSLOG_FLUSH_THRESHOLD_SIZE_SETTING.getKey(),
                new ByteSizeValue(RandomNumbers.randomIntBetween(random, 1, 300), ByteSizeUnit.MB));
        }
        if (random.nextBoolean()) {
            builder.put(IndexSettings.INDEX_TRANSLOG_FLUSH_THRESHOLD_SIZE_SETTING.getKey(),
                new ByteSizeValue(1, ByteSizeUnit.PB)); // just don't flush
        }
        if (random.nextBoolean()) {
            builder.put(IndexSettings.INDEX_TRANSLOG_DURABILITY_SETTING.getKey(),
                RandomPicks.randomFrom(random, Translog.Durability.values()));
        }

        if (random.nextBoolean()) {
            builder.put(IndexSettings.INDEX_TRANSLOG_SYNC_INTERVAL_SETTING.getKey(),
                RandomNumbers.randomIntBetween(random, 100, 5000), TimeUnit.MILLISECONDS);
        }

        return builder;
    }

    private TestCluster buildWithPrivateContext(final Scope scope, final long seed) throws Exception {
        return RandomizedContext.current().runWithPrivateRandomness(seed, () -> buildTestCluster(scope, seed));
    }

    private TestCluster buildAndPutCluster(Scope currentClusterScope, long seed) throws Exception {
        final Class<?> clazz = this.getClass();
        TestCluster testCluster = clusters.remove(clazz); // remove this cluster first
        clearClusters(); // all leftovers are gone by now... this is really just a double safety if we miss something somewhere
        switch (currentClusterScope) {
            case SUITE:
                if (testCluster == null) { // only build if it's not there yet
                    testCluster = buildWithPrivateContext(currentClusterScope, seed);
                }
                break;
            case TEST:
                // close the previous one and create a new one
                IOUtils.closeWhileHandlingException(testCluster);
                testCluster = buildTestCluster(currentClusterScope, seed);
                break;
        }
        clusters.put(clazz, testCluster);
        return testCluster;
    }

    private static void clearClusters() throws Exception {
        if (!clusters.isEmpty()) {
            IOUtils.close(clusters.values());
            clusters.clear();
        }
        if (restClient != null) {
            restClient.close();
            restClient = null;
        }
        assertBusy(() -> {
            int numChannels = RestCancellableNodeClient.getNumChannels();
            assertEquals( numChannels+ " channels still being tracked in " + RestCancellableNodeClient.class.getSimpleName()
                + " while there should be none", 0, numChannels);
        });
    }

    private void afterInternal(boolean afterClass) throws Exception {
        boolean success = false;
        try {
            final Scope currentClusterScope = getCurrentClusterScope();
            if (isInternalCluster()) {
                internalCluster().clearDisruptionScheme();
            }
            try {
                if (cluster() != null) {
                    if (currentClusterScope != Scope.TEST) {
                        Metadata metadata = client().admin().cluster().prepareState().execute().actionGet().getState().getMetadata();

                        final Set<String> persistentKeys = new HashSet<>(metadata.persistentSettings().keySet());
                        assertThat("test leaves persistent cluster metadata behind", persistentKeys, empty());

                        final Set<String> transientKeys = new HashSet<>(metadata.transientSettings().keySet());
                        if (isInternalCluster() && internalCluster().getAutoManageMinMasterNode()) {
                            // this is set by the test infra
                            transientKeys.remove(ElectMasterService.DISCOVERY_ZEN_MINIMUM_MASTER_NODES_SETTING.getKey());
                        }
                        assertThat("test leaves transient cluster metadata behind", transientKeys, empty());
                    }
                    ensureClusterSizeConsistency();
                    ensureClusterStateConsistency();
                    ensureClusterStateCanBeReadByNodeTool();
                    if (isInternalCluster()) {
                        // check no pending cluster states are leaked
                        for (Discovery discovery : internalCluster().getInstances(Discovery.class)) {
                            if (discovery instanceof ZenDiscovery) {
                                final ZenDiscovery zenDiscovery = (ZenDiscovery) discovery;
                                assertBusy(() -> {
                                    final ClusterState[] states = zenDiscovery.pendingClusterStates();
                                    assertThat(zenDiscovery.clusterState().nodes().getLocalNode().getName() +
                                            " still having pending states:\n" +
                                            Stream.of(states).map(ClusterState::toString).collect(Collectors.joining("\n")),
                                        states, emptyArray());
                                });
                            }
                        }
                    }
                    beforeIndexDeletion();
                    cluster().wipe(excludeTemplates()); // wipe after to make sure we fail in the test that didn't ack the delete
                    if (afterClass || currentClusterScope == Scope.TEST) {
                        cluster().close();
                    }
                    cluster().assertAfterTest();
                }
            } finally {
                if (currentClusterScope == Scope.TEST) {
                    clearClusters(); // it is ok to leave persistent / transient cluster state behind if scope is TEST
                }
            }
            success = true;
        } finally {
            if (!success) {
                // if we failed here that means that something broke horribly so we should clear all clusters
                // TODO: just let the exception happen, WTF is all this horseshit
                // afterTestRule.forceFailure();
            }
        }
    }

    /**
     * @return An exclude set of index templates that will not be removed in between tests.
     */
    protected Set<String> excludeTemplates() {
        return Collections.emptySet();
    }

    protected void beforeIndexDeletion() throws Exception {
        cluster().beforeIndexDeletion();
    }

    public static TestCluster cluster() {
        return currentCluster;
    }

    public static boolean isInternalCluster() {
        return (currentCluster instanceof InternalTestCluster);
    }

    public static InternalTestCluster internalCluster() {
        if (!isInternalCluster()) {
            throw new UnsupportedOperationException("current test cluster is immutable");
        }
        return (InternalTestCluster) currentCluster;
    }

    public ClusterService clusterService() {
        return internalCluster().clusterService();
    }

    public static Client client() {
        return client(null);
    }

    public static Client client(@Nullable String node) {
        if (node != null) {
            return internalCluster().client(node);
        }
        Client client = cluster().client();
        if (frequently()) {
            client = new RandomizingClient(client, random());
        }
        return client;
    }

    public static Client dataNodeClient() {
        Client client = internalCluster().dataNodeClient();
        if (frequently()) {
            client = new RandomizingClient(client, random());
        }
        return client;
    }

    public static Iterable<Client> clients() {
        return cluster().getClients();
    }

    protected int minimumNumberOfShards() {
        return DEFAULT_MIN_NUM_SHARDS;
    }

    protected int maximumNumberOfShards() {
        return DEFAULT_MAX_NUM_SHARDS;
    }

    protected int numberOfShards() {
        return between(minimumNumberOfShards(), maximumNumberOfShards());
    }

    protected int minimumNumberOfReplicas() {
        return 0;
    }

    protected int maximumNumberOfReplicas() {
        //use either 0 or 1 replica, yet a higher amount when possible, but only rarely
        int maxNumReplicas = Math.max(0, cluster().numDataNodes() - 1);
        return frequently() ? Math.min(1, maxNumReplicas) : maxNumReplicas;
    }

    protected int numberOfReplicas() {
        return between(minimumNumberOfReplicas(), maximumNumberOfReplicas());
    }


    public void setDisruptionScheme(ServiceDisruptionScheme scheme) {
        internalCluster().setDisruptionScheme(scheme);
    }

    /**
     * Creates a disruption that isolates the current master node from all other nodes in the cluster.
     *
     * @param disruptionType type of disruption to create
     * @return disruption
     */
    protected static NetworkDisruption isolateMasterDisruption(NetworkDisruption.NetworkLinkDisruptionType disruptionType) {
        final String masterNode = internalCluster().getMasterName();
        return new NetworkDisruption(
            new NetworkDisruption.TwoPartitions(
                Collections.singleton(masterNode),
                Arrays.stream(internalCluster().getNodeNames()).filter(name -> name.equals(masterNode) == false)
                    .collect(Collectors.toSet())), disruptionType);
    }

    /**
     * Returns a settings object used in {@link #createIndex(String...)} and {@link #prepareCreate(String)} and friends.
     * This method can be overwritten by subclasses to set defaults for the indices that are created by the test.
     * By default it returns a settings object that sets a random number of shards. Number of shards and replicas
     * can be controlled through specific methods.
     */
    public Settings indexSettings() {
        Settings.Builder builder = Settings.builder();
        int numberOfShards = numberOfShards();
        if (numberOfShards > 0) {
            builder.put(SETTING_NUMBER_OF_SHARDS, numberOfShards).build();
        }
        int numberOfReplicas = numberOfReplicas();
        if (numberOfReplicas >= 0) {
            builder.put(SETTING_NUMBER_OF_REPLICAS, numberOfReplicas).build();
        }
        // 30% of the time
        if (randomInt(9) < 3) {
            final String dataPath = randomAlphaOfLength(10);
            logger.info("using custom data_path for index: [{}]", dataPath);
            builder.put(IndexMetadata.SETTING_DATA_PATH, dataPath);
        }
        // always default delayed allocation to 0 to make sure we have tests are not delayed
        builder.put(UnassignedInfo.INDEX_DELAYED_NODE_LEFT_TIMEOUT_SETTING.getKey(), 0);
        builder.put(IndexSettings.INDEX_SOFT_DELETES_SETTING.getKey(), randomBoolean());
        if (randomBoolean()) {
            builder.put(IndexSettings.INDEX_SOFT_DELETES_RETENTION_OPERATIONS_SETTING.getKey(), between(0, 1000));
        }
        if (randomBoolean()) {
            builder.put(INDEX_SOFT_DELETES_RETENTION_LEASE_PERIOD_SETTING.getKey(), timeValueMillis(randomLongBetween(0, randomBoolean()
                ? 1000 : INDEX_SOFT_DELETES_RETENTION_LEASE_PERIOD_SETTING.get(Settings.EMPTY).millis())).getStringRep());
        }
        return builder.build();
    }

    /**
     * Creates one or more indices and asserts that the indices are acknowledged. If one of the indices
     * already exists this method will fail and wipe all the indices created so far.
     */
    public final void createIndex(String... names) {

        List<String> created = new ArrayList<>();
        for (String name : names) {
            boolean success = false;
            try {
                assertAcked(prepareCreate(name));
                created.add(name);
                success = true;
            } finally {
                if (!success && !created.isEmpty()) {
                    cluster().wipeIndices(created.toArray(new String[created.size()]));
                }
            }
        }
    }

    /**
     * creates an index with the given setting
     */
    public final void createIndex(String name, Settings indexSettings) {
        assertAcked(prepareCreate(name).setSettings(indexSettings));
    }

    /**
     * Creates a new {@link CreateIndexRequestBuilder} with the settings obtained from {@link #indexSettings()}.
     */
    public final CreateIndexRequestBuilder prepareCreate(String index) {
        return prepareCreate(index, -1);
    }

    /**
     * Creates a new {@link CreateIndexRequestBuilder} with the settings obtained from {@link #indexSettings()}.
     * The index that is created with this builder will only be allowed to allocate on the number of nodes passed to this
     * method.
     * <p>
     * This method uses allocation deciders to filter out certain nodes to allocate the created index on. It defines allocation
     * rules based on <code>index.routing.allocation.exclude._name</code>.
     * </p>
     */
    public final CreateIndexRequestBuilder prepareCreate(String index, int numNodes) {
        return prepareCreate(index, numNodes, Settings.builder());
    }

    /**
     * Creates a new {@link CreateIndexRequestBuilder} with the settings obtained from {@link #indexSettings()}, augmented
     * by the given builder
     */
    public CreateIndexRequestBuilder prepareCreate(String index, Settings.Builder settingsBuilder) {
        return prepareCreate(index, -1, settingsBuilder);
    }
    /**
     * Creates a new {@link CreateIndexRequestBuilder} with the settings obtained from {@link #indexSettings()}.
     * The index that is created with this builder will only be allowed to allocate on the number of nodes passed to this
     * method.
     * <p>
     * This method uses allocation deciders to filter out certain nodes to allocate the created index on. It defines allocation
     * rules based on <code>index.routing.allocation.exclude._name</code>.
     * </p>
     */
    public CreateIndexRequestBuilder prepareCreate(String index, int numNodes, Settings.Builder settingsBuilder) {
        Settings.Builder builder = Settings.builder().put(indexSettings()).put(settingsBuilder.build());

        if (numNodes > 0) {
            internalCluster().ensureAtLeastNumDataNodes(numNodes);
            getExcludeSettings(numNodes, builder);
        }
        return client().admin().indices().prepareCreate(index).setSettings(builder.build());
    }

    private Settings.Builder getExcludeSettings(int num, Settings.Builder builder) {
        String exclude = String.join(",", internalCluster().allDataNodesButN(num));
        builder.put("index.routing.allocation.exclude._name", exclude);
        return builder;
    }

    /**
     * Waits until all nodes have no pending tasks.
     */
    public void waitNoPendingTasksOnAll() throws Exception {
        assertNoTimeout(client().admin().cluster().prepareHealth().setWaitForEvents(Priority.LANGUID).get());
        assertBusy(() -> {
            for (Client client : clients()) {
                ClusterHealthResponse clusterHealth = client.admin().cluster().prepareHealth().setLocal(true).get();
                assertThat("client " + client + " still has in flight fetch", clusterHealth.getNumberOfInFlightFetch(), equalTo(0));
                PendingClusterTasksResponse pendingTasks = client.admin().cluster().preparePendingClusterTasks().setLocal(true).get();
                assertThat("client " + client + " still has pending tasks " + pendingTasks, pendingTasks, Matchers.emptyIterable());
                clusterHealth = client.admin().cluster().prepareHealth().setLocal(true).get();
                assertThat("client " + client + " still has in flight fetch", clusterHealth.getNumberOfInFlightFetch(), equalTo(0));
            }
        });
        assertNoTimeout(client().admin().cluster().prepareHealth().setWaitForEvents(Priority.LANGUID).get());
    }

    /** Ensures the result counts are as expected, and logs the results if different */
    public void assertResultsAndLogOnFailure(long expectedResults, SearchResponse searchResponse) {
        final TotalHits totalHits = searchResponse.getHits().getTotalHits();
        if (totalHits.value != expectedResults || totalHits.relation != TotalHits.Relation.EQUAL_TO) {
            StringBuilder sb = new StringBuilder("search result contains [");
            String value = Long.toString(totalHits.value) +
                (totalHits.relation == TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO ? "+" : "");
            sb.append(value).append("] results. expected [").append(expectedResults).append("]");
            String failMsg = sb.toString();
            for (SearchHit hit : searchResponse.getHits().getHits()) {
                sb.append("\n-> _index: [").append(hit.getIndex()).append("] type [").append(hit.getType())
                    .append("] id [").append(hit.getId()).append("]");
            }
            logger.warn("{}", sb);
            fail(failMsg);
        }
    }

    /**
     * Restricts the given index to be allocated on <code>n</code> nodes using the allocation deciders.
     * Yet if the shards can't be allocated on any other node shards for this index will remain allocated on
     * more than <code>n</code> nodes.
     */
    public void allowNodes(String index, int n) {
        assert index != null;
        internalCluster().ensureAtLeastNumDataNodes(n);
        Settings.Builder builder = Settings.builder();
        if (n > 0) {
            getExcludeSettings(n, builder);
        }
        Settings build = builder.build();
        if (!build.isEmpty()) {
            logger.debug("allowNodes: updating [{}]'s setting to [{}]", index, build.toDelimitedString(';'));
            client().admin().indices().prepareUpdateSettings(index).setSettings(build).execute().actionGet();
        }
    }

    /**
     * Ensures the cluster has a green state via the cluster health API. This method will also wait for relocations.
     * It is useful to ensure that all action on the cluster have finished and all shards that were currently relocating
     * are now allocated and started.
     */
    public ClusterHealthStatus ensureGreen(String... indices) {
        return ensureGreen(TimeValue.timeValueSeconds(30), indices);
    }

    /**
     * Ensures the cluster has a green state via the cluster health API. This method will also wait for relocations.
     * It is useful to ensure that all action on the cluster have finished and all shards that were currently relocating
     * are now allocated and started.
     *
     * @param timeout time out value to set on {@link ClusterHealthRequest}
     */
    public ClusterHealthStatus ensureGreen(TimeValue timeout, String... indices) {
        return ensureColor(ClusterHealthStatus.GREEN, timeout, false, indices);
    }

    /**
     * Ensures the cluster has a yellow state via the cluster health API.
     */
    public ClusterHealthStatus ensureYellow(String... indices) {
        return ensureColor(ClusterHealthStatus.YELLOW, TimeValue.timeValueSeconds(30), false, indices);
    }

    /**
     * Ensures the cluster has a yellow state via the cluster health API and ensures the that cluster has no initializing shards
     * for the given indices
     */
    public ClusterHealthStatus ensureYellowAndNoInitializingShards(String... indices) {
        return ensureColor(ClusterHealthStatus.YELLOW, TimeValue.timeValueSeconds(30), true, indices);
    }

    private ClusterHealthStatus ensureColor(ClusterHealthStatus clusterHealthStatus, TimeValue timeout, boolean waitForNoInitializingShards,
                                            String... indices) {
        String color = clusterHealthStatus.name().toLowerCase(Locale.ROOT);
        String method = "ensure" + Strings.capitalize(color);

        ClusterHealthRequest healthRequest = Requests.clusterHealthRequest(indices)
            .timeout(timeout)
            .waitForStatus(clusterHealthStatus)
            .waitForEvents(Priority.LANGUID)
            .waitForNoRelocatingShards(true)
            .waitForNoInitializingShards(waitForNoInitializingShards)
            // We currently often use ensureGreen or ensureYellow to check whether the cluster is back in a good state after shutting down
            // a node. If the node that is stopped is the master node, another node will become master and publish a cluster state where it
            // is master but where the node that was stopped hasn't been removed yet from the cluster state. It will only subsequently
            // publish a second state where the old master is removed. If the ensureGreen/ensureYellow is timed just right, it will get to
            // execute before the second cluster state update removes the old master and the condition ensureGreen / ensureYellow will
            // trivially hold if it held before the node was shut down. The following "waitForNodes" condition ensures that the node has
            // been removed by the master so that the health check applies to the set of nodes we expect to be part of the cluster.
            .waitForNodes(Integer.toString(cluster().size()));

        ClusterHealthResponse actionGet = client().admin().cluster().health(healthRequest).actionGet();
        if (actionGet.isTimedOut()) {
            final String hotThreads = client().admin().cluster().prepareNodesHotThreads().setThreads(99999).setIgnoreIdleThreads(false)
                .get().getNodes().stream().map(NodeHotThreads::getHotThreads).collect(Collectors.joining("\n"));
            logger.info("{} timed out, cluster state:\n{}\npending tasks:\n{}\nhot threads:\n{}\n",
                method,
                client().admin().cluster().prepareState().get().getState(),
                client().admin().cluster().preparePendingClusterTasks().get(),
                hotThreads);
            fail("timed out waiting for " + color + " state");
        }
        assertThat("Expected at least " + clusterHealthStatus + " but got " + actionGet.getStatus(),
            actionGet.getStatus().value(), lessThanOrEqualTo(clusterHealthStatus.value()));
        logger.debug("indices {} are {}", indices.length == 0 ? "[_all]" : indices, color);
        return actionGet.getStatus();
    }

    /**
     * Waits for all relocating shards to become active using the cluster health API.
     */
    public ClusterHealthStatus waitForRelocation() {
        return waitForRelocation(null);
    }

    /**
     * Waits for all relocating shards to become active and the cluster has reached the given health status
     * using the cluster health API.
     */
    public ClusterHealthStatus waitForRelocation(ClusterHealthStatus status) {
        ClusterHealthRequest request = Requests.clusterHealthRequest().waitForNoRelocatingShards(true).waitForEvents(Priority.LANGUID);
        if (status != null) {
            request.waitForStatus(status);
        }
        ClusterHealthResponse actionGet = client().admin().cluster()
            .health(request).actionGet();
        if (actionGet.isTimedOut()) {
            logger.info("waitForRelocation timed out (status={}), cluster state:\n{}\n{}", status,
                client().admin().cluster().prepareState().get().getState(), client().admin().cluster().preparePendingClusterTasks().get());
            assertThat("timed out waiting for relocation", actionGet.isTimedOut(), equalTo(false));
        }
        if (status != null) {
            assertThat(actionGet.getStatus(), equalTo(status));
        }
        return actionGet.getStatus();
    }

    /**
     * Waits until at least a give number of document is visible for searchers
     *
     * @param numDocs number of documents to wait for
     * @param indexer a {@link org.elasticsearch.test.BackgroundIndexer}. It will be first checked for documents indexed.
     *                This saves on unneeded searches.
     */
    public void waitForDocs(final long numDocs, final BackgroundIndexer indexer) throws Exception {
        // indexing threads can wait for up to ~1m before retrying when they first try to index into a shard which is not STARTED.
        final long maxWaitTimeMs = Math.max(90 * 1000, 200 * numDocs);

        assertBusy(
            () -> {
                long lastKnownCount = indexer.totalIndexedDocs();

                if (lastKnownCount >= numDocs) {
                    try {
                        long count = client().prepareSearch()
                            .setTrackTotalHits(true)
                            .setSize(0)
                            .setQuery(matchAllQuery())
                            .get()
                            .getHits().getTotalHits().value;

                        if (count == lastKnownCount) {
                            // no progress - try to refresh for the next time
                            client().admin().indices().prepareRefresh().get();
                        }
                        lastKnownCount = count;
                    } catch (Exception e) { // count now acts like search and barfs if all shards failed...
                        logger.debug("failed to executed count", e);
                        throw e;
                    }
                }

                if (logger.isDebugEnabled()) {
                    if (lastKnownCount < numDocs) {
                        logger.debug("[{}] docs indexed. waiting for [{}]", lastKnownCount, numDocs);
                    } else {
                        logger.debug("[{}] docs visible for search (needed [{}])", lastKnownCount, numDocs);
                    }
                }

                assertThat(lastKnownCount, greaterThanOrEqualTo(numDocs));
            },
            maxWaitTimeMs,
            TimeUnit.MILLISECONDS
        );
    }

    /**
     * Prints the current cluster state as debug logging.
     */
    public void logClusterState() {
        logger.debug("cluster state:\n{}\n{}",
            client().admin().cluster().prepareState().get().getState(), client().admin().cluster().preparePendingClusterTasks().get());
    }

    protected void ensureClusterSizeConsistency() {
        if (cluster() != null && cluster().size() > 0) { // if static init fails the cluster can be null
            logger.trace("Check consistency for [{}] nodes", cluster().size());
            assertNoTimeout(client().admin().cluster().prepareHealth().setWaitForNodes(Integer.toString(cluster().size())).get());
        }
    }

    /**
     * Verifies that all nodes that have the same version of the cluster state as master have same cluster state
     */
    protected void ensureClusterStateConsistency() throws IOException {
        if (cluster() != null && cluster().size() > 0) {
            final NamedWriteableRegistry namedWriteableRegistry = cluster().getNamedWriteableRegistry();
            final Client masterClient = client();
            ClusterState masterClusterState = masterClient.admin().cluster().prepareState().all().get().getState();
            byte[] masterClusterStateBytes = ClusterState.Builder.toBytes(masterClusterState);
            // remove local node reference
            masterClusterState = ClusterState.Builder.fromBytes(masterClusterStateBytes, null, namedWriteableRegistry);
            Map<String, Object> masterStateMap = convertToMap(masterClusterState);
            int masterClusterStateSize = ClusterState.Builder.toBytes(masterClusterState).length;
            String masterId = masterClusterState.nodes().getMasterNodeId();
            for (Client client : cluster().getClients()) {
                ClusterState localClusterState = client.admin().cluster().prepareState().all().setLocal(true).get().getState();
                byte[] localClusterStateBytes = ClusterState.Builder.toBytes(localClusterState);
                // remove local node reference
                localClusterState = ClusterState.Builder.fromBytes(localClusterStateBytes, null, namedWriteableRegistry);
                final Map<String, Object> localStateMap = convertToMap(localClusterState);
                final int localClusterStateSize = ClusterState.Builder.toBytes(localClusterState).length;
                // Check that the non-master node has the same version of the cluster state as the master and
                // that the master node matches the master (otherwise there is no requirement for the cluster state to match)
                if (masterClusterState.version() == localClusterState.version()
                    && masterId.equals(localClusterState.nodes().getMasterNodeId())) {
                    try {
                        assertEquals("cluster state UUID does not match", masterClusterState.stateUUID(), localClusterState.stateUUID());
                        /*
                         * The cluster state received by the transport client can miss customs that the client does not understand. This
                         * means that we only expect equality in the cluster state including customs if the master client and the local
                         * client are of the same type (both or neither are transport clients). Otherwise, we can only assert equality
                         * modulo non-core customs.
                         */
                        if (isTransportClient(masterClient) == isTransportClient(client)) {
                            // We cannot compare serialization bytes since serialization order of maps is not guaranteed
                            // but we can compare serialization sizes - they should be the same
                            assertEquals("cluster state size does not match", masterClusterStateSize, localClusterStateSize);
                            // Compare JSON serialization
                            assertNull(
                                "cluster state JSON serialization does not match",
                                differenceBetweenMapsIgnoringArrayOrder(masterStateMap, localStateMap));
                        } else {
                            // remove non-core customs and compare the cluster states
                            assertNull(
                                "cluster state JSON serialization does not match (after removing some customs)",
                                differenceBetweenMapsIgnoringArrayOrder(
                                    convertToMap(removePluginCustoms(masterClusterState)),
                                    convertToMap(removePluginCustoms(localClusterState))));
                        }
                    } catch (final AssertionError error) {
                        logger.error(
                            "Cluster state from master:\n{}\nLocal cluster state:\n{}",
                            masterClusterState.toString(),
                            localClusterState.toString());
                        throw error;
                    }
                }
            }
        }

    }

    protected void ensureClusterStateCanBeReadByNodeTool() throws IOException {
        if (cluster() != null && cluster().size() > 0) {
            final Client masterClient = client();
            Metadata metadata = masterClient.admin().cluster().prepareState().all().get().getState().metadata();
            final Map<String, String> serializationParams = new HashMap<>(2);
            serializationParams.put("binary", "true");
            serializationParams.put(Metadata.CONTEXT_MODE_PARAM, Metadata.CONTEXT_MODE_GATEWAY);
            final ToXContent.Params serializationFormatParams = new ToXContent.MapParams(serializationParams);

            // when comparing XContent output, do not use binary format
            final Map<String, String> compareParams = new HashMap<>(2);
            compareParams.put(Metadata.CONTEXT_MODE_PARAM, Metadata.CONTEXT_MODE_GATEWAY);
            final ToXContent.Params compareFormatParams = new ToXContent.MapParams(compareParams);

            {
                Metadata metadataWithoutIndices = Metadata.builder(metadata).removeAllIndices().build();

                XContentBuilder builder = SmileXContent.contentBuilder();
                builder.startObject();
                metadataWithoutIndices.toXContent(builder, serializationFormatParams);
                builder.endObject();
                final BytesReference originalBytes = BytesReference.bytes(builder);

                XContentBuilder compareBuilder = SmileXContent.contentBuilder();
                compareBuilder.startObject();
                metadataWithoutIndices.toXContent(compareBuilder, compareFormatParams);
                compareBuilder.endObject();
                final BytesReference compareOriginalBytes = BytesReference.bytes(compareBuilder);

                final Metadata loadedMetadata;
                try (XContentParser parser = createParser(OpenSearchNodeCommand.namedXContentRegistry,
                    SmileXContent.smileXContent, originalBytes)) {
                    loadedMetadata = Metadata.fromXContent(parser);
                }
                builder = SmileXContent.contentBuilder();
                builder.startObject();
                loadedMetadata.toXContent(builder, compareFormatParams);
                builder.endObject();
                final BytesReference parsedBytes = BytesReference.bytes(builder);

                assertNull(
                    "cluster state XContent serialization does not match, expected " +
                        XContentHelper.convertToMap(compareOriginalBytes, false, XContentType.SMILE) +
                        " but got " +
                        XContentHelper.convertToMap(parsedBytes, false, XContentType.SMILE),
                    differenceBetweenMapsIgnoringArrayOrder(
                        XContentHelper.convertToMap(compareOriginalBytes, false, XContentType.SMILE).v2(),
                        XContentHelper.convertToMap(parsedBytes, false, XContentType.SMILE).v2()));
            }

            for (IndexMetadata indexMetadata : metadata) {
                XContentBuilder builder = SmileXContent.contentBuilder();
                builder.startObject();
                indexMetadata.toXContent(builder, serializationFormatParams);
                builder.endObject();
                final BytesReference originalBytes = BytesReference.bytes(builder);

                XContentBuilder compareBuilder = SmileXContent.contentBuilder();
                compareBuilder.startObject();
                indexMetadata.toXContent(compareBuilder, compareFormatParams);
                compareBuilder.endObject();
                final BytesReference compareOriginalBytes = BytesReference.bytes(compareBuilder);

                final IndexMetadata loadedIndexMetadata;
                try (XContentParser parser = createParser(OpenSearchNodeCommand.namedXContentRegistry,
                    SmileXContent.smileXContent, originalBytes)) {
                    loadedIndexMetadata = IndexMetadata.fromXContent(parser);
                }
                builder = SmileXContent.contentBuilder();
                builder.startObject();
                loadedIndexMetadata.toXContent(builder, compareFormatParams);
                builder.endObject();
                final BytesReference parsedBytes = BytesReference.bytes(builder);

                assertNull(
                    "cluster state XContent serialization does not match, expected " +
                        XContentHelper.convertToMap(compareOriginalBytes, false, XContentType.SMILE) +
                        " but got " +
                        XContentHelper.convertToMap(parsedBytes, false, XContentType.SMILE),
                    differenceBetweenMapsIgnoringArrayOrder(
                        XContentHelper.convertToMap(compareOriginalBytes, false, XContentType.SMILE).v2(),
                        XContentHelper.convertToMap(parsedBytes, false, XContentType.SMILE).v2()));
            }
        }
    }

    /**
     * Tests if the client is a transport client or wraps a transport client.
     *
     * @param client the client to test
     * @return true if the client is a transport client or a wrapped transport client
     */
    private boolean isTransportClient(final Client client) {
        if (TransportClient.class.isAssignableFrom(client.getClass())) {
            return true;
        } else if (client instanceof RandomizingClient) {
            return isTransportClient(((RandomizingClient) client).in());
        }
        return false;
    }

    private static final Set<String> SAFE_METADATA_CUSTOMS =
        Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(IndexGraveyard.TYPE, IngestMetadata.TYPE, RepositoriesMetadata.TYPE, ScriptMetadata.TYPE)));

    private static final Set<String> SAFE_CUSTOMS =
        Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(RestoreInProgress.TYPE, SnapshotDeletionsInProgress.TYPE, SnapshotsInProgress.TYPE)));

    /**
     * Remove any customs except for customs that we know all clients understand.
     *
     * @param clusterState the cluster state to remove possibly-unknown customs from
     * @return the cluster state with possibly-unknown customs removed
     */
    private ClusterState removePluginCustoms(final ClusterState clusterState) {
        final ClusterState.Builder builder = ClusterState.builder(clusterState);
        clusterState.customs().keysIt().forEachRemaining(key -> {
            if (SAFE_CUSTOMS.contains(key) == false) {
                builder.removeCustom(key);
            }
        });
        final Metadata.Builder mdBuilder = Metadata.builder(clusterState.metadata());
        clusterState.metadata().customs().keysIt().forEachRemaining(key -> {
            if (SAFE_METADATA_CUSTOMS.contains(key) == false) {
                mdBuilder.removeCustom(key);
            }
        });
        builder.metadata(mdBuilder);
        return builder.build();
    }

    /**
     * Ensures the cluster is in a searchable state for the given indices. This means a searchable copy of each
     * shard is available on the cluster.
     */
    protected ClusterHealthStatus ensureSearchable(String... indices) {
        // this is just a temporary thing but it's easier to change if it is encapsulated.
        return ensureGreen(indices);
    }

    protected void ensureStableCluster(int nodeCount) {
        ensureStableCluster(nodeCount, TimeValue.timeValueSeconds(30));
    }

    protected void ensureStableCluster(int nodeCount, TimeValue timeValue) {
        ensureStableCluster(nodeCount, timeValue, false, null);
    }

    protected void ensureStableCluster(int nodeCount, @Nullable String viaNode) {
        ensureStableCluster(nodeCount, TimeValue.timeValueSeconds(30), false, viaNode);
    }

    protected void ensureStableCluster(int nodeCount, TimeValue timeValue, boolean local, @Nullable String viaNode) {
        if (viaNode == null) {
            viaNode = randomFrom(internalCluster().getNodeNames());
        }
        logger.debug("ensuring cluster is stable with [{}] nodes. access node: [{}]. timeout: [{}]", nodeCount, viaNode, timeValue);
        ClusterHealthResponse clusterHealthResponse = client(viaNode).admin().cluster().prepareHealth()
            .setWaitForEvents(Priority.LANGUID)
            .setWaitForNodes(Integer.toString(nodeCount))
            .setTimeout(timeValue)
            .setLocal(local)
            .setWaitForNoRelocatingShards(true)
            .get();
        if (clusterHealthResponse.isTimedOut()) {
            ClusterStateResponse stateResponse = client(viaNode).admin().cluster().prepareState().get();
            fail("failed to reach a stable cluster of [" + nodeCount + "] nodes. Tried via [" + viaNode + "]. last cluster state:\n"
                + stateResponse.getState());
        }
        assertThat(clusterHealthResponse.isTimedOut(), is(false));
        ensureFullyConnectedCluster();
    }

    /**
     * Ensures that all nodes in the cluster are connected to each other.
     *
     * Some network disruptions may leave nodes that are not the master disconnected from each other.
     * {@link org.opensearch.cluster.NodeConnectionsService} will eventually reconnect but it's
     * handy to be able to ensure this happens faster
     */
    protected void ensureFullyConnectedCluster() {
        NetworkDisruption.ensureFullyConnectedCluster(internalCluster());
    }

    /**
     * Syntactic sugar for:
     * <pre>
     *   client().prepareIndex(index, type).setSource(source).execute().actionGet();
     * </pre>
     */
    protected final IndexResponse index(String index, String type, XContentBuilder source) {
        return client().prepareIndex(index, type).setSource(source).execute().actionGet();
    }

    /**
     * Syntactic sugar for:
     * <pre>
     *   client().prepareIndex(index, type).setSource(source).execute().actionGet();
     * </pre>
     */
    protected final IndexResponse index(String index, String type, String id, Map<String, Object> source) {
        return client().prepareIndex(index, type, id).setSource(source).execute().actionGet();
    }

    /**
     * Syntactic sugar for:
     * <pre>
     *   return client().prepareIndex(index, type, id).setSource(source).execute().actionGet();
     * </pre>
     */
    protected final IndexResponse index(String index, String type, String id, XContentBuilder source) {
        return client().prepareIndex(index, type, id).setSource(source).execute().actionGet();
    }

    /**
     * Syntactic sugar for:
     * <pre>
     *   return client().prepareIndex(index, type, id).setSource(source).execute().actionGet();
     * </pre>
     */
    protected final IndexResponse index(String index, String type, String id, Object... source) {
        return client().prepareIndex(index, type, id).setSource(source).execute().actionGet();
    }

    /**
     * Syntactic sugar for:
     * <pre>
     *   return client().prepareIndex(index, type, id).setSource(source).execute().actionGet();
     * </pre>
     * <p>
     * where source is a JSON String.
     */
    protected final IndexResponse index(String index, String type, String id, String source) {
        return client().prepareIndex(index, type, id).setSource(source, XContentType.JSON).execute().actionGet();
    }

    /**
     * Waits for relocations and refreshes all indices in the cluster.
     *
     * @see #waitForRelocation()
     */
    protected final RefreshResponse refresh(String... indices) {
        waitForRelocation();
        // TODO RANDOMIZE with flush?
        RefreshResponse actionGet = client().admin().indices().prepareRefresh(indices)
            .setIndicesOptions(IndicesOptions.STRICT_EXPAND_OPEN_HIDDEN_FORBID_CLOSED).execute().actionGet();
        assertNoFailures(actionGet);
        return actionGet;
    }

    /**
     * Flushes and refreshes all indices in the cluster
     */
    protected final void flushAndRefresh(String... indices) {
        flush(indices);
        refresh(indices);
    }

    /**
     * Flush some or all indices in the cluster.
     */
    protected final FlushResponse flush(String... indices) {
        waitForRelocation();
        FlushResponse actionGet = client().admin().indices().prepareFlush(indices).execute().actionGet();
        for (DefaultShardOperationFailedException failure : actionGet.getShardFailures()) {
            assertThat("unexpected flush failure " + failure.reason(), failure.status(), equalTo(RestStatus.SERVICE_UNAVAILABLE));
        }
        return actionGet;
    }

    /**
     * Waits for all relocations and force merge all indices in the cluster to 1 segment.
     */
    protected ForceMergeResponse forceMerge() {
        waitForRelocation();
        ForceMergeResponse actionGet = client().admin().indices().prepareForceMerge().setMaxNumSegments(1).execute().actionGet();
        assertNoFailures(actionGet);
        return actionGet;
    }

    /**
     * Returns <code>true</code> iff the given index exists otherwise <code>false</code>
     */
    protected static boolean indexExists(String index) {
        IndicesExistsResponse actionGet = client().admin().indices().prepareExists(index).execute().actionGet();
        return actionGet.isExists();
    }

    /**
     * Syntactic sugar for enabling allocation for <code>indices</code>
     */
    protected final void enableAllocation(String... indices) {
        client().admin().indices().prepareUpdateSettings(indices).setSettings(Settings.builder().put(
            EnableAllocationDecider.INDEX_ROUTING_ALLOCATION_ENABLE_SETTING.getKey(), "all"
        )).get();
    }

    /**
     * Syntactic sugar for disabling allocation for <code>indices</code>
     */
    protected final void disableAllocation(String... indices) {
        client().admin().indices().prepareUpdateSettings(indices).setSettings(Settings.builder().put(
            EnableAllocationDecider.INDEX_ROUTING_ALLOCATION_ENABLE_SETTING.getKey(), "none"
        )).get();
    }

    /**
     * Returns a random admin client. This client can either be a node or a transport client pointing to any of
     * the nodes in the cluster.
     */
    protected AdminClient admin() {
        return client().admin();
    }

    /**
     * Returns a random cluster admin client. This client can be pointing to any of the nodes in the cluster.
     */
    protected ClusterAdminClient clusterAdmin() {
        return admin().cluster();
    }

    /**
     * Convenience method that forwards to {@link #indexRandom(boolean, List)}.
     */
    public void indexRandom(boolean forceRefresh, IndexRequestBuilder... builders) throws InterruptedException {
        indexRandom(forceRefresh, Arrays.asList(builders));
    }

    public void indexRandom(boolean forceRefresh, boolean dummyDocuments, IndexRequestBuilder... builders)
        throws InterruptedException {
        indexRandom(forceRefresh, dummyDocuments, Arrays.asList(builders));
    }

    /**
     * Indexes the given {@link IndexRequestBuilder} instances randomly. It shuffles the given builders and either
     * indexes them in a blocking or async fashion. This is very useful to catch problems that relate to internal document
     * ids or index segment creations. Some features might have bug when a given document is the first or the last in a
     * segment or if only one document is in a segment etc. This method prevents issues like this by randomizing the index
     * layout.
     *
     * @param forceRefresh if {@code true} all involved indices are refreshed
     *   once the documents are indexed. Additionally if {@code true} some
     *   empty dummy documents are may be randomly inserted into the document
     *   list and deleted once all documents are indexed. This is useful to
     *   produce deleted documents on the server side.
     * @param builders     the documents to index.
     * @see #indexRandom(boolean, boolean, java.util.List)
     */
    public void indexRandom(boolean forceRefresh, List<IndexRequestBuilder> builders) throws InterruptedException {
        indexRandom(forceRefresh, forceRefresh, builders);
    }

    /**
     * Indexes the given {@link IndexRequestBuilder} instances randomly. It shuffles the given builders and either
     * indexes them in a blocking or async fashion. This is very useful to catch problems that relate to internal document
     * ids or index segment creations. Some features might have bug when a given document is the first or the last in a
     * segment or if only one document is in a segment etc. This method prevents issues like this by randomizing the index
     * layout.
     *
     * @param forceRefresh   if {@code true} all involved indices are refreshed once the documents are indexed.
     * @param dummyDocuments if {@code true} some empty dummy documents may be randomly inserted into the document list and deleted once
     *                       all documents are indexed. This is useful to produce deleted documents on the server side.
     * @param builders       the documents to index.
     */
    public void indexRandom(boolean forceRefresh, boolean dummyDocuments, List<IndexRequestBuilder> builders)
        throws InterruptedException {
        indexRandom(forceRefresh, dummyDocuments, true, builders);
    }

    /**
     * Indexes the given {@link IndexRequestBuilder} instances randomly. It shuffles the given builders and either
     * indexes them in a blocking or async fashion. This is very useful to catch problems that relate to internal document
     * ids or index segment creations. Some features might have bug when a given document is the first or the last in a
     * segment or if only one document is in a segment etc. This method prevents issues like this by randomizing the index
     * layout.
     *
     * @param forceRefresh   if {@code true} all involved indices are refreshed once the documents are indexed.
     * @param dummyDocuments if {@code true} some empty dummy documents may be randomly inserted into the document list and deleted once
     *                       all documents are indexed. This is useful to produce deleted documents on the server side.
     * @param maybeFlush     if {@code true} this method may randomly execute full flushes after index operations.
     * @param builders       the documents to index.
     */
    public void indexRandom(boolean forceRefresh, boolean dummyDocuments, boolean maybeFlush, List<IndexRequestBuilder> builders)
        throws InterruptedException {
        Random random = random();
        Map<String, Set<String>> indicesAndTypes = new HashMap<>();
        for (IndexRequestBuilder builder : builders) {
            final Set<String> types = indicesAndTypes.computeIfAbsent(builder.request().index(), index -> new HashSet<>());
            types.add(builder.request().type());
        }
        Set<List<String>> bogusIds = new HashSet<>(); // (index, type, id)
        if (random.nextBoolean() && !builders.isEmpty() && dummyDocuments) {
            builders = new ArrayList<>(builders);
            // inject some bogus docs
            final int numBogusDocs = scaledRandomIntBetween(1, builders.size() * 2);
            final int unicodeLen = between(1, 10);
            for (int i = 0; i < numBogusDocs; i++) {
                String id = "bogus_doc_"
                    + randomRealisticUnicodeOfLength(unicodeLen)
                    + Integer.toString(dummmyDocIdGenerator.incrementAndGet());
                Map.Entry<String, Set<String>> indexAndTypes = RandomPicks.randomFrom(random, indicesAndTypes.entrySet());
                String index = indexAndTypes.getKey();
                String type = RandomPicks.randomFrom(random, indexAndTypes.getValue());
                bogusIds.add(Arrays.asList(index, type, id));
                // We configure a routing key in case the mapping requires it
                builders.add(client().prepareIndex(index, type, id).setSource("{}", XContentType.JSON).setRouting(id));
            }
        }
        Collections.shuffle(builders, random());
        final CopyOnWriteArrayList<Tuple<IndexRequestBuilder, Exception>> errors = new CopyOnWriteArrayList<>();
        List<CountDownLatch> inFlightAsyncOperations = new ArrayList<>();
        // If you are indexing just a few documents then frequently do it one at a time.  If many then frequently in bulk.
        final String[] indices = indicesAndTypes.keySet().toArray(new String[0]);
        if (builders.size() < FREQUENT_BULK_THRESHOLD ? frequently() : builders.size() < ALWAYS_BULK_THRESHOLD ? rarely() : false) {
            if (frequently()) {
                logger.info("Index [{}] docs async: [{}] bulk: [{}]", builders.size(), true, false);
                for (IndexRequestBuilder indexRequestBuilder : builders) {
                    indexRequestBuilder.execute(
                        new PayloadLatchedActionListener<>(indexRequestBuilder, newLatch(inFlightAsyncOperations), errors));
                    postIndexAsyncActions(indices, inFlightAsyncOperations, maybeFlush);
                }
            } else {
                logger.info("Index [{}] docs async: [{}] bulk: [{}]", builders.size(), false, false);
                for (IndexRequestBuilder indexRequestBuilder : builders) {
                    indexRequestBuilder.execute().actionGet();
                    postIndexAsyncActions(indices, inFlightAsyncOperations, maybeFlush);
                }
            }
        } else {
            List<List<IndexRequestBuilder>> partition = eagerPartition(builders, Math.min(MAX_BULK_INDEX_REQUEST_SIZE,
                Math.max(1, (int) (builders.size() * randomDouble()))));
            logger.info("Index [{}] docs async: [{}] bulk: [{}] partitions [{}]", builders.size(), false, true, partition.size());
            for (List<IndexRequestBuilder> segmented : partition) {
                BulkRequestBuilder bulkBuilder = client().prepareBulk();
                for (IndexRequestBuilder indexRequestBuilder : segmented) {
                    bulkBuilder.add(indexRequestBuilder);
                }
                BulkResponse actionGet = bulkBuilder.execute().actionGet();
                assertThat(actionGet.hasFailures() ? actionGet.buildFailureMessage() : "", actionGet.hasFailures(), equalTo(false));
            }
        }
        for (CountDownLatch operation : inFlightAsyncOperations) {
            operation.await();
        }
        final List<Exception> actualErrors = new ArrayList<>();
        for (Tuple<IndexRequestBuilder, Exception> tuple : errors) {
            Throwable t = ExceptionsHelper.unwrapCause(tuple.v2());
            if (t instanceof EsRejectedExecutionException) {
                logger.debug("Error indexing doc: " + t.getMessage() + ", reindexing.");
                tuple.v1().execute().actionGet(); // re-index if rejected
            } else {
                actualErrors.add(tuple.v2());
            }
        }
        assertThat(actualErrors, emptyIterable());
        if (!bogusIds.isEmpty()) {
            // delete the bogus types again - it might trigger merges or at least holes in the segments and enforces deleted docs!
            for (List<String> doc : bogusIds) {
                assertEquals("failed to delete a dummy doc [" + doc.get(0) + "][" + doc.get(2) + "]",
                    DocWriteResponse.Result.DELETED,
                    client().prepareDelete(doc.get(0), doc.get(1), doc.get(2)).setRouting(doc.get(2)).get().getResult());
            }
        }
        if (forceRefresh) {
            assertNoFailures(client().admin().indices().prepareRefresh(indices)
                .setIndicesOptions(IndicesOptions.lenientExpandOpen())
                .get());
        }
    }

    private final AtomicInteger dummmyDocIdGenerator = new AtomicInteger();

    /** Disables an index block for the specified index */
    public static void disableIndexBlock(String index, String block) {
        Settings settings = Settings.builder().put(block, false).build();
        client().admin().indices().prepareUpdateSettings(index).setSettings(settings).get();
    }

    /** Enables an index block for the specified index */
    public static void enableIndexBlock(String index, String block) {
        if (IndexMetadata.APIBlock.fromSetting(block) == IndexMetadata.APIBlock.READ_ONLY_ALLOW_DELETE || randomBoolean()) {
            // the read-only-allow-delete block isn't supported by the add block API so we must use the update settings API here.
            Settings settings = Settings.builder().put(block, true).build();
            client().admin().indices().prepareUpdateSettings(index).setSettings(settings).get();
        } else {
            client().admin().indices().prepareAddBlock(IndexMetadata.APIBlock.fromSetting(block), index).get();
        }
    }

    /** Sets or unsets the cluster read_only mode **/
    public static void setClusterReadOnly(boolean value) {
        Settings settings = value ? Settings.builder().put(Metadata.SETTING_READ_ONLY_SETTING.getKey(), value).build() :
            Settings.builder().putNull(Metadata.SETTING_READ_ONLY_SETTING.getKey()).build()  ;
        assertAcked(client().admin().cluster().prepareUpdateSettings().setTransientSettings(settings).get());
    }

    private static CountDownLatch newLatch(List<CountDownLatch> latches) {
        CountDownLatch l = new CountDownLatch(1);
        latches.add(l);
        return l;
    }

    /**
     * Maybe refresh, force merge, or flush then always make sure there aren't too many in flight async operations.
     */
    private void postIndexAsyncActions(String[] indices, List<CountDownLatch> inFlightAsyncOperations, boolean maybeFlush)
        throws InterruptedException {
        if (rarely()) {
            if (rarely()) {
                client().admin().indices().prepareRefresh(indices).setIndicesOptions(IndicesOptions.lenientExpandOpen()).execute(
                    new LatchedActionListener<>(newLatch(inFlightAsyncOperations)));
            } else if (maybeFlush && rarely()) {
                if (randomBoolean()) {
                    client().admin().indices().prepareFlush(indices).setIndicesOptions(IndicesOptions.lenientExpandOpen()).execute(
                        new LatchedActionListener<>(newLatch(inFlightAsyncOperations)));
                } else {
                    client().admin().indices().syncedFlush(syncedFlushRequest(indices).indicesOptions(IndicesOptions.lenientExpandOpen()),
                        new LatchedActionListener<>(newLatch(inFlightAsyncOperations)));
                }
            } else if (rarely()) {
                client().admin().indices().prepareForceMerge(indices)
                    .setIndicesOptions(IndicesOptions.lenientExpandOpen())
                    .setMaxNumSegments(between(1, 10))
                    .setFlush(maybeFlush && randomBoolean())
                    .execute(new LatchedActionListener<>(newLatch(inFlightAsyncOperations)));
            }
        }
        while (inFlightAsyncOperations.size() > MAX_IN_FLIGHT_ASYNC_INDEXES) {
            int waitFor = between(0, inFlightAsyncOperations.size() - 1);
            inFlightAsyncOperations.remove(waitFor).await();
        }
    }

    /**
     * The scope of a test cluster used together with
     * {@link ESIntegTestCase.ClusterScope} annotations on {@link ESIntegTestCase} subclasses.
     */
    public enum Scope {
        /**
         * A cluster shared across all method in a single test suite
         */
        SUITE,
        /**
         * A test exclusive test cluster
         */
        TEST
    }

    /**
     * Defines a cluster scope for a {@link ESIntegTestCase} subclass.
     * By default if no {@link ClusterScope} annotation is present {@link ESIntegTestCase.Scope#SUITE} is used
     * together with randomly chosen settings like number of nodes etc.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE})
    public @interface ClusterScope {
        /**
         * Returns the scope. {@link ESIntegTestCase.Scope#SUITE} is default.
         */
        Scope scope() default Scope.SUITE;

        /**
         * Returns the number of nodes in the cluster. Default is {@code -1} which means
         * a random number of nodes is used, where the minimum and maximum number of nodes
         * are either the specified ones or the default ones if not specified.
         */
        int numDataNodes() default -1;

        /**
         * Returns the minimum number of data nodes in the cluster. Default is {@code -1}.
         * Ignored when {@link ClusterScope#numDataNodes()} is set.
         */
        int minNumDataNodes() default -1;

        /**
         * Returns the maximum number of data nodes in the cluster.  Default is {@code -1}.
         * Ignored when {@link ClusterScope#numDataNodes()} is set.
         */
        int maxNumDataNodes() default -1;

        /**
         * Indicates whether the cluster can have dedicated master nodes. If {@code false} means data nodes will serve as master nodes
         * and there will be no dedicated master (and data) nodes. Default is {@code false} which means
         * dedicated master nodes will be randomly used.
         */
        boolean supportsDedicatedMasters() default true;

        /**
         * Indicates whether the cluster automatically manages cluster bootstrapping and the removal of any master-eligible nodes as well
         * as {@link ElectMasterService#DISCOVERY_ZEN_MINIMUM_MASTER_NODES_SETTING} if running the pre-7.0 cluster coordination
         * implementation. If set to {@code false} then the tests must manage these things explicitly.
         */
        boolean autoManageMasterNodes() default true;

        /**
         * Returns the number of client nodes in the cluster. Default is {@link InternalTestCluster#DEFAULT_NUM_CLIENT_NODES}, a
         * negative value means that the number of client nodes will be randomized.
         */
        int numClientNodes() default InternalTestCluster.DEFAULT_NUM_CLIENT_NODES;

        /**
         * Returns the transport client ratio. By default this returns <code>-1</code> which means a random
         * ratio in the interval <code>[0..1]</code> is used.
         */
        double transportClientRatio() default -1;
    }

    private class LatchedActionListener<Response> implements ActionListener<Response> {
        private final CountDownLatch latch;

        LatchedActionListener(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public final void onResponse(Response response) {
            latch.countDown();
        }

        @Override
        public final void onFailure(Exception t) {
            try {
                logger.info("Action Failed", t);
                addError(t);
            } finally {
                latch.countDown();
            }
        }

        protected void addError(Exception e) {
        }

    }

    private class PayloadLatchedActionListener<Response, T> extends LatchedActionListener<Response> {
        private final CopyOnWriteArrayList<Tuple<T, Exception>> errors;
        private final T builder;

        PayloadLatchedActionListener(T builder, CountDownLatch latch, CopyOnWriteArrayList<Tuple<T, Exception>> errors) {
            super(latch);
            this.errors = errors;
            this.builder = builder;
        }

        @Override
        protected void addError(Exception e) {
            errors.add(new Tuple<>(builder, e));
        }

    }

    /**
     * Clears the given scroll Ids
     */
    public void clearScroll(String... scrollIds) {
        ClearScrollResponse clearResponse = client().prepareClearScroll()
            .setScrollIds(Arrays.asList(scrollIds)).get();
        assertThat(clearResponse.isSucceeded(), equalTo(true));
    }

    private static <A extends Annotation> A getAnnotation(Class<?> clazz, Class<A> annotationClass) {
        if (clazz == Object.class || clazz == ESIntegTestCase.class) {
            return null;
        }
        A annotation = clazz.getAnnotation(annotationClass);
        if (annotation != null) {
            return annotation;
        }
        return getAnnotation(clazz.getSuperclass(), annotationClass);
    }

    private Scope getCurrentClusterScope() {
        return getCurrentClusterScope(this.getClass());
    }

    private static Scope getCurrentClusterScope(Class<?> clazz) {
        ClusterScope annotation = getAnnotation(clazz, ClusterScope.class);
        // if we are not annotated assume suite!
        return annotation == null ? Scope.SUITE : annotation.scope();
    }

    private boolean getSupportsDedicatedMasters() {
        ClusterScope annotation = getAnnotation(this.getClass(), ClusterScope.class);
        return annotation == null ? true : annotation.supportsDedicatedMasters();
    }

    private boolean getAutoManageMasterNodes() {
        ClusterScope annotation = getAnnotation(this.getClass(), ClusterScope.class);
        return annotation == null ? true : annotation.autoManageMasterNodes();
    }

    private int getNumDataNodes() {
        ClusterScope annotation = getAnnotation(this.getClass(), ClusterScope.class);
        return annotation == null ? -1 : annotation.numDataNodes();
    }

    private int getMinNumDataNodes() {
        ClusterScope annotation = getAnnotation(this.getClass(), ClusterScope.class);
        return annotation == null || annotation.minNumDataNodes() == -1
            ? InternalTestCluster.DEFAULT_MIN_NUM_DATA_NODES : annotation.minNumDataNodes();
    }

    private int getMaxNumDataNodes() {
        ClusterScope annotation = getAnnotation(this.getClass(), ClusterScope.class);
        return annotation == null || annotation.maxNumDataNodes() == -1
            ? InternalTestCluster.DEFAULT_MAX_NUM_DATA_NODES : annotation.maxNumDataNodes();
    }

    private int getNumClientNodes() {
        ClusterScope annotation = getAnnotation(this.getClass(), ClusterScope.class);
        return annotation == null ? InternalTestCluster.DEFAULT_NUM_CLIENT_NODES : annotation.numClientNodes();
    }

    /**
     * This method is used to obtain settings for the {@code N}th node in the cluster.
     * Nodes in this cluster are associated with an ordinal number such that nodes can
     * be started with specific configurations. This method might be called multiple
     * times with the same ordinal and is expected to return the same value for each invocation.
     * In other words subclasses must ensure this method is idempotent.
     */
    protected Settings nodeSettings(int nodeOrdinal) {
        Settings.Builder builder = Settings.builder()
            // Default the watermarks to absurdly low to prevent the tests
            // from failing on nodes without enough disk space
            .put(DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_LOW_DISK_WATERMARK_SETTING.getKey(), "1b")
            .put(DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_HIGH_DISK_WATERMARK_SETTING.getKey(), "1b")
            .put(DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_DISK_FLOOD_STAGE_WATERMARK_SETTING.getKey(), "1b")
            // by default we never cache below 10k docs in a segment,
            // bypass this limit so that caching gets some testing in
            // integration tests that usually create few documents
            .put(IndicesQueryCache.INDICES_QUERIES_CACHE_ALL_SEGMENTS_SETTING.getKey(), nodeOrdinal % 2 == 0)
            // wait short time for other active shards before actually deleting, default 30s not needed in tests
            .put(IndicesStore.INDICES_STORE_DELETE_SHARD_TIMEOUT.getKey(), new TimeValue(1, TimeUnit.SECONDS))
            // randomly enable low-level search cancellation to make sure it does not alter results
            .put(SearchService.LOW_LEVEL_CANCELLATION_SETTING.getKey(), randomBoolean())
            .putList(DISCOVERY_SEED_HOSTS_SETTING.getKey()) // empty list disables a port scan for other nodes
            .putList(DISCOVERY_SEED_PROVIDERS_SETTING.getKey(), "file");
        if (rarely()) {
            // Sometimes adjust the minimum search thread pool size, causing
            // QueueResizingEsThreadPoolExecutor to be used instead of a regular
            // fixed thread pool
            builder.put("thread_pool.search.min_queue_size", 100);
        }
        return builder.build();
    }

    protected Path nodeConfigPath(int nodeOrdinal) {
        return null;
    }

    /**
     * Returns a collection of plugins that should be loaded on each node.
     */
    protected Collection<Class<? extends Plugin>> nodePlugins() {
        return Collections.emptyList();
    }

    /**
     * Returns a collection of plugins that should be loaded when creating a transport client.
     */
    protected Collection<Class<? extends Plugin>> transportClientPlugins() {
        return Collections.emptyList();
    }

    /**
     * This method is used to obtain additional settings for clients created by the internal cluster.
     * These settings will be applied on the client in addition to some randomized settings defined in
     * the cluster. These settings will also override any other settings the internal cluster might
     * add by default.
     */
    protected Settings transportClientSettings() {
        return Settings.EMPTY;
    }

    private ExternalTestCluster buildExternalCluster(String clusterAddresses) throws IOException {
        String[] stringAddresses = clusterAddresses.split(",");
        TransportAddress[] transportAddresses = new TransportAddress[stringAddresses.length];
        int i = 0;
        for (String stringAddress : stringAddresses) {
            URL url = new URL("http://" + stringAddress);
            InetAddress inetAddress = InetAddress.getByName(url.getHost());
            transportAddresses[i++] = new TransportAddress(new InetSocketAddress(inetAddress, url.getPort()));
        }
        return new ExternalTestCluster(createTempDir(), externalClusterClientSettings(), transportClientPlugins(), transportAddresses);
    }

    protected Settings externalClusterClientSettings() {
        return Settings.EMPTY;
    }

    protected boolean ignoreExternalCluster() {
        return false;
    }

    protected TestCluster buildTestCluster(Scope scope, long seed) throws IOException {
        String clusterAddresses = System.getProperty(TESTS_CLUSTER);
        if (Strings.hasLength(clusterAddresses) && ignoreExternalCluster() == false) {
            if (scope == Scope.TEST) {
                throw new IllegalArgumentException("Cannot run TEST scope test with " + TESTS_CLUSTER);
            }
            return buildExternalCluster(clusterAddresses);
        }

        final String nodePrefix;
        switch (scope) {
            case TEST:
                nodePrefix = TEST_CLUSTER_NODE_PREFIX;
                break;
            case SUITE:
                nodePrefix = SUITE_CLUSTER_NODE_PREFIX;
                break;
            default:
                throw new OpenSearchException("Scope not supported: " + scope);
        }


        boolean supportsDedicatedMasters = getSupportsDedicatedMasters();
        int numDataNodes = getNumDataNodes();
        int minNumDataNodes;
        int maxNumDataNodes;
        if (numDataNodes >= 0) {
            minNumDataNodes = maxNumDataNodes = numDataNodes;
        } else {
            minNumDataNodes = getMinNumDataNodes();
            maxNumDataNodes = getMaxNumDataNodes();
        }
        Collection<Class<? extends Plugin>> mockPlugins = getMockPlugins();
        final NodeConfigurationSource nodeConfigurationSource = getNodeConfigSource();
        if (addMockTransportService()) {
            ArrayList<Class<? extends Plugin>> mocks = new ArrayList<>(mockPlugins);
            // add both mock plugins - local and tcp if they are not there
            // we do this in case somebody overrides getMockPlugins and misses to call super
            if (mockPlugins.contains(getTestTransportPlugin()) == false) {
                mocks.add(getTestTransportPlugin());
            }
            mockPlugins = mocks;
        }
        return new InternalTestCluster(seed, createTempDir(), supportsDedicatedMasters, getAutoManageMasterNodes(),
            minNumDataNodes, maxNumDataNodes,
            InternalTestCluster.clusterName(scope.name(), seed) + "-cluster", nodeConfigurationSource, getNumClientNodes(),
            nodePrefix, mockPlugins, getClientWrapper(), forbidPrivateIndexSettings());
    }

    private NodeConfigurationSource getNodeConfigSource() {
        Settings.Builder initialNodeSettings = Settings.builder();
        Settings.Builder initialTransportClientSettings = Settings.builder();
        if (addMockTransportService()) {
            initialNodeSettings.put(NetworkModule.TRANSPORT_TYPE_KEY, getTestTransportType());
            initialTransportClientSettings.put(NetworkModule.TRANSPORT_TYPE_KEY, getTestTransportType());
        }
        return new NodeConfigurationSource() {
            @Override
            public Settings nodeSettings(int nodeOrdinal) {
                return Settings.builder()
                    .put(initialNodeSettings.build())
                    .put(ESIntegTestCase.this.nodeSettings(nodeOrdinal)).build();
            }

            @Override
            public Path nodeConfigPath(int nodeOrdinal) {
                return ESIntegTestCase.this.nodeConfigPath(nodeOrdinal);
            }

            @Override
            public Collection<Class<? extends Plugin>> nodePlugins() {
                return ESIntegTestCase.this.nodePlugins();
            }

            @Override
            public Settings transportClientSettings() {
                return Settings.builder().put(initialTransportClientSettings.build())
                    .put(ESIntegTestCase.this.transportClientSettings()).build();
            }

            @Override
            public Collection<Class<? extends Plugin>> transportClientPlugins() {
                Collection<Class<? extends Plugin>> plugins = ESIntegTestCase.this.transportClientPlugins();
                if (plugins.contains(getTestTransportPlugin()) == false) {
                    plugins = new ArrayList<>(plugins);
                    plugins.add(getTestTransportPlugin());
                }
                return Collections.unmodifiableCollection(plugins);
            }
        };
    }


    /**
     * Iff this returns true mock transport implementations are used for the test runs. Otherwise not mock transport impls are used.
     * The default is {@code true}.
     */
    protected boolean addMockTransportService() {
        return true;
    }

    /** Returns {@code true} iff this test cluster should use a dummy http transport */
    protected boolean addMockHttpTransport() {
        return true;
    }

    /**
     * Returns {@code true} if this test cluster can use a mock internal engine. Defaults to true.
     */
    protected boolean addMockInternalEngine() {
        return true;
    }

    /** Returns {@code true} iff this test cluster should use a dummy geo_shape field mapper */
    protected boolean addMockGeoShapeFieldMapper() {
        return true;
    }

    /**
     * Returns a function that allows to wrap / filter all clients that are exposed by the test cluster. This is useful
     * for debugging or request / response pre and post processing. It also allows to intercept all calls done by the test
     * framework. By default this method returns an identity function {@link Function#identity()}.
     */
    protected Function<Client,Client> getClientWrapper() {
        return Function.identity();
    }

    /** Return the mock plugins the cluster should use */
    protected Collection<Class<? extends Plugin>> getMockPlugins() {
        final ArrayList<Class<? extends Plugin>> mocks = new ArrayList<>();
        if (MOCK_MODULES_ENABLED && randomBoolean()) { // sometimes run without those completely
            if (randomBoolean() && addMockTransportService()) {
                mocks.add(MockTransportService.TestPlugin.class);
            }
            if (randomBoolean()) {
                mocks.add(MockFSIndexStore.TestPlugin.class);
            }
            if (randomBoolean()) {
                mocks.add(NodeMocksPlugin.class);
            }
            if (addMockInternalEngine() && randomBoolean()) {
                mocks.add(MockEngineFactoryPlugin.class);
            }
            if (randomBoolean()) {
                mocks.add(MockSearchService.TestPlugin.class);
            }
            if (randomBoolean()) {
                mocks.add(MockFieldFilterPlugin.class);
            }
        }
        if (addMockTransportService()) {
            mocks.add(getTestTransportPlugin());
        }
        if (addMockHttpTransport()) {
            mocks.add(MockHttpTransport.TestPlugin.class);
        }
        mocks.add(TestSeedPlugin.class);
        mocks.add(AssertActionNamePlugin.class);
        mocks.add(MockScriptService.TestPlugin.class);
        if (addMockGeoShapeFieldMapper()) {
            mocks.add(TestGeoShapeFieldMapperPlugin.class);
        }
        return Collections.unmodifiableList(mocks);
    }

    public static final class TestSeedPlugin extends Plugin {
        @Override
        public List<Setting<?>> getSettings() {
            return Collections.singletonList(INDEX_TEST_SEED_SETTING);
        }
    }

    public static final class AssertActionNamePlugin extends Plugin implements NetworkPlugin {
        @Override
        public List<TransportInterceptor> getTransportInterceptors(NamedWriteableRegistry namedWriteableRegistry,
                                                                   ThreadContext threadContext) {
            return Arrays.asList(new TransportInterceptor() {
                @Override
                public <T extends TransportRequest> TransportRequestHandler<T> interceptHandler(String action, String executor,
                                                                                                boolean forceExecution,
                                                                                                TransportRequestHandler<T> actualHandler) {
                    if (TransportService.isValidActionName(action) == false) {
                        throw new IllegalArgumentException("invalid action name [" + action + "] must start with one of: " +
                            TransportService.VALID_ACTION_PREFIXES );
                    }
                    return actualHandler;
                }
            });
        }
    }

    /**
     * Returns the client ratio configured via
     */
    private static double transportClientRatio() {
        String property = System.getProperty(TESTS_CLIENT_RATIO);
        if (property == null || property.isEmpty()) {
            return Double.NaN;
        }
        return Double.parseDouble(property);
    }

    /**
     * Returns the transport client ratio from the class level annotation or via
     * {@link System#getProperty(String)} if available. If both are not available this will
     * return a random ratio in the interval {@code [0..1]}.
     */
    protected double getPerTestTransportClientRatio() {
        final ClusterScope annotation = getAnnotation(this.getClass(), ClusterScope.class);
        double perTestRatio = -1;
        if (annotation != null) {
            perTestRatio = annotation.transportClientRatio();
        }
        if (perTestRatio == -1) {
            return Double.isNaN(TRANSPORT_CLIENT_RATIO) ? randomDouble() : TRANSPORT_CLIENT_RATIO;
        }
        assert perTestRatio >= 0.0 && perTestRatio <= 1.0;
        return perTestRatio;
    }

    /**
     * Returns path to a random directory that can be used to create a temporary file system repo
     */
    public Path randomRepoPath() {
        if (currentCluster instanceof InternalTestCluster) {
            return randomRepoPath(((InternalTestCluster) currentCluster).getDefaultSettings());
        }
        throw new UnsupportedOperationException("unsupported cluster type");
    }

    /**
     * Returns path to a random directory that can be used to create a temporary file system repo
     */
    public static Path randomRepoPath(Settings settings) {
        Environment environment = TestEnvironment.newEnvironment(settings);
        Path[] repoFiles = environment.repoFiles();
        assert repoFiles.length > 0;
        Path path;
        do {
            path = repoFiles[0].resolve(randomAlphaOfLength(10));
        } while (Files.exists(path));
        return path;
    }

    protected NumShards getNumShards(String index) {
        Metadata metadata = client().admin().cluster().prepareState().get().getState().metadata();
        assertThat(metadata.hasIndex(index), equalTo(true));
        int numShards = Integer.valueOf(metadata.index(index).getSettings().get(SETTING_NUMBER_OF_SHARDS));
        int numReplicas = Integer.valueOf(metadata.index(index).getSettings().get(SETTING_NUMBER_OF_REPLICAS));
        return new NumShards(numShards, numReplicas);
    }

    /**
     * Asserts that all shards are allocated on nodes matching the given node pattern.
     */
    public Set<String> assertAllShardsOnNodes(String index, String... pattern) {
        Set<String> nodes = new HashSet<>();
        ClusterState clusterState = client().admin().cluster().prepareState().execute().actionGet().getState();
        for (IndexRoutingTable indexRoutingTable : clusterState.routingTable()) {
            for (IndexShardRoutingTable indexShardRoutingTable : indexRoutingTable) {
                for (ShardRouting shardRouting : indexShardRoutingTable) {
                    if (shardRouting.currentNodeId() != null && index.equals(shardRouting.getIndexName())) {
                        String name = clusterState.nodes().get(shardRouting.currentNodeId()).getName();
                        nodes.add(name);
                        assertThat("Allocated on new node: " + name, Regex.simpleMatch(pattern, name), is(true));
                    }
                }
            }
        }
        return nodes;
    }


    /**
     * Asserts that all segments are sorted with the provided {@link Sort}.
     */
    public void assertSortedSegments(String indexName, Sort expectedIndexSort) {
        IndicesSegmentResponse segmentResponse =
            client().admin().indices().prepareSegments(indexName).execute().actionGet();
        IndexSegments indexSegments = segmentResponse.getIndices().get(indexName);
        for (IndexShardSegments indexShardSegments : indexSegments.getShards().values()) {
            for (ShardSegments shardSegments : indexShardSegments.getShards()) {
                for (Segment segment : shardSegments) {
                    assertThat(expectedIndexSort, equalTo(segment.getSegmentSort()));
                }
            }
        }
    }

    protected static class NumShards {
        public final int numPrimaries;
        public final int numReplicas;
        public final int totalNumShards;
        public final int dataCopies;

        private NumShards(int numPrimaries, int numReplicas) {
            this.numPrimaries = numPrimaries;
            this.numReplicas = numReplicas;
            this.dataCopies = numReplicas + 1;
            this.totalNumShards = numPrimaries * dataCopies;
        }
    }

    private static boolean runTestScopeLifecycle() {
        return INSTANCE == null;
    }


    @Before
    public final void setupTestCluster() throws Exception {
        if (runTestScopeLifecycle()) {
            printTestMessage("setting up");
            beforeInternal();
            printTestMessage("all set up");
        }
    }


    @After
    public final void cleanUpCluster() throws Exception {
        // Deleting indices is going to clear search contexts implicitly so we
        // need to check that there are no more in-flight search contexts before
        // we remove indices
        if (isInternalCluster()) {
            internalCluster().setBootstrapMasterNodeIndex(-1);
        }
        super.ensureAllSearchContextsReleased();
        if (runTestScopeLifecycle()) {
            printTestMessage("cleaning up after");
            afterInternal(false);
            printTestMessage("cleaned up after");
        }
    }

    @AfterClass
    public static void afterClass() throws Exception {
        try {
            if (runTestScopeLifecycle()) {
                clearClusters();
            } else {
                INSTANCE.printTestMessage("cleaning up after");
                INSTANCE.afterInternal(true);
                checkStaticState(true);
            }
        } finally {
            SUITE_SEED = null;
            currentCluster = null;
            INSTANCE = null;
        }
    }

    private static void initializeSuiteScope() throws Exception {
        Class<?> targetClass = getTestClass();
        /**
         * Note we create these test class instance via reflection
         * since JUnit creates a new instance per test and that is also
         * the reason why INSTANCE is static since this entire method
         * must be executed in a static context.
         */
        assert INSTANCE == null;
        if (isSuiteScopedTest(targetClass)) {
            // note we need to do this this way to make sure this is reproducible
            INSTANCE = (ESIntegTestCase) targetClass.getConstructor().newInstance();
            boolean success = false;
            try {
                INSTANCE.printTestMessage("setup");
                INSTANCE.beforeInternal();
                INSTANCE.setupSuiteScopeCluster();
                success = true;
            } finally {
                if (!success) {
                    afterClass();
                }
            }
        } else {
            INSTANCE = null;
        }
    }

    /**
     * Compute a routing key that will route documents to the <code>shard</code>-th shard
     * of the provided index.
     */
    protected String routingKeyForShard(String index, int shard) {
        return internalCluster().routingKeyForShard(resolveIndex(index), shard, random());
    }

    @Override
    protected NamedXContentRegistry xContentRegistry() {
        if (isInternalCluster() && cluster().size() > 0) {
            // If it's internal cluster - using existing registry in case plugin registered custom data
            return internalCluster().getInstance(NamedXContentRegistry.class);
        } else {
            // If it's external cluster - fall back to the standard set
            return new NamedXContentRegistry(ClusterModule.getNamedXWriteables());
        }
    }

    protected boolean forbidPrivateIndexSettings() {
        return true;
    }

    /**
     * Returns an instance of {@link RestClient} pointing to the current test cluster.
     * Creates a new client if the method is invoked for the first time in the context of the current test scope.
     * The returned client gets automatically closed when needed, it shouldn't be closed as part of tests otherwise
     * it cannot be reused by other tests anymore.
     */
    protected static synchronized RestClient getRestClient() {
        if (restClient == null) {
            restClient = createRestClient();
        }
        return restClient;
    }

    protected static RestClient createRestClient() {
        return createRestClient(null, "http");
    }

    protected static RestClient createRestClient(RestClientBuilder.HttpClientConfigCallback httpClientConfigCallback, String protocol) {
        NodesInfoResponse nodesInfoResponse = client().admin().cluster().prepareNodesInfo().get();
        assertFalse(nodesInfoResponse.hasFailures());
        return createRestClient(nodesInfoResponse.getNodes(), httpClientConfigCallback, protocol);
    }

    protected static RestClient createRestClient(final List<NodeInfo> nodes,
                                                 RestClientBuilder.HttpClientConfigCallback httpClientConfigCallback, String protocol) {
        List<HttpHost> hosts = new ArrayList<>();
        for (NodeInfo node : nodes) {
            if (node.getInfo(HttpInfo.class) != null) {
                TransportAddress publishAddress = node.getInfo(HttpInfo.class).address().publishAddress();
                InetSocketAddress address = publishAddress.address();
                hosts.add(new HttpHost(NetworkAddress.format(address.getAddress()), address.getPort(), protocol));
            }
        }
        RestClientBuilder builder = RestClient.builder(hosts.toArray(new HttpHost[hosts.size()]));
        if (httpClientConfigCallback != null) {
            builder.setHttpClientConfigCallback(httpClientConfigCallback);
        }
        return builder.build();
    }

    /**
     * This method is executed iff the test is annotated with {@link SuiteScopeTestCase}
     * before the first test of this class is executed.
     *
     * @see SuiteScopeTestCase
     */
    protected void setupSuiteScopeCluster() throws Exception {
    }

    private static boolean isSuiteScopedTest(Class<?> clazz) {
        return clazz.getAnnotation(SuiteScopeTestCase.class) != null;
    }

    /**
     * If a test is annotated with {@link SuiteScopeTestCase}
     * the checks and modifications that are applied to the used test cluster are only done after all tests
     * of this class are executed. This also has the side-effect of a suite level setup method {@link #setupSuiteScopeCluster()}
     * that is executed in a separate test instance. Variables that need to be accessible across test instances must be static.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @Target(ElementType.TYPE)
    public @interface SuiteScopeTestCase {
    }

    public static Index resolveIndex(String index) {
        GetIndexResponse getIndexResponse = client().admin().indices().prepareGetIndex().setIndices(index).get();
        assertTrue("index " + index + " not found", getIndexResponse.getSettings().containsKey(index));
        String uuid = getIndexResponse.getSettings().get(index).get(IndexMetadata.SETTING_INDEX_UUID);
        return new Index(index, uuid);
    }

    public static String resolveCustomDataPath(String index) {
        GetIndexResponse getIndexResponse = client().admin().indices().prepareGetIndex().setIndices(index).get();
        assertTrue("index " + index + " not found", getIndexResponse.getSettings().containsKey(index));
        return getIndexResponse.getSettings().get(index).get(IndexMetadata.SETTING_DATA_PATH);
    }

    public static boolean inFipsJvm() {
        return Boolean.parseBoolean(System.getProperty(FIPS_SYSPROP));
    }

    /**
     * On Debian 8 the "memory" subsystem is not mounted by default
     * when cgroups are enabled, and this confuses many versions of
     * Java prior to Java 15.  Tests that rely on machine memory
     * being accurately determined will not work on such setups,
     * and can use this method for selective muting.
     * See https://github.com/elastic/elasticsearch/issues/67089
     * and https://github.com/elastic/elasticsearch/issues/66885
     */
    protected boolean willSufferDebian8MemoryProblem() {
        final NodesInfoResponse response = client().admin().cluster().prepareNodesInfo().execute().actionGet();
        final boolean anyDebian8Nodes = response.getNodes()
            .stream()
            .anyMatch(ni -> ni.getInfo(OsInfo.class).getPrettyName().equals("Debian GNU/Linux 8 (jessie)"));
        boolean java15Plus = JavaVersion.current().compareTo(JavaVersion.parse("15")) >= 0;
        return anyDebian8Nodes && java15Plus == false;
    }
}
