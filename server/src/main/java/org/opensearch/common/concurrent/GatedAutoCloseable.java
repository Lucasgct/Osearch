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

package org.opensearch.common.concurrent;

/**
 * Decorator class that wraps an object reference with a {@link Runnable} that is
 * invoked when {@link #close()} is called. The internal {@link OneWayGate} instance ensures
 * that this is invoked only once. See also {@link GatedCloseable}
 */
public class GatedAutoCloseable<T> implements AutoCloseable {

    private final T ref;
    private final Runnable onClose;
    private final OneWayGate gate;

    public GatedAutoCloseable(T ref, Runnable onClose) {
        this.ref = ref;
        this.onClose = onClose;
        gate = new OneWayGate();
    }

    public T get() {
        return ref;
    }

    @Override
    public void close() {
        if (gate.close()) {
            onClose.run();
        }
    }
}
