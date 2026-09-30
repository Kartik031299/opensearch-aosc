/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package com.atlassian.opensearch.aosc.utils;

import com.atlassian.opensearch.aosc.model.DeleteRoutingStrategy;

import org.opensearch.Version;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.node.DiscoveryNodes;
import org.opensearch.test.OpenSearchTestCase;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DeleteRoutingStrategyResolverTests extends OpenSearchTestCase {

    public void testAll39NodesUseTranslogRouting() {
        ClusterState state = stateWithMinVersion(Version.CURRENT);
        assertEquals(DeleteRoutingStrategy.TRANSLOG_ROUTING, DeleteRoutingStrategyResolver.resolve(state));
    }

    public void testMixedClusterUsesShardTopology() {
        ClusterState state = stateWithMinVersion(Version.fromString("3.8.0"));
        assertEquals(DeleteRoutingStrategy.SHARD_TOPOLOGY, DeleteRoutingStrategyResolver.resolve(state));
    }

    public void testEmptyClusterUsesShardTopology() {
        ClusterState state = mock(ClusterState.class);
        DiscoveryNodes nodes = mock(DiscoveryNodes.class);
        when(state.nodes()).thenReturn(nodes);

        assertEquals(DeleteRoutingStrategy.SHARD_TOPOLOGY, DeleteRoutingStrategyResolver.resolve(state));
    }

    private static ClusterState stateWithMinVersion(Version version) {
        ClusterState state = mock(ClusterState.class);
        DiscoveryNodes nodes = mock(DiscoveryNodes.class);
        when(state.nodes()).thenReturn(nodes);
        when(nodes.getSize()).thenReturn(1);
        when(nodes.getMinNodeVersion()).thenReturn(version);
        return state;
    }
}
