/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package com.atlassian.opensearch.aosc.action.start.validation;

import com.atlassian.opensearch.aosc.model.MigrationRequest;
import com.atlassian.opensearch.aosc.transform.TransformFactory;

import org.opensearch.Version;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.metadata.IndexMetadata;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.routing.IndexRoutingTable;
import org.opensearch.cluster.routing.IndexShardRoutingTable;
import org.opensearch.cluster.routing.RoutingTable;
import org.opensearch.cluster.routing.ShardRoutingState;
import org.opensearch.cluster.routing.TestShardRouting;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.index.Index;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.transport.client.Client;

import java.util.Collections;

import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link IndexPreconditionsValidator}: delegates to the
 * production {@code validatePreconditions} helper and wraps errors in an
 * {@link IllegalStateException}.
 */
public class IndexPreconditionsValidatorTests extends OpenSearchTestCase {

    private static IndexMetadata buildMeta(String name) {
        return IndexMetadata.builder(name)
            .settings(
                Settings.builder()
                    .put(IndexMetadata.SETTING_VERSION_CREATED, Version.CURRENT)
                    .put(IndexMetadata.SETTING_NUMBER_OF_SHARDS, 1)
                    .put(IndexMetadata.SETTING_NUMBER_OF_REPLICAS, 0)
            )
            .build();
    }

    private static IndexRoutingTable activeRouting(IndexMetadata meta) {
        Index index = meta.getIndex();
        IndexRoutingTable.Builder b = IndexRoutingTable.builder(index);
        for (int i = 0; i < meta.getNumberOfShards(); i++) {
            ShardId shardId = new ShardId(index, i);
            b.addIndexShard(
                new IndexShardRoutingTable.Builder(shardId).addShard(
                    TestShardRouting.newShardRouting(shardId, "n1", true, ShardRoutingState.STARTED)
                ).build()
            );
        }
        return b.build();
    }

    private static IndexRoutingTable unassignedRouting(IndexMetadata meta) {
        Index index = meta.getIndex();
        IndexRoutingTable.Builder b = IndexRoutingTable.builder(index);
        for (int i = 0; i < meta.getNumberOfShards(); i++) {
            ShardId shardId = new ShardId(index, i);
            b.addIndexShard(
                new IndexShardRoutingTable.Builder(shardId).addShard(
                    TestShardRouting.newShardRouting(shardId, null, true, ShardRoutingState.UNASSIGNED)
                ).build()
            );
        }
        return b.build();
    }

    private static ValidationContext ctx(ClusterState state, IndexMetadata src, IndexMetadata tgt, String alias) {
        MigrationRequest req = new MigrationRequest().setSourceIndex(src.getIndex().getName())
            .setTargetIndex(tgt.getIndex().getName())
            .setAlias(alias);
        return ValidationContext.of(
            req,
            state,
            src,
            tgt,
            new TransformFactory(null),
            new ClusterSettings(Settings.EMPTY, Collections.emptySet()),
            mock(Client.class)
        );
    }

    public void testAllHealthyPasses() {
        IndexMetadata src = buildMeta("src");
        IndexMetadata tgt = buildMeta("tgt");
        ClusterState state = ClusterState.builder(ClusterState.EMPTY_STATE)
            .metadata(Metadata.builder().put(src, false).put(tgt, false).build())
            .routingTable(RoutingTable.builder().add(activeRouting(src)).add(activeRouting(tgt)).build())
            .build();
        new IndexPreconditionsValidator().validate(ctx(state, src, tgt, "my-alias"));
    }

    public void testUnreadyPrimaryFailsAsIllegalState() {
        IndexMetadata src = buildMeta("src");
        IndexMetadata tgt = buildMeta("tgt");
        ClusterState state = ClusterState.builder(ClusterState.EMPTY_STATE)
            .metadata(Metadata.builder().put(src, false).put(tgt, false).build())
            .routingTable(RoutingTable.builder().add(unassignedRouting(src)).add(activeRouting(tgt)).build())
            .build();
        IllegalStateException ex = expectThrows(
            IllegalStateException.class,
            () -> new IndexPreconditionsValidator().validate(ctx(state, src, tgt, "my-alias"))
        );
        assertTrue(ex.getMessage(), ex.getMessage().startsWith("Migration precondition check failed"));
        assertTrue(ex.getMessage(), ex.getMessage().contains("source index [src] has unready primaries"));
    }
}
