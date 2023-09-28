/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.rest;

import org.opensearch.client.node.NodeClient;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.test.OpenSearchTestCase;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class DelegatingRestHandlerTests extends OpenSearchTestCase {
    public void testDelegatingRestHandlerShouldActAsOriginal() throws Exception {
        RestHandler rh = new RestHandler() {
            @Override
            public void handleRequest(RestRequest request, RestChannel channel, NodeClient client) throws Exception {
                new BytesRestResponse(RestStatus.OK, BytesRestResponse.TEXT_CONTENT_TYPE, BytesArray.EMPTY);
            }
        };
        RestHandler handlerSpy = spy(rh);
        DelegatingRestHandler drh = new DelegatingRestHandler(handlerSpy);

        List<Method> overridableMethods = Arrays.stream(RestHandler.class.getMethods())
            .filter(
                m -> !(Modifier.isPrivate(m.getModifiers()) || Modifier.isStatic(m.getModifiers()) || Modifier.isFinal(m.getModifiers()))
            )
            .collect(Collectors.toList());

        for (Method method : overridableMethods) {
            int argCount = method.getParameterCount();
            Object[] args = new Object[argCount];
            for (int i = 0; i < argCount; i++) {
                args[i] = any();
            }
            if (args.length > 0) {
                method.invoke(drh, args);
            } else {
                method.invoke(drh);
            }
            method.invoke(verify(handlerSpy, times(1)), args);
        }
    }
}
