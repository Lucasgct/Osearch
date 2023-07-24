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
 *    http://www.apache.org/licenses/LICENSE-2.0
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

package org.opensearch.index.search.stats;

import org.opensearch.Version;
import org.opensearch.action.search.SearchRequestStats;
import org.opensearch.common.Nullable;
import org.opensearch.core.common.Strings;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentFragment;
import org.opensearch.core.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Encapsulates stats for search time
 *
 * @opensearch.internal
 */
public class SearchStats implements Writeable, ToXContentFragment {

    /**
     * Statistics for search
     *
     * @opensearch.internal
     */

    public static class RequestStatsLongHolder {
        public long dfsPreQueryMetric;
        public long dfsPreQueryCurrent;
        public long dfsPreQueryTotal;
        public long canMatchMetric;
        public long canMatchCurrent;
        public long canMatchTotal;
        public long queryMetric;
        public long queryCurrent;
        public long queryTotal;
        public long fetchMetric;
        public long fetchCurrent;
        public long fetchTotal;
        public long expandSearchMetric;
        public long expandSearchCurrent;
        public long expandSearchTotal;
    }

    public static class Stats implements Writeable, ToXContentFragment {

        private long queryCount;
        private long queryTimeInMillis;
        private long queryCurrent;

        private long fetchCount;
        private long fetchTimeInMillis;
        private long fetchCurrent;

        private long scrollCount;
        private long scrollTimeInMillis;
        private long scrollCurrent;

        private long suggestCount;
        private long suggestTimeInMillis;
        private long suggestCurrent;

        private long pitCount;
        private long pitTimeInMillis;
        private long pitCurrent;

        @Nullable
        public RequestStatsLongHolder requestStatsLongHolder;

        public RequestStatsLongHolder getRequestStatsLongHolder() {
            if (requestStatsLongHolder == null) {
                return null;
            }
            return requestStatsLongHolder;
        }

        private Stats() {
            // for internal use, initializes all counts to 0
        }

        public Stats(
            long queryCount,
            long queryTimeInMillis,
            long queryCurrent,
            long fetchCount,
            long fetchTimeInMillis,
            long fetchCurrent,
            long scrollCount,
            long scrollTimeInMillis,
            long scrollCurrent,
            long pitCount,
            long pitTimeInMillis,
            long pitCurrent,
            long suggestCount,
            long suggestTimeInMillis,
            long suggestCurrent
        ) {
            this.requestStatsLongHolder = new RequestStatsLongHolder();
            this.queryCount = queryCount;
            this.queryTimeInMillis = queryTimeInMillis;
            this.queryCurrent = queryCurrent;

            this.fetchCount = fetchCount;
            this.fetchTimeInMillis = fetchTimeInMillis;
            this.fetchCurrent = fetchCurrent;

            this.scrollCount = scrollCount;
            this.scrollTimeInMillis = scrollTimeInMillis;
            this.scrollCurrent = scrollCurrent;

            this.suggestCount = suggestCount;
            this.suggestTimeInMillis = suggestTimeInMillis;
            this.suggestCurrent = suggestCurrent;

            this.pitCount = pitCount;
            this.pitTimeInMillis = pitTimeInMillis;
            this.pitCurrent = pitCurrent;
        }

        private Stats(StreamInput in) throws IOException {
            queryCount = in.readVLong();
            queryTimeInMillis = in.readVLong();
            queryCurrent = in.readVLong();

            fetchCount = in.readVLong();
            fetchTimeInMillis = in.readVLong();
            fetchCurrent = in.readVLong();

            scrollCount = in.readVLong();
            scrollTimeInMillis = in.readVLong();
            scrollCurrent = in.readVLong();

            suggestCount = in.readVLong();
            suggestTimeInMillis = in.readVLong();
            suggestCurrent = in.readVLong();

            if (in.getVersion().onOrAfter(Version.V_2_4_0)) {
                pitCount = in.readVLong();
                pitTimeInMillis = in.readVLong();
                pitCurrent = in.readVLong();
            }

            if (in.getVersion().onOrAfter(Version.V_3_0_0)) {
                this.requestStatsLongHolder = new RequestStatsLongHolder();
                requestStatsLongHolder.dfsPreQueryMetric = in.readVLong();
                requestStatsLongHolder.dfsPreQueryCurrent = in.readVLong();
                requestStatsLongHolder.dfsPreQueryTotal = in.readVLong();

                requestStatsLongHolder.canMatchMetric = in.readVLong();
                requestStatsLongHolder.canMatchCurrent = in.readVLong();
                requestStatsLongHolder.canMatchTotal = in.readVLong();

                requestStatsLongHolder.queryMetric = in.readVLong();
                requestStatsLongHolder.queryCurrent = in.readVLong();
                requestStatsLongHolder.queryTotal = in.readVLong();

                requestStatsLongHolder.fetchMetric = in.readVLong();
                requestStatsLongHolder.fetchCurrent = in.readVLong();
                requestStatsLongHolder.fetchTotal = in.readVLong();

                requestStatsLongHolder.expandSearchMetric = in.readVLong();
                requestStatsLongHolder.expandSearchCurrent = in.readVLong();
                requestStatsLongHolder.expandSearchTotal = in.readVLong();
            }
        }

