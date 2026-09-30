/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package com.atlassian.opensearch.aosc.model;

/**
 * Describes the source-to-target shard topology. {@link DeleteRoutingStrategy}
 * independently selects whether replayed deletes use recorded routing or this topology.
 *
 * <ul>
 *   <li><b>SAME_SHARD</b> (N→N): Source and target have the same shard count.
 *       Legacy delete replay addresses the corresponding target shard.</li>
 *   <li><b>SPLIT_SHARD</b> (N→kN, k is a power of 2): Legacy delete replay fans
 *       out to the k candidate target shards when routing metadata is compatible.</li>
 *   <li><b>BULK_API</b> (all other relationships): Legacy delete replay sends an
 *       unrouted delete and requires explicit data-loss consent. Translog-routing
 *       replay still sends one delete with its recorded routing value.</li>
 * </ul>
 */
public enum ShardRoutingMode {
    SAME_SHARD,
    SPLIT_SHARD,
    BULK_API
}
