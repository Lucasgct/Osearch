/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.extensions.rest;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.opensearch.client.node.NodeClient;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.extensions.DiscoveryExtension;
import org.opensearch.extensions.ExtensionsOrchestrator;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.RestRequest.Method;
import org.opensearch.rest.RestStatus;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportException;
import org.opensearch.transport.TransportResponseHandler;
import org.opensearch.transport.TransportService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static java.util.Collections.unmodifiableList;

/**
 * An action that forwards REST requests to an extension
 */
public class RestSendToExtensionAction extends BaseRestHandler {

    private static final String SEND_TO_EXTENSION_ACTION = "send_to_extension_action";
    private static final Logger logger = LogManager.getLogger(RestSendToExtensionAction.class);

    private final List<Route> routes;
    private final String uriPrefix;
    private final DiscoveryExtension discoveryExtension;
    private final TransportService transportService;

    /**
     * Instantiates this object using a {@link RegisterRestActionsRequest} to populate the routes.
     *
     * @param restActionsRequest A request encapsulating a list of Strings with the API methods and URIs.
     * @param transportService The OpenSearch transport service
     * @param discoveryExtension The extension node to which to send actions
     */
    public RestSendToExtensionAction(
        RegisterRestActionsRequest restActionsRequest,
        DiscoveryExtension discoveryExtension,
        TransportService transportService
    ) {
        this.uriPrefix = "/_extensions/_" + restActionsRequest.getUniqueId();
        List<Route> restActionsAsRoutes = new ArrayList<>();
        for (String restAction : restActionsRequest.getRestActions()) {
            RestRequest.Method method;
            String uri;
            try {
                int delim = restAction.indexOf(' ');
                method = RestRequest.Method.valueOf(restAction.substring(0, delim));
                uri = uriPrefix + restAction.substring(delim).trim();
            } catch (IndexOutOfBoundsException | IllegalArgumentException e) {
                throw new IllegalArgumentException(restAction + " does not begin with a valid REST method");
            }
            logger.info("Registering: " + method + " " + uri);
            restActionsAsRoutes.add(new Route(method, uri));
        }
        this.routes = unmodifiableList(restActionsAsRoutes);
        this.discoveryExtension = discoveryExtension;
        this.transportService = transportService;
    }

    @Override
    public String getName() {
        return SEND_TO_EXTENSION_ACTION;
    }

    @Override
    public List<Route> routes() {
        return this.routes;
    }

    @Override
    public RestChannelConsumer prepareRequest(final RestRequest request, final NodeClient client) throws IOException {
        Method method = request.getHttpRequest().method();
        String uri = request.getHttpRequest().uri();
        if (uri.startsWith(uriPrefix)) {
            uri = uri.substring(uriPrefix.length());
        }
        String message = "Forwarding the request " + method + " " + uri + " to " + discoveryExtension;
        logger.info(message);
        // Hack to pass a final class in to fetch the response string
        final StringBuilder responseBuilder = new StringBuilder();
        final CountDownLatch inProgressLatch = new CountDownLatch(1);
        final TransportResponseHandler<RestExecuteOnExtensionResponse> restExecuteOnExtensionResponseHandler = new TransportResponseHandler<
            RestExecuteOnExtensionResponse>() {

            @Override
            public RestExecuteOnExtensionResponse read(StreamInput in) throws IOException {
                return new RestExecuteOnExtensionResponse(in);
            }

            @Override
            public void handleResponse(RestExecuteOnExtensionResponse response) {
                responseBuilder.append(response.getResponse());
                logger.info("Received response from extension: {}", response.getResponse());
                inProgressLatch.countDown();
            }

            @Override
            public void handleException(TransportException exp) {
                responseBuilder.append("FAILED: ").append(exp);
                logger.debug(new ParameterizedMessage("REST request failed"), exp);
                inProgressLatch.countDown();
            }

            @Override
            public String executor() {
                return ThreadPool.Names.GENERIC;
            }
        };
        try {
            transportService.sendRequest(
                discoveryExtension,
                ExtensionsOrchestrator.REQUEST_REST_EXECUTE_ON_EXTENSION_ACTION,
                new RestExecuteOnExtensionRequest(method, uri),
                restExecuteOnExtensionResponseHandler
            );
            try {
                inProgressLatch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                return channel -> channel.sendResponse(
                    new BytesRestResponse(RestStatus.REQUEST_TIMEOUT, "No response from extension to request.")
                );
            }
        } catch (Exception e) {
            logger.info("Failed to send REST Actions to extension " + discoveryExtension.getName(), e);
        }
        String response = responseBuilder.toString();
        if (response.isBlank() || response.startsWith("FAILED")) {
            return channel -> channel.sendResponse(
                new BytesRestResponse(RestStatus.INTERNAL_SERVER_ERROR, response.isBlank() ? "Request Failed" : response)
            );
        }
        return channel -> channel.sendResponse(new BytesRestResponse(RestStatus.OK, response));
    }
}
