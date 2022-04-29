/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.index;

import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.transport.TransportResponse;

import java.io.IOException;

public class IndicesModuleNameResponse extends TransportResponse {
    private boolean requestAck;

    public IndicesModuleNameResponse(StreamInput in) throws IOException {
        this.requestAck = in.readBoolean();
    }

    public IndicesModuleNameResponse(Boolean requestAck) {
        this.requestAck = requestAck;
    }

    public void IndicesModuleNameResponse(StreamInput in) throws IOException {
        this.requestAck = in.readBoolean();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeBoolean(requestAck);
    }

}
