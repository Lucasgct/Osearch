/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.discovery;

import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.extensions.DiscoveryExtension;
import org.opensearch.transport.TransportRequest;

import java.io.IOException;
import java.util.Objects;

/**
 * InitializeExtensionsRequest to initialize extension
 *
 * @opensearch.internal
 */
public class InitializeExtensionsRequest extends TransportRequest {
    private final DiscoveryNode sourceNode;
    private final DiscoveryExtension extension;

    public InitializeExtensionsRequest(DiscoveryNode sourceNode, DiscoveryExtension extension) {
        this.sourceNode = sourceNode;
        this.extension = extension;
    }

    public InitializeExtensionsRequest(StreamInput in) throws IOException {
        super(in);
        sourceNode = new DiscoveryNode(in);
        extension = new DiscoveryExtension(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        sourceNode.writeTo(out);
        extension.writeTo(out);
    }

    public DiscoveryNode getSourceNode() {
        return sourceNode;
    }

    public DiscoveryExtension getExtension() {
        return extension;
    }

    @Override
    public String toString() {
        return "InitializeExtensionsRequest{" + "sourceNode=" + sourceNode + ", extension=" + extension + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InitializeExtensionsRequest that = (InitializeExtensionsRequest) o;
        return Objects.equals(sourceNode, that.sourceNode) && Objects.equals(extension, that.extension);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceNode, extension);
    }
}
