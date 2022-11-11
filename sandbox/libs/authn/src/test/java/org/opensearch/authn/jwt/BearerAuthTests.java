/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.authn.jwt;

import org.apache.cxf.rs.security.jose.jwt.JwtToken;
import org.opensearch.test.OpenSearchTestCase;

import java.util.HashMap;
import java.util.Map;

public class BearerAuthTests extends OpenSearchTestCase {

    public void testExtractJwt() {
        Map<String, Object> jwtClaims = new HashMap<>();
        jwtClaims.put("sub", "testSubject");

        String encodedToken = JwtVendor.createJwt(jwtClaims);

        try {
            JwtToken token = JwtVerifier.getVerifiedJwtToken(encodedToken);
            assertTrue(token.getClaims().getClaim("sub").equals("testSubject"));
        } catch (BadCredentialsException e) {
            fail("Unexpected BadCredentialsException thrown");
        }
    }
}
