/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.authn;

import org.apache.shiro.authc.UsernamePasswordToken;
import org.hamcrest.MatcherAssert;
import org.opensearch.authn.jwt.BadCredentialsException;
import org.opensearch.authn.jwt.JwtVendor;
import org.opensearch.authn.tokens.AuthenticationToken;
import org.opensearch.authn.tokens.BasicAuthToken;
import org.opensearch.test.OpenSearchTestCase;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

public class AuthenticationTokenHandlerTests extends OpenSearchTestCase {

    public void testShouldExtractBasicAuthTokenSuccessfully() throws BadCredentialsException {

        // The auth header that is part of the request
        String authHeader = "Basic YWRtaW46YWRtaW4="; // admin:admin

        AuthenticationToken authToken = new BasicAuthToken(authHeader);

        UsernamePasswordToken usernamePasswordToken = (UsernamePasswordToken) AuthenticationTokenHandler.extractShiroAuthToken(authToken);

        MatcherAssert.assertThat(usernamePasswordToken, notNullValue());
    }

    public void testShouldReturnNullWhenExtractingInvalidToken() throws BadCredentialsException {
        String authHeader = "Basic Nah";

        AuthenticationToken authToken = new BasicAuthToken(authHeader);

        UsernamePasswordToken usernamePasswordToken = (UsernamePasswordToken) AuthenticationTokenHandler.extractShiroAuthToken(authToken);

        MatcherAssert.assertThat(usernamePasswordToken, nullValue());
    }

    public void testShouldReturnNullWhenExtractingNullToken() throws BadCredentialsException {

        org.apache.shiro.authc.AuthenticationToken shiroAuthToken = AuthenticationTokenHandler.extractShiroAuthToken(null);

        MatcherAssert.assertThat(shiroAuthToken, nullValue());
    }

    public void testShouldExtractBearerAuthTokenSuccessfully() throws BadCredentialsException {

        Map<String, Object> jwtClaims = new HashMap<>();
        jwtClaims.put("sub", "testSubject");

        String encodedToken = JwtVendor.createJwt(jwtClaims);

        String headerBody = "Bearer " + encodedToken;

        AuthenticationToken authToken = new BasicAuthToken(headerBody);

        UsernamePasswordToken usernamePasswordToken = (UsernamePasswordToken) AuthenticationTokenHandler.extractShiroAuthToken(authToken);

        MatcherAssert.assertThat(usernamePasswordToken, notNullValue());
    }

    public void testShouldReturnNullWhenExtractingInvalidBearerToken() throws BadCredentialsException { // TODO: Update this once determine how to create invalid JWT
        String authHeader = "Basic Nah";

        AuthenticationToken authToken = new BasicAuthToken(authHeader);

        UsernamePasswordToken usernamePasswordToken = (UsernamePasswordToken) AuthenticationTokenHandler.extractShiroAuthToken(authToken);

        MatcherAssert.assertThat(usernamePasswordToken, nullValue());
    }

}
