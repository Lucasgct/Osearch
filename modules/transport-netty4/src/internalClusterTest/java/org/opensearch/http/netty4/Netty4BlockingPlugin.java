/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.http.netty4;

import org.opensearch.common.network.NetworkService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.BigArrays;
import org.opensearch.common.util.PageCacheRecycler;
import org.opensearch.core.indices.breaker.CircuitBreakerService;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.http.HttpServerTransport;
import org.opensearch.telemetry.tracing.Tracer;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.Netty4ModulePlugin;
import org.opensearch.transport.SharedGroupFactory;

import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.ReferenceCountUtil;

public class Netty4BlockingPlugin extends Netty4ModulePlugin {

    public class Netty4BlockingHttpServerTransport extends Netty4HttpServerTransport {

        public Netty4BlockingHttpServerTransport(
            Settings settings,
            NetworkService networkService,
            BigArrays bigArrays,
            ThreadPool threadPool,
            NamedXContentRegistry xContentRegistry,
            Dispatcher dispatcher,
            ClusterSettings clusterSettings,
            SharedGroupFactory sharedGroupFactory,
            Tracer tracer
        ) {
            super(
                settings,
                networkService,
                bigArrays,
                threadPool,
                xContentRegistry,
                dispatcher,
                clusterSettings,
                sharedGroupFactory,
                tracer
            );
        }

        @Override
        protected ChannelInboundHandlerAdapter createHeaderVerifier() {
            return new ExampleBlockingNetty4HeaderVerifier();
        }
    }

    @Override
    public Map<String, Supplier<HttpServerTransport>> getHttpTransports(
        Settings settings,
        ThreadPool threadPool,
        BigArrays bigArrays,
        PageCacheRecycler pageCacheRecycler,
        CircuitBreakerService circuitBreakerService,
        NamedXContentRegistry xContentRegistry,
        NetworkService networkService,
        HttpServerTransport.Dispatcher dispatcher,
        ClusterSettings clusterSettings,
        Tracer tracer
    ) {
        return Collections.singletonMap(
            NETTY_HTTP_TRANSPORT_NAME,
            () -> new Netty4BlockingHttpServerTransport(
                settings,
                networkService,
                bigArrays,
                threadPool,
                xContentRegistry,
                dispatcher,
                clusterSettings,
                getSharedGroupFactory(settings),
                tracer
            )
        );
    }

    /** POC for how an external header verifier would be implemented */
    public class ExampleBlockingNetty4HeaderVerifier extends ChannelInboundHandlerAdapter {

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (!(msg instanceof HttpRequest)) {
                ctx.fireChannelRead(msg);
                return;
            }

            HttpRequest request = (HttpRequest) msg;
            if (!isAuthenticated(request)) {
                final FullHttpResponse response = new DefaultFullHttpResponse(request.protocolVersion(), HttpResponseStatus.UNAUTHORIZED);
                ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
                ReferenceCountUtil.release(msg);
            } else {
                // Lets the request pass to the next channel handler
                ctx.fireChannelRead(msg);
            }
        }

        private boolean isAuthenticated(HttpRequest request) {
            final boolean shouldBlock = request.headers().contains("blockme");

            return !shouldBlock;
        }
    }
}
