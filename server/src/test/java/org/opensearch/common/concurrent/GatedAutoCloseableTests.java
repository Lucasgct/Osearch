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

import org.junit.Before;
import org.opensearch.test.OpenSearchTestCase;

import java.util.concurrent.atomic.AtomicInteger;

public class GatedAutoCloseableTests extends OpenSearchTestCase {

    private AtomicInteger testRef;
    private GatedAutoCloseable<AtomicInteger> testObject;

    @Before
    public void setup() {
        testRef = new AtomicInteger(0);
        testObject = new GatedAutoCloseable<>(testRef, testRef::incrementAndGet);
    }

    public void testGet() {
        assertEquals(0, testObject.get().get());
    }

    public void testClose() {
        testObject.close();
        assertEquals(1, testObject.get().get());
    }

    public void testIdempotent() {
        testObject.close();
        testObject.close();
        assertEquals(1, testObject.get().get());
    }
}
