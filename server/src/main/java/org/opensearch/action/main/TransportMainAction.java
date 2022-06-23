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
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package org.opensearch.action.main;

import org.opensearch.Build;
import org.opensearch.LegacyESVersion;
import org.opensearch.Version;
import org.opensearch.action.ActionListener;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.logging.DeprecationLogger;
import org.opensearch.common.settings.Setting;
import org.opensearch.common.settings.Settings;
import org.opensearch.node.Node;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

/**
 * Performs the main action
 *
 * @opensearch.internal
 */
public class TransportMainAction extends HandledTransportAction<MainRequest, MainResponse> {

    private static final DeprecationLogger DEPRECATION_LOGGER = DeprecationLogger.getLogger(TransportMainAction.class);

    public static final String OVERRIDE_MAIN_RESPONSE_VERSION_KEY = "compatibility.override_main_response_version";

    public static final Setting<Boolean> OVERRIDE_MAIN_RESPONSE_VERSION = Setting.boolSetting(
        OVERRIDE_MAIN_RESPONSE_VERSION_KEY,
        false,
        Setting.Property.NodeScope,
        Setting.Property.Dynamic
    );

    public static final String OVERRIDE_MAIN_RESPONSE_VERSION_DEPRECATION_MESSAGE = "overriding main response version"
        + " number will be removed in a future version";

    private final String nodeName;
    private final ClusterService clusterService;
    private volatile String responseVersion;

    @Inject
    public TransportMainAction(
        Settings settings,
        TransportService transportService,
        ActionFilters actionFilters,
        ClusterService clusterService
    ) {
        super(MainAction.NAME, transportService, actionFilters, MainRequest::new);
        this.nodeName = Node.NODE_NAME_SETTING.get(settings);
        this.clusterService = clusterService;
        setResponseVersion(OVERRIDE_MAIN_RESPONSE_VERSION.get(settings));

        clusterService.getClusterSettings().addSettingsUpdateConsumer(OVERRIDE_MAIN_RESPONSE_VERSION, this::setResponseVersion);
    }

    private void setResponseVersion(boolean isResponseVersionOverrideEnabled) {
        if (isResponseVersionOverrideEnabled) {
            DEPRECATION_LOGGER.deprecate(OVERRIDE_MAIN_RESPONSE_VERSION.getKey(), OVERRIDE_MAIN_RESPONSE_VERSION_DEPRECATION_MESSAGE);
            this.responseVersion = LegacyESVersion.V_7_10_2.toString();
        } else {
            this.responseVersion = Build.CURRENT.getQualifiedVersion();
        }
    }

    @Override
    protected void doExecute(Task task, MainRequest request, ActionListener<MainResponse> listener) {
        ClusterState clusterState = clusterService.state();
        listener.onResponse(
            new MainResponse(
                nodeName,
                Version.CURRENT,
                clusterState.getClusterName(),
                clusterState.metadata().clusterUUID(),
                Build.CURRENT,
                responseVersion
            )
        );
    }
}
