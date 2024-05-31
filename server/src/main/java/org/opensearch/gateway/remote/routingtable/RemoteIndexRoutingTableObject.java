/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.gateway.remote.routingtable;

import org.opensearch.cluster.routing.IndexRoutingTable;
import org.opensearch.cluster.routing.IndexShardRoutingTable;
import org.opensearch.core.common.io.stream.BufferedChecksumStreamInput;
import org.opensearch.core.common.io.stream.BufferedChecksumStreamOutput;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.index.Index;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Remote store object for IndexRoutingTable
 */
public class RemoteIndexRoutingTableObject implements Writeable {

    private final IndexRoutingTable indexRoutingTable;

    public RemoteIndexRoutingTableObject(IndexRoutingTable indexRoutingTable) {
        this.indexRoutingTable = indexRoutingTable;
    }

    public RemoteIndexRoutingTableObject(InputStream inputStream, Index index) throws IOException {
        try {
            try (BufferedChecksumStreamInput in = new BufferedChecksumStreamInput(new InputStreamStreamInput(inputStream), "assertion")) {
                // Read the Table Header first and confirm the index
                IndexRoutingTableHeader indexRoutingTableHeader = new IndexRoutingTableHeader(in);
                assert indexRoutingTableHeader.getIndexName().equals(index.getName());

                int numberOfShardRouting = in.readVInt();
                IndexRoutingTable.Builder indicesRoutingTable = IndexRoutingTable.builder(index);
                for (int idx = 0; idx < numberOfShardRouting; idx++) {
                    IndexShardRoutingTable indexShardRoutingTable = IndexShardRoutingTable.Builder.readFrom(in);
                    indicesRoutingTable.addIndexShard(indexShardRoutingTable);
                }
                verifyCheckSum(in);
                indexRoutingTable = indicesRoutingTable.build();
            }
        } catch (EOFException e) {
            throw new IOException("Indices Routing table is corrupted", e);
        }
    }

    public IndexRoutingTable getIndexRoutingTable() {
        return indexRoutingTable;
    }

    public void writeTo(StreamOutput streamOutput) throws IOException {
        try {
            BufferedChecksumStreamOutput out = new BufferedChecksumStreamOutput(streamOutput);
            IndexRoutingTableHeader indexRoutingTableHeader = new IndexRoutingTableHeader(indexRoutingTable.getIndex().getName());
            indexRoutingTableHeader.writeTo(out);
            out.writeVInt(indexRoutingTable.shards().size());
            for (IndexShardRoutingTable next : indexRoutingTable) {
                IndexShardRoutingTable.Builder.writeTo(next, out);
            }
            out.writeLong(out.getChecksum());
            out.flush();
        } catch (IOException e) {
            throw new IOException("Failed to write IndexRoutingTable to stream", e);
        }
    }

    private void verifyCheckSum(BufferedChecksumStreamInput in) throws IOException {
        long expectedChecksum = in.getChecksum();
        long readChecksum = in.readLong();
        if (readChecksum != expectedChecksum) {
            throw new IOException(
                "checksum verification failed - expected: 0x"
                    + Long.toHexString(expectedChecksum)
                    + ", got: 0x"
                    + Long.toHexString(readChecksum)
            );
        }
    }
}
