/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.common.util.concurrent;

import org.opensearch.common.annotation.InternalApi;
import org.opensearch.threadpool.ThreadPool;

/**
 * InternalContextSwitcher is an internal class used to switch into a fresh
 * internal system context
 *
 * @opensearch.internal
 */
@InternalApi
public class InternalContextSwitcher {
    private final ThreadPool threadPool;

    public InternalContextSwitcher(ThreadPool threadPool) {
        this.threadPool = threadPool;
    }

    public ThreadContext.StoredContext switchContext() {
        return threadPool.getThreadContext().stashContext();
    }
}
