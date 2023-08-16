/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.common.blobstore;

import org.opensearch.core.action.ActionListener;
import org.opensearch.common.blobstore.stream.write.WriteContext;

import java.io.IOException;

/**
 * An extension of {@link BlobContainer} that adds {@link AsyncMultiStreamBlobContainer#asyncBlobUpload} to allow
 * multipart uploads and performs integrity checks on transferred files
 *
 * @opensearch.internal
 */
public interface AsyncMultiStreamBlobContainer extends BlobContainer {

    /**
     * Reads blob content from multiple streams, each from a specific part of the file, which is provided by the
     * StreamContextSupplier in the WriteContext passed to this method. An {@link IOException} is thrown if reading
     * any of the input streams fails, or writing to the target blob fails
     *
     * @param writeContext         A WriteContext object encapsulating all information needed to perform the upload
     * @param completionListener   Listener on which upload events should be published.
     * @throws IOException if any of the input streams could not be read, or the target blob could not be written to
     */
    void asyncBlobUpload(WriteContext writeContext, ActionListener<Void> completionListener) throws IOException;

    /**
     * @return whether underlying blobContainer can verify integrity of data after transfer. If true and if expected
     * checksum is provided in WriteContext, then the checksum of transferred data is compared with expected checksum
     * by underlying blobContainer. In this case, caller doesn't need to ensure integrity of data.
     */
    boolean remoteIntegrityCheckSupported();
}
