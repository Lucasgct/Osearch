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

package org.opensearch.action.admin.cluster.shards;

import org.opensearch.cluster.routing.ShardRouting;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.io.stream.Writeable;
import org.opensearch.common.xcontent.ToXContentObject;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.index.shard.ShardId;

import java.io.IOException;

public class ClusterSearchShardsGroup implements Writeable, ToXContentObject {

    private final ShardId shardId;
    private final ShardRouting[] shards;

    public ClusterSearchShardsGroup(ShardId shardId, ShardRouting[] shards) {
        this.shardId = shardId;
        this.shards = shards;
    }

    ClusterSearchShardsGroup(StreamInput in) throws IOException {
        shardId = new ShardId(in);
        shards = in.readArray(i -> new ShardRouting(shardId, i), ShardRouting[]::new);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        shardId.writeTo(out);
        out.writeArray((o, s) -> s.writeToThin(o), shards);
    }

    public ShardId getShardId() {
        return shardId;
    }

    public ShardRouting[] getShards() {
        return shards;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startArray();
        for (ShardRouting shard : getShards()) {
            shard.toXContent(builder, params);
        }
        builder.endArray();
        return builder;
    }
}