        public void add(Stats stats) {
            queryCount += stats.queryCount;
            queryTimeInMillis += stats.queryTimeInMillis;
            queryCurrent += stats.queryCurrent;

            fetchCount += stats.fetchCount;
            fetchTimeInMillis += stats.fetchTimeInMillis;
            fetchCurrent += stats.fetchCurrent;

            scrollCount += stats.scrollCount;
            scrollTimeInMillis += stats.scrollTimeInMillis;
            scrollCurrent += stats.scrollCurrent;

            suggestCount += stats.suggestCount;
            suggestTimeInMillis += stats.suggestTimeInMillis;
            suggestCurrent += stats.suggestCurrent;

            pitCount += stats.pitCount;
            pitTimeInMillis += stats.pitTimeInMillis;
            pitCurrent += stats.pitCurrent;
        }

        public void addForClosingShard(Stats stats) {
            queryCount += stats.queryCount;
            queryTimeInMillis += stats.queryTimeInMillis;

            fetchCount += stats.fetchCount;
            fetchTimeInMillis += stats.fetchTimeInMillis;

            scrollCount += stats.scrollCount;
            scrollTimeInMillis += stats.scrollTimeInMillis;
            // need consider the count of the shard's current scroll
            scrollCount += stats.scrollCurrent;

            suggestCount += stats.suggestCount;
            suggestTimeInMillis += stats.suggestTimeInMillis;

            pitCount += stats.pitCount;
            pitTimeInMillis += stats.pitTimeInMillis;
            pitCurrent += stats.pitCurrent;
        }

        public long getQueryCount() {
            return queryCount;
        }

        public TimeValue getQueryTime() {
            return new TimeValue(queryTimeInMillis);
        }

        public long getQueryTimeInMillis() {
            return queryTimeInMillis;
        }

        public long getQueryCurrent() {
            return queryCurrent;
        }

        public long getFetchCount() {
            return fetchCount;
        }

        public TimeValue getFetchTime() {
            return new TimeValue(fetchTimeInMillis);
        }

        public long getFetchTimeInMillis() {
            return fetchTimeInMillis;
        }

        public long getFetchCurrent() {
            return fetchCurrent;
        }

        public long getScrollCount() {
            return scrollCount;
        }

        public TimeValue getScrollTime() {
            return new TimeValue(scrollTimeInMillis);
        }

        public long getScrollTimeInMillis() {
            return scrollTimeInMillis;
        }

        public long getScrollCurrent() {
            return scrollCurrent;
        }

        public long getPitCount() {
            return pitCount;
        }

        public TimeValue getPitTime() {
            return new TimeValue(pitTimeInMillis);
        }

        public long getPitTimeInMillis() {
            return pitTimeInMillis;
        }

        public long getPitCurrent() {
            return pitCurrent;
        }

        public long getSuggestCount() {
            return suggestCount;
        }

        public long getSuggestTimeInMillis() {
            return suggestTimeInMillis;
        }

        public TimeValue getSuggestTime() {
            return new TimeValue(suggestTimeInMillis);
        }

        public long getSuggestCurrent() {
            return suggestCurrent;
        }

