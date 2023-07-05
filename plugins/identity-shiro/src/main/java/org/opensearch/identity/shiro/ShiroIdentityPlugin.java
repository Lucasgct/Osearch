/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.identity.shiro;

import java.security.Principal;
import org.opensearch.identity.ServiceAccountManager;
import org.opensearch.identity.Subject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.identity.tokens.AuthToken;
import org.opensearch.identity.tokens.TokenManager;
import org.opensearch.plugins.IdentityPlugin;
import org.opensearch.common.settings.Settings;
import org.opensearch.plugins.Plugin;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.mgt.SecurityManager;

/**
 * Identity implementation with Shiro
 *
 * @opensearch.experimental
 */
public final class ShiroIdentityPlugin extends Plugin implements IdentityPlugin {
    private Logger log = LogManager.getLogger(this.getClass());

    private final Settings settings;
    private final ShiroTokenManager authTokenHandler;
    private final ShiroServiceAccountManager serviceAccountManager;

    /**
     * Create a new instance of the Shiro Identity Plugin
     *
     * @param settings settings being used in the configuration
     */
    public ShiroIdentityPlugin(final Settings settings) {
        this.settings = settings;
        authTokenHandler = new ShiroTokenManager();
        serviceAccountManager = new ShiroServiceAccountManager();

        SecurityManager securityManager = new ShiroSecurityManager();
        SecurityUtils.setSecurityManager(securityManager);
    }

    /**
     * Return a Shiro Subject based on the provided authTokenHandler and current subject
     *
     * @return The current subject
     */
    @Override
    public Subject getSubject() {
        return new ShiroSubject(authTokenHandler, SecurityUtils.getSubject());
    }

    /**
     * Return the Shiro Token Handler
     *
     * @return the Shiro Token Handler
     */
    @Override
    public TokenManager getTokenManager() {
        return this.authTokenHandler;
    }

    @Override
    public ServiceAccountManager getServiceAccountManager() {
        return this.serviceAccountManager;
    }


    // This may seem circular since it is a Plugin being used to track other Plugins, but this can be changed in the future
    @Override
    public Principal getPrincipal() {
        return null;
    }

    @Override
    public void authenticate(AuthToken token) {
    }
}
