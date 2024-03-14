/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.plugin.insights.core.listener;

import org.opensearch.action.search.SearchPhaseContext;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchRequestContext;
import org.opensearch.action.search.SearchType;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.InjectSecurity;
import org.opensearch.commons.authuser.User;
import org.opensearch.plugin.insights.core.service.QueryInsightsService;
import org.opensearch.plugin.insights.core.service.TopQueriesService;
import org.opensearch.plugin.insights.rules.model.Attribute;
import org.opensearch.plugin.insights.rules.model.MetricType;
import org.opensearch.plugin.insights.rules.model.SearchQueryRecord;
import org.opensearch.plugin.insights.settings.QueryInsightsSettings;
import org.opensearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.opensearch.search.aggregations.support.ValueType;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.junit.Before;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Phaser;

import org.mockito.ArgumentCaptor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit Tests for {@link QueryInsightsListener}.
 */
public class QueryInsightsListenerTests extends OpenSearchTestCase {
    private final SearchRequestContext searchRequestContext = mock(SearchRequestContext.class);
    private final SearchPhaseContext searchPhaseContext = mock(SearchPhaseContext.class);
    private final SearchRequest searchRequest = mock(SearchRequest.class);
    private final QueryInsightsService queryInsightsService = mock(QueryInsightsService.class);
    private final TopQueriesService topQueriesService = mock(TopQueriesService.class);
    private final ThreadPool threadPool = mock(ThreadPool.class);
    private final Settings.Builder settingsBuilder = Settings.builder();
    private final Settings settings = settingsBuilder.build();
    private final String remoteAddress = "1.2.3.4";
    private User user;
    private ClusterService clusterService;

    @Before
    public void setup() {
        ClusterSettings clusterSettings = new ClusterSettings(settings, ClusterSettings.BUILT_IN_CLUSTER_SETTINGS);
        clusterSettings.registerSetting(QueryInsightsSettings.TOP_N_LATENCY_QUERIES_ENABLED);
        clusterSettings.registerSetting(QueryInsightsSettings.TOP_N_LATENCY_QUERIES_SIZE);
        clusterSettings.registerSetting(QueryInsightsSettings.TOP_N_LATENCY_QUERIES_WINDOW_SIZE);
        clusterService = new ClusterService(settings, clusterSettings, null);
        when(queryInsightsService.isCollectionEnabled(MetricType.LATENCY)).thenReturn(true);
        when(queryInsightsService.getTopQueriesService(MetricType.LATENCY)).thenReturn(topQueriesService);

        // inject user info
        ThreadContext threadContext = new ThreadContext(settings);
        user = new User("user-1", List.of("role1", "role2"), List.of("role3", "role4"), List.of());
        InjectSecurity injector = new InjectSecurity("id", settings, threadContext);
        injector.injectUserInfo(user);
        threadContext.putTransient(QueryInsightsSettings.REQUEST_HEADER_REMOTE_ADDRESS, remoteAddress);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
    }

    public void testOnRequestEnd() {
        long timestamp = System.currentTimeMillis() - 100L;
        SearchType searchType = SearchType.QUERY_THEN_FETCH;

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.aggregation(new TermsAggregationBuilder("agg1").userValueTypeHint(ValueType.STRING).field("type.keyword"));
        searchSourceBuilder.size(0);

        String[] indices = new String[] { "index-1", "index-2" };

        Map<String, Long> phaseLatencyMap = new HashMap<>();
        phaseLatencyMap.put("expand", 0L);
        phaseLatencyMap.put("query", 20L);
        phaseLatencyMap.put("fetch", 1L);

        int numberOfShards = 10;

        QueryInsightsListener queryInsightsListener = new QueryInsightsListener(clusterService, queryInsightsService, threadPool);

        when(searchRequest.getOrCreateAbsoluteStartMillis()).thenReturn(timestamp);
        when(searchRequest.searchType()).thenReturn(searchType);
        when(searchRequest.source()).thenReturn(searchSourceBuilder);
        when(searchRequest.indices()).thenReturn(indices);
        when(searchRequestContext.phaseTookMap()).thenReturn(phaseLatencyMap);
        when(searchPhaseContext.getRequest()).thenReturn(searchRequest);
        when(searchPhaseContext.getNumShards()).thenReturn(numberOfShards);

        queryInsightsListener.onRequestEnd(searchPhaseContext, searchRequestContext);

        verify(queryInsightsService, times(1)).addRecord(any());
        ArgumentCaptor<SearchQueryRecord> argumentCaptor = ArgumentCaptor.forClass(SearchQueryRecord.class);
        verify(queryInsightsService).addRecord(argumentCaptor.capture());
        assertEquals(timestamp, argumentCaptor.getValue().getTimestamp());
        Map<Attribute, Object> attrs = argumentCaptor.getValue().getAttributes();
        assertEquals(searchType.toString().toLowerCase(Locale.ROOT), attrs.get(Attribute.SEARCH_TYPE));
        assertEquals(numberOfShards, attrs.get(Attribute.TOTAL_SHARDS));
        assertEquals(indices, attrs.get(Attribute.INDICES));
        assertEquals(phaseLatencyMap, attrs.get(Attribute.PHASE_LATENCY_MAP));
        assertEquals(user, attrs.get(Attribute.USER));
        assertEquals(remoteAddress, attrs.get(Attribute.REMOTE_ADDRESS));
    }