        public static Stats readStats(StreamInput in) throws IOException {
            return new Stats(in);
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeVLong(queryCount);
            out.writeVLong(queryTimeInMillis);
            out.writeVLong(queryCurrent);

            out.writeVLong(fetchCount);
            out.writeVLong(fetchTimeInMillis);
            out.writeVLong(fetchCurrent);

            out.writeVLong(scrollCount);
            out.writeVLong(scrollTimeInMillis);
            out.writeVLong(scrollCurrent);

            out.writeVLong(suggestCount);
            out.writeVLong(suggestTimeInMillis);
            out.writeVLong(suggestCurrent);

            if (out.getVersion().onOrAfter(Version.V_2_4_0)) {
                out.writeVLong(pitCount);
                out.writeVLong(pitTimeInMillis);
                out.writeVLong(pitCurrent);
            }

            if (out.getVersion().onOrAfter(Version.V_3_0_0)) {
                out.writeVLong(requestStatsLongHolder.dfsPreQueryMetric);
                out.writeVLong(requestStatsLongHolder.dfsPreQueryCurrent);
                out.writeVLong(requestStatsLongHolder.dfsPreQueryTotal);

                out.writeVLong(requestStatsLongHolder.canMatchMetric);
                out.writeVLong(requestStatsLongHolder.canMatchCurrent);
                out.writeVLong(requestStatsLongHolder.canMatchTotal);

                out.writeVLong(requestStatsLongHolder.queryMetric);
                out.writeVLong(requestStatsLongHolder.queryCurrent);
                out.writeVLong(requestStatsLongHolder.queryTotal);

                out.writeVLong(requestStatsLongHolder.fetchMetric);
                out.writeVLong(requestStatsLongHolder.fetchCurrent);
                out.writeVLong(requestStatsLongHolder.fetchTotal);

                out.writeVLong(requestStatsLongHolder.expandSearchMetric);
                out.writeVLong(requestStatsLongHolder.expandSearchCurrent);
                out.writeVLong(requestStatsLongHolder.expandSearchTotal);
            }
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            builder.field(Fields.QUERY_TOTAL, queryCount);
            builder.humanReadableField(Fields.QUERY_TIME_IN_MILLIS, Fields.QUERY_TIME, getQueryTime());
            builder.field(Fields.QUERY_CURRENT, queryCurrent);

            builder.field(Fields.FETCH_TOTAL, fetchCount);
            builder.humanReadableField(Fields.FETCH_TIME_IN_MILLIS, Fields.FETCH_TIME, getFetchTime());
            builder.field(Fields.FETCH_CURRENT, fetchCurrent);

            builder.field(Fields.SCROLL_TOTAL, scrollCount);
            builder.humanReadableField(Fields.SCROLL_TIME_IN_MILLIS, Fields.SCROLL_TIME, getScrollTime());
            builder.field(Fields.SCROLL_CURRENT, scrollCurrent);

            builder.field(Fields.PIT_TOTAL, pitCount);
            builder.humanReadableField(Fields.PIT_TIME_IN_MILLIS, Fields.PIT_TIME, getPitTime());
            builder.field(Fields.PIT_CURRENT, pitCurrent);

            builder.field(Fields.SUGGEST_TOTAL, suggestCount);
            builder.humanReadableField(Fields.SUGGEST_TIME_IN_MILLIS, Fields.SUGGEST_TIME, getSuggestTime());
            builder.field(Fields.SUGGEST_CURRENT, suggestCurrent);

            if (requestStatsLongHolder != null) {
                builder.startObject(Fields.REQUEST);

                builder.humanReadableField(
                    Fields.DFS_PREQUERY_TIME_IN_MILLIS,
                    Fields.QUERY_TIME,
                    new TimeValue(requestStatsLongHolder.dfsPreQueryMetric)
                );
                builder.field(Fields.DFS_PREQUERY_CURRENT, requestStatsLongHolder.dfsPreQueryCurrent);
                builder.field(Fields.DFS_PREQUERY_TOTAL, requestStatsLongHolder.dfsPreQueryTotal);

                builder.humanReadableField(
                    Fields.CANMATCH_TIME_IN_MILLIS,
                    Fields.QUERY_TIME,
                    new TimeValue(requestStatsLongHolder.canMatchMetric)
                );
                builder.field(Fields.CANMATCH_CURRENT, requestStatsLongHolder.canMatchCurrent);
                builder.field(Fields.CANMATCH_TOTAL, requestStatsLongHolder.canMatchTotal);

                builder.humanReadableField(
                    Fields.QUERY_TIME_IN_MILLIS,
                    Fields.QUERY_TIME,
                    new TimeValue(requestStatsLongHolder.queryMetric)
                );
                builder.field(Fields.QUERY_CURRENT, requestStatsLongHolder.queryCurrent);
                builder.field(Fields.QUERY_TOTAL, requestStatsLongHolder.queryTotal);

                builder.humanReadableField(
                    Fields.FETCH_TIME_IN_MILLIS,
                    Fields.FETCH_TIME,
                    new TimeValue(requestStatsLongHolder.fetchMetric)
                );
                builder.field(Fields.FETCH_CURRENT, requestStatsLongHolder.fetchCurrent);
                builder.field(Fields.FETCH_TOTAL, requestStatsLongHolder.fetchTotal);

                builder.humanReadableField(
                    Fields.EXPAND_TIME_IN_MILLIS,
                    Fields.FETCH_TIME,
                    new TimeValue(requestStatsLongHolder.expandSearchMetric)
                );
                builder.field(Fields.EXPAND_CURRENT, requestStatsLongHolder.expandSearchCurrent);
                builder.field(Fields.EXPAND_TOTAL, requestStatsLongHolder.expandSearchTotal);

                builder.endObject();
            }
            return builder;
        }
    }

