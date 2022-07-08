/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.index.translog;

import org.apache.lucene.store.AlreadyClosedException;
import org.opensearch.common.util.concurrent.ReleasableLock;
import org.opensearch.index.engine.EngineConfig;
import org.opensearch.index.seqno.LocalCheckpointTracker;
import org.opensearch.index.shard.ShardId;
import org.opensearch.index.translog.listener.TranslogEventListener;

import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/***
 * The implementation of {@link TranslogManager} that only orchestrates writes to the underlying {@link Translog}
 *
 * @opensearch.internal
 */
public class WriteOnlyTranslogManager extends InternalTranslogManager {

    public WriteOnlyTranslogManager(
        EngineConfig engineConfig,
        ShardId shardId,
        ReleasableLock readLock,
        Supplier<LocalCheckpointTracker> localCheckpointTrackerSupplier,
        String translogUUID,
        TranslogEventListener translogEventListener,
        Runnable ensureOpen,
        BiConsumer<String, Exception> failEngine,
        Function<AlreadyClosedException, Boolean> failOnTragicEvent
    ) throws IOException {
        super(
            engineConfig,
            shardId,
            readLock,
            localCheckpointTrackerSupplier,
            translogUUID,
            translogEventListener,
            ensureOpen,
            failEngine,
            failOnTragicEvent
        );
    }

    @Override
    public int restoreLocalHistoryFromTranslog(long processedCheckpoint, TranslogRecoveryRunner translogRecoveryRunner) throws IOException {
        return 0;
    }

    @Override
    public int recoverFromTranslog(TranslogRecoveryRunner translogRecoveryRunner, long localCheckpoint, long recoverUpToSeqNo)
        throws IOException {
        throw new UnsupportedOperationException("Read only replicas do not have an IndexWriter and cannot recover from a translog.");
    }

    @Override
    public void skipTranslogRecovery() {
        // Do nothing.
    }
}
