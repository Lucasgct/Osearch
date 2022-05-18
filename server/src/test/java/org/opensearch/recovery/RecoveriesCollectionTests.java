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

package org.opensearch.recovery;

import org.opensearch.OpenSearchException;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.index.replication.OpenSearchIndexLevelReplicationTestCase;
import org.opensearch.index.shard.IndexShard;
import org.opensearch.index.shard.ShardId;
import org.opensearch.index.store.Store;
import org.opensearch.indices.replication.common.ShardTargetCollection;
import org.opensearch.indices.replication.common.ShardTargetListener;
import org.opensearch.indices.replication.common.ShardTargetState;
import org.opensearch.indices.recovery.RecoveryState;
import org.opensearch.indices.recovery.RecoveryTarget;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThan;

public class RecoveriesCollectionTests extends OpenSearchIndexLevelReplicationTestCase {
    static final ShardTargetListener listener = new ShardTargetListener() {
        @Override
        public void onDone(ShardTargetState state) {

        }

        @Override
        public void onFailure(ShardTargetState state, OpenSearchException e, boolean sendShardFailure) {

        }
    };

    public void testLastAccessTimeUpdate() throws Exception {
        try (ReplicationGroup shards = createGroup(0)) {
            final ShardTargetCollection<RecoveryTarget> collection = new ShardTargetCollection<>(logger, threadPool);
            final long recoveryId = startRecovery(collection, shards.getPrimaryNode(), shards.addReplica());
            try (ShardTargetCollection.ShardTargetRef<RecoveryTarget> status = collection.get(recoveryId)) {
                final long lastSeenTime = status.get().lastAccessTime();
                assertBusy(() -> {
                    try (ShardTargetCollection.ShardTargetRef<RecoveryTarget> currentStatus = collection.get(recoveryId)) {
                        assertThat("access time failed to update", lastSeenTime, lessThan(currentStatus.get().lastAccessTime()));
                    }
                });
            } finally {
                collection.cancel(recoveryId, "life");
            }
        }
    }

    public void testRecoveryTimeout() throws Exception {
        try (ReplicationGroup shards = createGroup(0)) {
            final ShardTargetCollection<RecoveryTarget> collection = new ShardTargetCollection<>(logger, threadPool);
            final AtomicBoolean failed = new AtomicBoolean();
            final CountDownLatch latch = new CountDownLatch(1);
            final long recoveryId = startRecovery(collection, shards.getPrimaryNode(), shards.addReplica(), new ShardTargetListener() {
                @Override
                public void onDone(ShardTargetState state) {
                    latch.countDown();
                }

                @Override
                public void onFailure(ShardTargetState state, OpenSearchException e, boolean sendShardFailure) {
                    failed.set(true);
                    latch.countDown();
                }
            }, TimeValue.timeValueMillis(100));
            try {
                latch.await(30, TimeUnit.SECONDS);
                assertTrue("recovery failed to timeout", failed.get());
            } finally {
                collection.cancel(recoveryId, "meh");
            }
        }

    }

    public void testRecoveryCancellation() throws Exception {
        try (ReplicationGroup shards = createGroup(0)) {
            final ShardTargetCollection<RecoveryTarget> collection = new ShardTargetCollection<>(logger, threadPool);
            final long recoveryId = startRecovery(collection, shards.getPrimaryNode(), shards.addReplica());
            final long recoveryId2 = startRecovery(collection, shards.getPrimaryNode(), shards.addReplica());
            try (ShardTargetCollection.ShardTargetRef<RecoveryTarget> recoveryRef = collection.get(recoveryId)) {
                ShardId shardId = recoveryRef.get().indexShard().shardId();
                assertTrue("failed to cancel recoveries", collection.cancelForShard(shardId, "test"));
                assertThat("all recoveries should be cancelled", collection.size(), equalTo(0));
            } finally {
                collection.cancel(recoveryId, "meh");
                collection.cancel(recoveryId2, "meh");
            }
        }
    }

    public void testResetRecovery() throws Exception {
        try (ReplicationGroup shards = createGroup(0)) {
            shards.startAll();
            int numDocs = randomIntBetween(1, 15);
            shards.indexDocs(numDocs);
            final ShardTargetCollection<RecoveryTarget> collection = new ShardTargetCollection<>(logger, threadPool);
            IndexShard shard = shards.addReplica();
            final long recoveryId = startRecovery(collection, shards.getPrimaryNode(), shard);
            RecoveryTarget recoveryTarget = collection.getTarget(recoveryId);
            final int currentAsTarget = shard.recoveryStats().currentAsTarget();
            final int referencesToStore = recoveryTarget.store().refCount();
            IndexShard indexShard = recoveryTarget.indexShard();
            Store store = recoveryTarget.store();
            String tempFileName = recoveryTarget.getTempNameForFile("foobar");
            RecoveryTarget resetRecovery = collection.resetRecovery(recoveryId, TimeValue.timeValueMinutes(60));
            final long resetRecoveryId = resetRecovery.getId();
            assertNotSame(recoveryTarget, resetRecovery);
            assertNotSame(recoveryTarget.cancellableThreads(), resetRecovery.cancellableThreads());
            assertSame(indexShard, resetRecovery.indexShard());
            assertSame(store, resetRecovery.store());
            assertEquals(referencesToStore, resetRecovery.store().refCount());
            assertEquals(currentAsTarget, shard.recoveryStats().currentAsTarget());
            assertEquals(recoveryTarget.refCount(), 0);
            expectThrows(OpenSearchException.class, () -> recoveryTarget.store());
            expectThrows(OpenSearchException.class, () -> recoveryTarget.indexShard());
            String resetTempFileName = resetRecovery.getTempNameForFile("foobar");
            assertNotEquals(tempFileName, resetTempFileName);
            assertEquals(currentAsTarget, shard.recoveryStats().currentAsTarget());
            try (ShardTargetCollection.ShardTargetRef<RecoveryTarget> newRecoveryRef = collection.get(resetRecoveryId)) {
                shards.recoverReplica(shard, (s, n) -> {
                    assertSame(s, newRecoveryRef.get().indexShard());
                    return newRecoveryRef.get();
                }, false);
            }
            shards.assertAllEqual(numDocs);
            assertNull("recovery is done", collection.get(recoveryId));
        }
    }

    long startRecovery(ShardTargetCollection<RecoveryTarget> collection, DiscoveryNode sourceNode, IndexShard shard) {
        return startRecovery(collection, sourceNode, shard, listener, TimeValue.timeValueMinutes(60));
    }

    long startRecovery(
        ShardTargetCollection<RecoveryTarget> collection,
        DiscoveryNode sourceNode,
        IndexShard indexShard,
        ShardTargetListener listener,
        TimeValue timeValue
    ) {
        final DiscoveryNode rNode = getDiscoveryNode(indexShard.routingEntry().currentNodeId());
        indexShard.markAsRecovering("remote", new RecoveryState(indexShard.routingEntry(), sourceNode, rNode));
        indexShard.prepareForIndexRecovery();
        return collection.start(new RecoveryTarget(indexShard, sourceNode, listener), timeValue);
    }
}