    public void testConcurrentOnRequestEnd() throws InterruptedException {
        Long timestamp = System.currentTimeMillis() - 100L;
        SearchType searchType = SearchType.QUERY_THEN_FETCH;

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.aggregation(new TermsAggregationBuilder("agg1").userValueTypeHint(ValueType.STRING).field("type.keyword"));
        searchSourceBuilder.size(0);

        String[] indices = new String[] { "index-1", "index-2" };

        Map<String, Long> phaseLatencyMap = new HashMap<>();
        phaseLatencyMap.put("expand", 0L);
        phaseLatencyMap.put("query", 20L);
        phaseLatencyMap.put("fetch", 1L);

        int numberOfShards = 10;

        final List<QueryInsightsListener> searchListenersList = new ArrayList<>();

        when(searchRequest.getOrCreateAbsoluteStartMillis()).thenReturn(timestamp);
        when(searchRequest.searchType()).thenReturn(searchType);
        when(searchRequest.source()).thenReturn(searchSourceBuilder);
        when(searchRequest.indices()).thenReturn(indices);
        when(searchRequestContext.phaseTookMap()).thenReturn(phaseLatencyMap);
        when(searchPhaseContext.getRequest()).thenReturn(searchRequest);
        when(searchPhaseContext.getNumShards()).thenReturn(numberOfShards);

        int numRequests = 50;
        Thread[] threads = new Thread[numRequests];
        Phaser phaser = new Phaser(numRequests + 1);
        CountDownLatch countDownLatch = new CountDownLatch(numRequests);

        for (int i = 0; i < numRequests; i++) {
            searchListenersList.add(new QueryInsightsListener(clusterService, queryInsightsService, threadPool));
        }

        for (int i = 0; i < numRequests; i++) {
            int finalI = i;
            threads[i] = new Thread(() -> {
                phaser.arriveAndAwaitAdvance();
                QueryInsightsListener thisListener = searchListenersList.get(finalI);
                thisListener.onRequestEnd(searchPhaseContext, searchRequestContext);
                countDownLatch.countDown();
            });
            threads[i].start();
        }
        phaser.arriveAndAwaitAdvance();
        countDownLatch.await();

        verify(queryInsightsService, times(numRequests)).addRecord(any());
    }

    public void testSetEnabled() {
        when(queryInsightsService.isCollectionEnabled(MetricType.LATENCY)).thenReturn(true);
        QueryInsightsListener queryInsightsListener = new QueryInsightsListener(clusterService, queryInsightsService, threadPool);
        queryInsightsListener.setEnableTopQueries(MetricType.LATENCY, true);
        assertTrue(queryInsightsListener.isEnabled());

        when(queryInsightsService.isCollectionEnabled(MetricType.LATENCY)).thenReturn(false);
        when(queryInsightsService.isCollectionEnabled(MetricType.CPU)).thenReturn(false);
        when(queryInsightsService.isCollectionEnabled(MetricType.JVM)).thenReturn(false);
        queryInsightsListener.setEnableTopQueries(MetricType.LATENCY, false);
        assertFalse(queryInsightsListener.isEnabled());
    }
}
