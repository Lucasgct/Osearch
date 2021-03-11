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

package org.opensearch.rest.action.admin.indices;

import org.opensearch.action.admin.indices.resolve.ResolveIndexAction;
import org.opensearch.action.support.IndicesOptions;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.Strings;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

import java.io.IOException;
import java.util.List;

public class RestResolveIndexAction extends BaseRestHandler {

    @Override
    public String getName() {
        return "resolve_index_action";
    }

    @Override
    public List<Route> routes() {
        return org.elasticsearch.common.collect.List.of(
            new Route(RestRequest.Method.GET, "/_resolve/index/{name}")
        );
    }

    @Override
    protected BaseRestHandler.RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        String[] indices = Strings.splitStringByCommaToArray(request.param("name"));
        ResolveIndexAction.Request resolveRequest = new ResolveIndexAction.Request(indices,
            IndicesOptions.fromRequest(request, ResolveIndexAction.Request.DEFAULT_INDICES_OPTIONS));
        return channel -> client.admin().indices().resolveIndex(resolveRequest, new RestToXContentListener<>(channel));
    }
}

