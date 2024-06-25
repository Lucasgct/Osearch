/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.cluster.metadata;

import org.opensearch.common.ResourceType;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.test.AbstractSerializingTestCase;
import org.joda.time.Instant;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QueryGroupTests extends AbstractSerializingTestCase<QueryGroup> {

    private static final List<QueryGroup.QueryGroupMode> allowedModes = List.of(
        QueryGroup.QueryGroupMode.SOFT,
        QueryGroup.QueryGroupMode.ENFORCED,
        QueryGroup.QueryGroupMode.MONITOR
    );

    static QueryGroup createRandomQueryGroup(String _id) {
        String name = randomAlphaOfLength(10);
        Map<ResourceType, Object> resourceLimit = new HashMap<>();
        resourceLimit.put(ResourceType.HEAP_ALLOCATIONS, randomDoubleBetween(0.0, 0.80, false));
        return new QueryGroup(name, _id, randomMode(), resourceLimit, Instant.now().getMillis());
    }

    private static QueryGroup.QueryGroupMode randomMode() {
        return allowedModes.get(randomIntBetween(0, allowedModes.size() - 1));
    }

    /**
     * Parses to a new instance using the provided {@link XContentParser}
     *
     * @param parser
     */
    @Override
    protected QueryGroup doParseInstance(XContentParser parser) throws IOException {
        return QueryGroup.fromXContent(parser);
    }

    /**
     * Returns a {@link Writeable.Reader} that can be used to de-serialize the instance
     */
    @Override
    protected Writeable.Reader<QueryGroup> instanceReader() {
        return QueryGroup::new;
    }

    /**
     * Creates a random test instance to use in the tests. This method will be
     * called multiple times during test execution and should return a different
     * random instance each time it is called.
     */
    @Override
    protected QueryGroup createTestInstance() {
        return createRandomQueryGroup("1232sfraeradf_");
    }

    public void testNullName() {
        assertThrows(
            NullPointerException.class,
            () -> new QueryGroup(null, "_id", randomMode(), Collections.emptyMap(), Instant.now().getMillis())
        );
    }

    public void testNullId() {
        assertThrows(
            NullPointerException.class,
            () -> new QueryGroup("Dummy", null, randomMode(), Collections.emptyMap(), Instant.now().getMillis())
        );
    }

    public void testNullResourceLimits() {
        assertThrows(NullPointerException.class, () -> new QueryGroup("analytics", "_id", randomMode(), null, Instant.now().getMillis()));
    }

    public void testEmptyResourceLimits() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new QueryGroup("analytics", "_id", randomMode(), Collections.emptyMap(), Instant.now().getMillis())
        );
    }

    public void testIllegalQueryGroupMode() {
        assertThrows(
            NullPointerException.class,
            () -> new QueryGroup("analytics", "_id", null, Map.of(ResourceType.HEAP_ALLOCATIONS, (Object) 0.4), Instant.now().getMillis())
        );
    }

    public void testInvalidResourceLimitWhenInvalidSystemResourceValueIsGiven() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new QueryGroup(
                "analytics",
                "_id",
                randomMode(),
                Map.of(ResourceType.HEAP_ALLOCATIONS, (Object) randomDoubleBetween(1.1, 1.8, false)),
                Instant.now().getMillis()
            )
        );
    }

    public void testValidQueryGroup() {
        QueryGroup queryGroup = new QueryGroup(
            "analytics",
            "_id",
            randomMode(),
            Map.of(ResourceType.HEAP_ALLOCATIONS, randomDoubleBetween(0.01, 0.8, false)),
            Instant.ofEpochMilli(1717187289).getMillis()
        );

        assertNotNull(queryGroup.getName());
        assertEquals("analytics", queryGroup.getName());
        assertNotNull(queryGroup.getResourceLimits());
        assertFalse(queryGroup.getResourceLimits().isEmpty());
        assertEquals(1, queryGroup.getResourceLimits().size());
        assertTrue(allowedModes.contains(queryGroup.getResiliencyMode()));
        assertEquals(1717187289, queryGroup.getUpdatedAtInMillis());
    }
}