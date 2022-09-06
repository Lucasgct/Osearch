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

package org.opensearch.action.bulk;

import org.opensearch.common.Randomness;
import org.opensearch.common.unit.TimeValue;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Provides a backoff policy for bulk requests. Whenever a bulk request is rejected due to resource constraints (i.e. the client's internal
 * thread pool is full), the backoff policy decides how long the bulk processor will wait before the operation is retried internally.
 *
 * Notes for implementing custom subclasses:
 *
 * The underlying mathematical principle of <code>BackoffPolicy</code> are progressions which can be either finite or infinite although
 * the latter should not be used for retrying. A progression can be mapped to a <code>java.util.Iterator</code> with the following
 * semantics:
 *
 * <ul>
 *     <li><code>#hasNext()</code> determines whether the progression has more elements. Return <code>true</code> for infinite progressions
 *     </li>
 *     <li><code>#next()</code> determines the next element in the progression, i.e. the next wait time period</li>
 * </ul>
 *
 * Note that backoff policies are exposed as <code>Iterables</code> in order to be consumed multiple times.
 *
 * @opensearch.internal
 */
public abstract class BackoffPolicy implements Iterable<TimeValue> {
    private static final BackoffPolicy NO_BACKOFF = new NoBackoff();

    /**
     * Creates a backoff policy that will not allow any backoff, i.e. an operation will fail after the first attempt.
     *
     * @return A backoff policy without any backoff period. The returned instance is thread safe.
     */
    public static BackoffPolicy noBackoff() {
        return NO_BACKOFF;
    }

    /**
     * Creates an new constant backoff policy with the provided configuration.
     *
     * @param delay              The delay defines how long to wait between retry attempts. Must not be null.
     *                           Must be &lt;= <code>Integer.MAX_VALUE</code> ms.
     * @param maxNumberOfRetries The maximum number of retries. Must be a non-negative number.
     * @return A backoff policy with a constant wait time between retries. The returned instance is thread safe but each
     * iterator created from it should only be used by a single thread.
     */
    public static BackoffPolicy constantBackoff(TimeValue delay, int maxNumberOfRetries) {
        return new ConstantBackoff(checkDelay(delay), maxNumberOfRetries);
    }

    /**
     * Creates an new exponential backoff policy with a default configuration of 50 ms initial wait period and 8 retries taking
     * roughly 5.1 seconds in total.
     *
     * @return A backoff policy with an exponential increase in wait time for retries. The returned instance is thread safe but each
     * iterator created from it should only be used by a single thread.
     */
    public static BackoffPolicy exponentialBackoff() {
        return exponentialBackoff(TimeValue.timeValueMillis(50), 8);
    }

    /**
     * Creates an new exponential backoff policy with the provided configuration.
     *
     * @param initialDelay       The initial delay defines how long to wait for the first retry attempt. Must not be null.
     *                           Must be &lt;= <code>Integer.MAX_VALUE</code> ms.
     * @param maxNumberOfRetries The maximum number of retries. Must be a non-negative number.
     * @return A backoff policy with an exponential increase in wait time for retries. The returned instance is thread safe but each
     * iterator created from it should only be used by a single thread.
     */
    public static BackoffPolicy exponentialBackoff(TimeValue initialDelay, int maxNumberOfRetries) {
        return new ExponentialBackoff((int) checkDelay(initialDelay).millis(), maxNumberOfRetries);
    }

    /**
     *  It provides exponential backoff between retries until it reaches maxDelayForRetry.
     *  It uses equal jitter scheme as it is being used for throttled exceptions.
     *  It will make random distribution and also guarantees a minimum delay.
     *
     * @param baseDelay BaseDelay for exponential Backoff
     * @param maxDelayForRetry MaxDelay that can be returned from backoff policy
     * @return A backoff policy with exponential backoff with equal jitter which can't return delay more than given max delay
     */
    public static BackoffPolicy exponentialEqualJitterBackoff(int baseDelay, int maxDelayForRetry) {
        return new ExponentialEqualJitterBackoff(baseDelay, maxDelayForRetry);
    }

