/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.cluster.coordination;

import org.opensearch.OpenSearchException;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.cluster.shutdown.NodeDecommissionService;

import java.io.IOException;

/**
 * This exception is thrown if the node is decommissioned by @{@link NodeDecommissionService}
 * and this nodes needs to be removed from the cluster
 *
 * @opensearch.internal
 */

public class NodeDecommissionedException extends OpenSearchException {

    public NodeDecommissionedException(String msg, Object... args) {
        super(msg, args);
    }

    public NodeDecommissionedException(StreamInput in) throws IOException {
        super(in);
    }
}