    private final Stats totalStats;
    private long openContexts;

    @Nullable
    private Map<String, Stats> groupStats;

    public SearchStats() {
        totalStats = new Stats();
    }

    // Set the different Request Stats fields in here
    public void setSearchRequestStats(SearchRequestStats searchRequestStats) {
        if (totalStats.requestStatsLongHolder == null) {
            totalStats.requestStatsLongHolder = new RequestStatsLongHolder();
        }
        totalStats.requestStatsLongHolder.dfsPreQueryMetric = searchRequestStats.getDFSPreQueryMetric();
        totalStats.requestStatsLongHolder.dfsPreQueryCurrent = searchRequestStats.getDFSPreQueryCurrent();
        totalStats.requestStatsLongHolder.dfsPreQueryTotal = searchRequestStats.getDFSPreQueryTotal();

        totalStats.requestStatsLongHolder.canMatchMetric = searchRequestStats.getCanMatchMetric();
        totalStats.requestStatsLongHolder.canMatchCurrent = searchRequestStats.getCanMatchCurrent();
        totalStats.requestStatsLongHolder.canMatchTotal = searchRequestStats.getCanMatchTotal();

        totalStats.requestStatsLongHolder.queryMetric = searchRequestStats.getQueryMetric();
        totalStats.requestStatsLongHolder.queryCurrent = searchRequestStats.getQueryCurrent();
        totalStats.requestStatsLongHolder.queryTotal = searchRequestStats.getQueryTotal();

        totalStats.requestStatsLongHolder.fetchMetric = searchRequestStats.getFetchMetric();
        totalStats.requestStatsLongHolder.fetchCurrent = searchRequestStats.getFetchCurrent();
        totalStats.requestStatsLongHolder.fetchTotal = searchRequestStats.getFetchTotal();

        totalStats.requestStatsLongHolder.expandSearchMetric = searchRequestStats.getExpandSearchMetric();
        totalStats.requestStatsLongHolder.expandSearchCurrent = searchRequestStats.getExpandSearchCurrent();
        totalStats.requestStatsLongHolder.expandSearchTotal = searchRequestStats.getExpandSearchTotal();
    }

    public SearchStats(Stats totalStats, long openContexts, @Nullable Map<String, Stats> groupStats) {
        this.totalStats = totalStats;
        this.openContexts = openContexts;
        this.groupStats = groupStats;
    }

    public SearchStats(StreamInput in) throws IOException {
        totalStats = Stats.readStats(in);
        openContexts = in.readVLong();
        if (in.readBoolean()) {
            groupStats = in.readMap(StreamInput::readString, Stats::readStats);
        }
    }

