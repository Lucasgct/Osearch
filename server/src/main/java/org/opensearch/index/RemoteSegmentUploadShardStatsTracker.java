/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.index;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Remote upload stats.
 *
 * @opensearch.internal
 */
public class RemoteSegmentUploadShardStatsTracker {

    public static final long UNASSIGNED = 0L;

    private volatile long localRefreshSeqNo = UNASSIGNED;

    private volatile long localRefreshTime = UNASSIGNED;

    private volatile long remoteRefreshSeqNo = UNASSIGNED;

    private volatile long remoteRefreshTime = UNASSIGNED;

    private volatile long uploadBytesStarted = UNASSIGNED;

    private volatile long uploadBytesFailed = UNASSIGNED;

    private volatile long uploadBytesSucceeded = UNASSIGNED;

    private volatile long totalUploadsStarted = UNASSIGNED;

    private volatile long totalUploadsFailed = UNASSIGNED;

    private volatile long totalUploadsSucceeded = UNASSIGNED;

    /**
     * Keeps map of filename to bytes length of the local segments post most recent refresh.
     */
    private volatile Map<String, Long> latestLocalFileNameLengthMap;

    /**
     * Keeps map of filename to bytes length of the most recent segments upload as part of refresh.
     */
    private volatile Map<String, Long> latestUploadFileNameLengthMap;

    public void incrementUploadBytesStarted(long bytes) {
        uploadBytesStarted += bytes;
    }

    public void incrementUploadBytesFailed(long bytes) {
        uploadBytesFailed += bytes;
    }

    public void incrementUploadBytesSucceeded(long bytes) {
        uploadBytesSucceeded += bytes;
    }

    public void incrementTotalUploadsStarted() {
        totalUploadsStarted += 1;
    }

    public void incrementTotalUploadsFailed() {
        totalUploadsFailed += 1;
    }

    public void incrementTotalUploadsSucceeded() {
        totalUploadsSucceeded += 1;
    }

    public void updateLocalRefreshSeqNo(long localRefreshSeqNo) {
        this.localRefreshSeqNo = localRefreshSeqNo;
    }

    public void updateLocalRefreshTime(long localRefreshTime) {
        this.localRefreshTime = localRefreshTime;
    }

    public void updateRemoteRefreshSeqNo(long remoteRefreshSeqNo) {
        this.remoteRefreshSeqNo = remoteRefreshSeqNo;
    }

    public void updateRemoteRefreshTime(long remoteRefreshTime) {
        this.remoteRefreshTime = remoteRefreshTime;
    }

    public void updateLatestLocalFileNameLengthMap(Map<String, Long> latestLocalFileNameLengthMap) {
        this.latestLocalFileNameLengthMap = latestLocalFileNameLengthMap;
    }

    public Set<String> getLatestUploadFiles() {
        return latestUploadFileNameLengthMap == null ? Collections.emptySet() : latestUploadFileNameLengthMap.keySet();
    }

    public void updateLatestUploadFileNameLengthMap(Map<String, Long> latestUploadFileNameLengthMap) {
        this.latestUploadFileNameLengthMap = latestUploadFileNameLengthMap;
    }
}