    /**
     *  It provides exponential backoff between retries until it reaches Integer.MAX_VALUE.
     *  It uses full jitter scheme for random distribution.
     *
     * @param baseDelay BaseDelay for exponential Backoff
     * @return A backoff policy with exponential backoff with full jitter.
     */
    public static BackoffPolicy exponentialFullJitterBackoff(long baseDelay) {
        return new ExponentialFullJitterBackoff(baseDelay);
    }

    /**
     * Wraps the backoff policy in one that calls a method every time a new backoff is taken from the policy.
     */
    public static BackoffPolicy wrap(BackoffPolicy delegate, Runnable onBackoff) {
        return new WrappedBackoffPolicy(delegate, onBackoff);
    }

    private static TimeValue checkDelay(TimeValue delay) {
        if (delay.millis() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("delay must be <= " + Integer.MAX_VALUE + " ms");
        }
        return delay;
    }

    /**
     * Concrete No Back Off Policy
     *
     * @opensearch.internal
     */
    private static class NoBackoff extends BackoffPolicy {
        @Override
        public Iterator<TimeValue> iterator() {
            return new Iterator<TimeValue>() {
                @Override
                public boolean hasNext() {
                    return false;
                }

                @Override
                public TimeValue next() {
                    throw new NoSuchElementException("No backoff");
                }
            };
        }
    }

    /**
     * Concrete Exponential Back Off Policy
     *
     * @opensearch.internal
     */
    private static class ExponentialBackoff extends BackoffPolicy {
        private final int start;

        private final int numberOfElements;

        private ExponentialBackoff(int start, int numberOfElements) {
            assert start >= 0;
            assert numberOfElements >= 0;
            this.start = start;
            this.numberOfElements = numberOfElements;
        }

        @Override
        public Iterator<TimeValue> iterator() {
            return new ExponentialBackoffIterator(start, numberOfElements);
        }
    }

    /**
     * Concrete Exponential Back Off Iterator
     *
     * @opensearch.internal
     */
    private static class ExponentialBackoffIterator implements Iterator<TimeValue> {
        private final int numberOfElements;

        private final int start;

        private int currentlyConsumed;

        private ExponentialBackoffIterator(int start, int numberOfElements) {
            this.start = start;
            this.numberOfElements = numberOfElements;
        }

        @Override
        public boolean hasNext() {
            return currentlyConsumed < numberOfElements;
        }

        @Override
        public TimeValue next() {
            if (!hasNext()) {
                throw new NoSuchElementException("Only up to " + numberOfElements + " elements");
            }
            int result = start + 10 * ((int) Math.exp(0.8d * (currentlyConsumed)) - 1);
            currentlyConsumed++;
            return TimeValue.timeValueMillis(result);
        }
    }

    private static class ExponentialEqualJitterBackoff extends BackoffPolicy {
        private final int maxDelayForRetry;
        private final int baseDelay;

        private ExponentialEqualJitterBackoff(int baseDelay, int maxDelayForRetry) {
            this.maxDelayForRetry = maxDelayForRetry;
            this.baseDelay = baseDelay;
        }

        @Override
        public Iterator<TimeValue> iterator() {
            return new ExponentialEqualJitterBackoffIterator(baseDelay, maxDelayForRetry);
        }
    }

    private static class ExponentialEqualJitterBackoffIterator implements Iterator<TimeValue> {
        /**
         * Retry limit to avoids integer overflow issues.
         * Post this limit, max delay will be returned with Equal Jitter.
         *
         * NOTE: If the value is greater than 30, there can be integer overflow
         * issues during delay calculation.
         **/
        private final int RETRIES_TILL_JITTER_INCREASE = 30;

        /**
         * Exponential increase in delay will happen till it reaches maxDelayForRetry.
         * Once delay has exceeded maxDelayForRetry, it will return maxDelayForRetry only
         * and not increase the delay.
         */
        private final int maxDelayForRetry;
        private final int baseDelay;
        private int retriesAttempted;

        private ExponentialEqualJitterBackoffIterator(int baseDelay, int maxDelayForRetry) {
            this.baseDelay = baseDelay;
            this.maxDelayForRetry = maxDelayForRetry;
        }

