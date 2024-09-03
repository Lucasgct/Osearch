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

package org.opensearch.rest.action.cat;

import org.opensearch.client.node.NodeClient;
import org.opensearch.common.Table;
import org.opensearch.common.io.Streams;
import org.opensearch.common.io.UTF8StreamWriter;
import org.opensearch.core.common.io.stream.BytesStream;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.pagination.PageToken;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.opensearch.rest.action.cat.RestTable.buildHelpWidths;
import static org.opensearch.rest.action.cat.RestTable.pad;

/**
 * Base Transport action class for _cat API
 *
 * @opensearch.api
 */
public abstract class AbstractCatAction extends BaseRestHandler {

    protected PaginationQueryMetadata paginationQueryMetadata;

    protected abstract RestChannelConsumer doCatRequest(RestRequest request, NodeClient client);

    public abstract void documentation(StringBuilder sb);

    protected abstract Table getTableWithHeader(RestRequest request);

    @Override
    public RestChannelConsumer prepareRequest(final RestRequest request, final NodeClient client) throws IOException {
        boolean helpWanted = request.paramAsBoolean("help", false);
        if (helpWanted) {
            return channel -> {
                Table table = getTableWithHeader(request);
                int[] width = buildHelpWidths(table, request);
                BytesStream bytesOutput = Streams.flushOnCloseStream(channel.bytesOutput());
                UTF8StreamWriter out = new UTF8StreamWriter().setOutput(bytesOutput);
                for (Table.Cell cell : table.getHeaders()) {
                    // need to do left-align always, so create new cells
                    pad(new Table.Cell(cell.value), width[0], request, out);
                    out.append(" | ");
                    pad(new Table.Cell(cell.attr.containsKey("alias") ? cell.attr.get("alias") : ""), width[1], request, out);
                    out.append(" | ");
                    pad(new Table.Cell(cell.attr.containsKey("desc") ? cell.attr.get("desc") : "not available"), width[2], request, out);
                    out.append("\n");
                }
                out.close();
                channel.sendResponse(new BytesRestResponse(RestStatus.OK, BytesRestResponse.TEXT_CONTENT_TYPE, bytesOutput.bytes()));
            };
        } else {
            if (isActionPaginated()) {
                this.paginationQueryMetadata = validateAndGetPaginationMetadata(request);
                assert Objects.nonNull(paginationQueryMetadata) : "paginationQueryMetadata can not be null for paginated queries";
            }
            return doCatRequest(request, client);
        }
    }

    static Set<String> RESPONSE_PARAMS = Collections.unmodifiableSet(
        new HashSet<>(Arrays.asList("format", "h", "v", "ts", "pri", "bytes", "size", "time", "s", "timeout"))
    );

    @Override
    protected Set<String> responseParams() {
        return RESPONSE_PARAMS;
    }

    /**
     *
     * @return boolean denoting whether the RestAction will output paginated responses or not.
     * Is kept false by default, every paginated action to override and return true.
     */
    public boolean isActionPaginated() {
        return false;
    }

    /**
     *
     * @return Metadata that can be extracted out from the rest request. Each paginated action to override and provide
     * its own implementation. Query params supported by the action specific to pagination along with the respective validations,
     * should be added here. The actions would also use the {@param restRequest} to initialise a {@link PageToken}.
     */
    protected PaginationQueryMetadata validateAndGetPaginationMetadata(RestRequest restRequest) {
        return null;
    }

    /**
     * A pagination helper class which would contain requested page token and
     * a map of query params required by a paginated API.
     *
     * @opensearch.internal
     */
    public static class PaginationQueryMetadata {
        private final Map<String, Object> paginationQueryParams;
        private final PageToken requestedPageToken;

        public PaginationQueryMetadata(final Map<String, Object> paginationQueryParams, PageToken requestedPageToken) {
            this.paginationQueryParams = paginationQueryParams;
            this.requestedPageToken = requestedPageToken;
        }

        public Map<String, Object> getPaginationQueryParams() {
            return paginationQueryParams;
        }

        public PageToken getRequestedPageToken() {
            return requestedPageToken;
        }
    }

}
