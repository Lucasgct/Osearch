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

package org.opensearch.geo.search.aggregations.bucket.geogrid;

import org.opensearch.common.geo.GeoPoint;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.geometry.utils.Geohash;
import org.opensearch.search.aggregations.InternalAggregations;

import java.io.IOException;

/**
 * Implementation of geohash grid bucket
 *
 * @opensearch.internal
 */
public class InternalGeoHashGridBucket extends BaseGeoGridBucket<InternalGeoHashGridBucket> {
    InternalGeoHashGridBucket(long hashAsLong, long docCount, InternalAggregations aggregations) {
        super(hashAsLong, docCount, aggregations);
    }

    /**
     * Read from a stream.
     */
    public InternalGeoHashGridBucket(StreamInput in) throws IOException {
        super(in);
    }

    @Override
    public String getKeyAsString() {
        return Geohash.stringEncode(hashAsLong);
    }

    @Override
    public GeoPoint getKey() {
        return GeoPoint.fromGeohash(hashAsLong);
    }
}