        /**
         * There is not any limit for this BackOff.
         * This Iterator will always return back off delay.
         *
         * @return true
         */
        @Override
        public boolean hasNext() {
            return true;
        }

        @Override
        public TimeValue next() {
            int retries = Math.min(retriesAttempted, RETRIES_TILL_JITTER_INCREASE);
            int exponentialDelay = (int) Math.min((1L << retries) * baseDelay, maxDelayForRetry);
            retriesAttempted++;
            return TimeValue.timeValueMillis((exponentialDelay / 2) + Randomness.get().nextInt(exponentialDelay / 2 + 1));
        }
    }

    private static class ExponentialFullJitterBackoff extends BackoffPolicy {
        private final long baseDelay;

        private ExponentialFullJitterBackoff(long baseDelay) {
            this.baseDelay = baseDelay;
        }

        @Override
        public Iterator<TimeValue> iterator() {
            return new ExponentialFullJitterBackoffIterator(baseDelay);
        }
    }

    private static class ExponentialFullJitterBackoffIterator implements Iterator<TimeValue> {
        /**
         * Current delay in exponential backoff
         */
        private long currentDelay;

        private ExponentialFullJitterBackoffIterator(long baseDelay) {
            this.currentDelay = baseDelay;
        }

        /**
         * There is not any limit for this BackOff.
         * This Iterator will always return back off delay.
         *
         * @return true
         */
        @Override
        public boolean hasNext() {
            return true;
        }

        @Override
        public TimeValue next() {
            TimeValue delayToReturn = TimeValue.timeValueMillis(Randomness.get().nextInt(Math.toIntExact(currentDelay)) + 1);
            currentDelay = Math.min(2 * currentDelay, Integer.MAX_VALUE);
            return delayToReturn;
        }
    }

    /**
     * Concrete Constant Back Off Policy
     *
     * @opensearch.internal
     */
    private static final class ConstantBackoff extends BackoffPolicy {
        private final TimeValue delay;

        private final int numberOfElements;

        ConstantBackoff(TimeValue delay, int numberOfElements) {
            assert numberOfElements >= 0;
            this.delay = delay;
            this.numberOfElements = numberOfElements;
        }

        @Override
        public Iterator<TimeValue> iterator() {
            return new ConstantBackoffIterator(delay, numberOfElements);
        }
    }

    /**
     * Concrete Constant Back Off Iterator
     *
     * @opensearch.internal
     */
    private static final class ConstantBackoffIterator implements Iterator<TimeValue> {
        private final TimeValue delay;
        private final int numberOfElements;
        private int curr;

        ConstantBackoffIterator(TimeValue delay, int numberOfElements) {
            this.delay = delay;
            this.numberOfElements = numberOfElements;
        }

        @Override
        public boolean hasNext() {
            return curr < numberOfElements;
        }

        @Override
        public TimeValue next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            curr++;
            return delay;
        }
    }

    /**
     * Concrete Wrapped Back Off Policy
     *
     * @opensearch.internal
     */
    private static final class WrappedBackoffPolicy extends BackoffPolicy {
        private final BackoffPolicy delegate;
        private final Runnable onBackoff;

        WrappedBackoffPolicy(BackoffPolicy delegate, Runnable onBackoff) {
            this.delegate = delegate;
            this.onBackoff = onBackoff;
        }

        @Override
        public Iterator<TimeValue> iterator() {
            return new WrappedBackoffIterator(delegate.iterator(), onBackoff);
        }
    }

    /**
     * Concrete Wrapped Back Off Iterator
     *
     * @opensearch.internal
     */
    private static final class WrappedBackoffIterator implements Iterator<TimeValue> {
        private final Iterator<TimeValue> delegate;
        private final Runnable onBackoff;

        WrappedBackoffIterator(Iterator<TimeValue> delegate, Runnable onBackoff) {
            this.delegate = delegate;
            this.onBackoff = onBackoff;
        }

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        public TimeValue next() {
            if (false == delegate.hasNext()) {
                throw new NoSuchElementException();
            }
            onBackoff.run();
            return delegate.next();
        }
    }
}
