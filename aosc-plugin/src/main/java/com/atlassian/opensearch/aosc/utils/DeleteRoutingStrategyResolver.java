/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package com.atlassian.opensearch.aosc.utils;

import com.atlassian.opensearch.aosc.compat.DeleteRoutingAccessor;
import com.atlassian.opensearch.aosc.model.DeleteRoutingStrategy;

import org.opensearch.Version;
import org.opensearch.cluster.ClusterState;

import java.util.Objects;

/** Resolves the immutable delete-routing strategy for a new migration. */
public final class DeleteRoutingStrategyResolver {

    private static final Version FIRST_TRANSLOG_ROUTING_VERSION = Version.fromString("3.9.0");

    private DeleteRoutingStrategyResolver() {}

    public static DeleteRoutingStrategy resolve(ClusterState state) {
        return supportsTranslogRouting(state) ? DeleteRoutingStrategy.TRANSLOG_ROUTING : DeleteRoutingStrategy.SHARD_TOPOLOGY;
    }

    private static boolean supportsTranslogRouting(ClusterState state) {
        Objects.requireNonNull(state, "state");
        if (state.nodes().getSize() > 0
            && DeleteRoutingAccessor.isAvailable()
            && state.nodes().getMinNodeVersion().onOrAfter(FIRST_TRANSLOG_ROUTING_VERSION)) {
            return true;
        }
        return false;
    }
}
