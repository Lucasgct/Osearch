/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.extensions;

import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.opensearch.Application;
import org.opensearch.OpenSearchException;
import org.opensearch.Version;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.node.DiscoveryNodeRole;
import org.opensearch.common.transport.TransportAddress;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentFragment;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.identity.Subject;
import org.opensearch.identity.scopes.Scope;
import org.opensearch.identity.tokens.AuthToken;

/**
 * Discover extensions running independently or in a separate process
 *
 * @opensearch.internal
 */
public class DiscoveryExtensionNode extends DiscoveryNode implements Writeable, ToXContentFragment, Subject, Application {

    private Version minimumCompatibleVersion;
    private List<ExtensionDependency> dependencies = Collections.emptyList();
    private List<String> implementedInterfaces = Collections.emptyList();
    private List<Scope> scopes = List.of();

    public DiscoveryExtensionNode(
        String name,
        String id,
        TransportAddress address,
        Map<String, String> attributes,
        Version version,
        Version minimumCompatibleVersion,
        List<ExtensionDependency> dependencies,
        List<Scope> scopes
    ) {
        super(name, id, address, attributes, DiscoveryNodeRole.BUILT_IN_ROLES, version);
        this.minimumCompatibleVersion = minimumCompatibleVersion;
        this.dependencies = dependencies;
        this.scopes = scopes;
        validate();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeVersion(minimumCompatibleVersion);
        out.writeVInt(dependencies.size());
        for (ExtensionDependency dependency : dependencies) {
            dependency.writeTo(out);
        }
        if (out.getVersion().onOrAfter(Version.V_3_0_0)) {
            for (Scope scope : scopes) {
                scope.writeTo(out);
            }
        }
    }

    /**
     * Construct DiscoveryExtensionNode from a stream.
     *
     * @param in the stream
     * @throws IOException if an I/O exception occurred reading the plugin info from the stream
     */
    public DiscoveryExtensionNode(final StreamInput in) throws IOException {
        super(in);
        minimumCompatibleVersion = in.readVersion();
        int size = in.readVInt();
        dependencies = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            dependencies.add(new ExtensionDependency(in));
        }
        scopes = new ArrayList<>(size);
        if (in.getVersion().onOrAfter(Version.V_3_0_0)) {
            for (int i = 0; i < size; i++) {
                scopes.add(Scope.readStream(in));
            }
        }
    }

    public List<ExtensionDependency> getDependencies() {
        return dependencies;
    }

    public Version getMinimumCompatibleVersion() {
        return minimumCompatibleVersion;
    }

    public List<String> getImplementedInterfaces() {
        return implementedInterfaces;
    }

    public Set<Scope> getScopes() {
        return new HashSet<>(this.scopes);
    }

    public void setImplementedInterfaces(List<String> implementedInterfaces) {
        this.implementedInterfaces = implementedInterfaces;
    }

    public boolean dependenciesContain(ExtensionDependency dependency) {
        for (ExtensionDependency extensiondependency : this.dependencies) {
            if (dependency.getUniqueId().equals(extensiondependency.getUniqueId())
                && dependency.getVersion().equals(extensiondependency.getVersion())) {
                return true;
            }
        }
        return false;
    }

    private void validate() {
        if (!Version.CURRENT.onOrAfter(minimumCompatibleVersion)) {
            throw new OpenSearchException(
                "Extension minimumCompatibleVersion: "
                    + minimumCompatibleVersion
                    + " is greater than current OpenSearch version: "
                    + Version.CURRENT
            );
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        return null;
    }

    @Override
    public void authenticate(AuthToken token) {

    }

    @Override
    public Optional<Principal> getApplication() {
        return Optional.of(this.getPrincipal());
    }
}