    public void add(SearchStats searchStats) {
        if (searchStats == null) {
            return;
        }
        addTotals(searchStats);
        openContexts += searchStats.openContexts;
        if (searchStats.groupStats != null && !searchStats.groupStats.isEmpty()) {
            if (groupStats == null) {
                groupStats = new HashMap<>(searchStats.groupStats.size());
            }
            for (Map.Entry<String, Stats> entry : searchStats.groupStats.entrySet()) {
                groupStats.putIfAbsent(entry.getKey(), new Stats());
                groupStats.get(entry.getKey()).add(entry.getValue());
            }
        }
    }

    public void addTotals(SearchStats searchStats) {
        if (searchStats == null) {
            return;
        }
        totalStats.add(searchStats.totalStats);
    }

    public void addTotalsForClosingShard(SearchStats searchStats) {
        if (searchStats == null) {
            return;
        }
        totalStats.addForClosingShard(searchStats.totalStats);
    }

    public Stats getTotal() {
        return this.totalStats;
    }

    public long getOpenContexts() {
        return this.openContexts;
    }

    @Nullable
    public Map<String, Stats> getGroupStats() {
        return this.groupStats != null ? Collections.unmodifiableMap(this.groupStats) : null;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject(Fields.SEARCH);
        builder.field(Fields.OPEN_CONTEXTS, openContexts);
        totalStats.toXContent(builder, params);
        if (groupStats != null && !groupStats.isEmpty()) {
            builder.startObject(Fields.GROUPS);
            for (Map.Entry<String, Stats> entry : groupStats.entrySet()) {
                builder.startObject(entry.getKey());
                entry.getValue().toXContent(builder, params);
                builder.endObject();
            }
            builder.endObject();
        }
        builder.endObject();
        return builder;
    }

    @Override
    public String toString() {
        return Strings.toString(XContentType.JSON, this, true, true);
    }

    /**
     * Fields for search statistics
     *
     * @opensearch.internal
     */
    static final class Fields {
        static final String SEARCH = "search";
        static final String OPEN_CONTEXTS = "open_contexts";
        static final String GROUPS = "groups";
        static final String QUERY_TOTAL = "query_total";
        static final String QUERY_TIME = "query_time";
        static final String QUERY_TIME_IN_MILLIS = "query_time_in_millis";
        static final String QUERY_CURRENT = "query_current";
        static final String FETCH_TOTAL = "fetch_total";
        static final String FETCH_TIME = "fetch_time";
        static final String FETCH_TIME_IN_MILLIS = "fetch_time_in_millis";
        static final String FETCH_CURRENT = "fetch_current";
        static final String SCROLL_TOTAL = "scroll_total";
        static final String SCROLL_TIME = "scroll_time";
        static final String SCROLL_TIME_IN_MILLIS = "scroll_time_in_millis";
        static final String SCROLL_CURRENT = "scroll_current";
        static final String PIT_TOTAL = "point_in_time_total";
        static final String PIT_TIME = "point_in_time_time";
        static final String PIT_TIME_IN_MILLIS = "point_in_time_time_in_millis";
        static final String PIT_CURRENT = "point_in_time_current";
        static final String SUGGEST_TOTAL = "suggest_total";
        static final String SUGGEST_TIME = "suggest_time";
        static final String SUGGEST_TIME_IN_MILLIS = "suggest_time_in_millis";
        static final String SUGGEST_CURRENT = "suggest_current";
        static final String REQUEST = "request";
        static final String DFS_PREQUERY_TIME_IN_MILLIS = "dfs_prequery_time_in_millis";
        static final String DFS_PREQUERY_CURRENT = "dfs_prequery_current";
        static final String DFS_PREQUERY_TOTAL = "dfs_prequery_total";
        static final String CANMATCH_TIME_IN_MILLIS = "canmatch_time_in_millis";
        static final String CANMATCH_CURRENT = "canmatch_current";
        static final String CANMATCH_TOTAL = "canmatch_total";
        static final String EXPAND_TIME_IN_MILLIS = "expand_time_in_millis";
        static final String EXPAND_CURRENT = "expand_current";
        static final String EXPAND_TOTAL = "expand_total";
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        totalStats.writeTo(out);
        out.writeVLong(openContexts);
        if (groupStats == null || groupStats.isEmpty()) {
            out.writeBoolean(false);
        } else {
            out.writeBoolean(true);
            out.writeMap(groupStats, StreamOutput::writeString, (stream, stats) -> stats.writeTo(stream));
        }
    }
}
