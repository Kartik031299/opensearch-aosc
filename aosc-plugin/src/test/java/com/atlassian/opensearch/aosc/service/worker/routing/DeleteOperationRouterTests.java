/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package com.atlassian.opensearch.aosc.service.worker.routing;

import com.atlassian.opensearch.aosc.model.DeleteRoutingStrategy;
import com.atlassian.opensearch.aosc.model.ShardRoutingMode;

import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.index.translog.Translog;
import org.opensearch.test.OpenSearchTestCase;

import java.util.List;
import java.util.stream.Collectors;

public class DeleteOperationRouterTests extends OpenSearchTestCase {

    private static final Translog.Delete DELETE = new Translog.Delete("doc-1", 7, 1);

    public void testSameShardUsesSyntheticRouting() {
        var router = new DeleteOperationRouter(
            DeleteRoutingStrategy.SHARD_TOPOLOGY,
            "target",
            ShardRoutingMode.SAME_SHARD,
            2,
            new String[] { "r0", "r1" },
            1
        );

        List<DeleteRequest> requests = router.route(DELETE);

        assertEquals(1, requests.size());
        assertEquals("doc-1", requests.get(0).id());
        assertEquals("r1", requests.get(0).routing());
    }

    public void testSplitFansOutToCandidateShards() {
        var router = new DeleteOperationRouter(
            DeleteRoutingStrategy.SHARD_TOPOLOGY,
            "target",
            ShardRoutingMode.SPLIT_SHARD,
            2,
            new String[] { "r0", "r1", "r2", "r3", "r4", "r5", "r6", "r7" },
            1
        );

        List<DeleteRequest> requests = router.route(DELETE);

        assertEquals(4, requests.size());
        assertEquals(List.of("r4", "r5", "r6", "r7"), requests.stream().map(DeleteRequest::routing).collect(Collectors.toList()));
    }

    public void testSplitWithoutSyntheticRoutingUsesUnroutedDelete() {
        var router = new DeleteOperationRouter(DeleteRoutingStrategy.SHARD_TOPOLOGY, "target", ShardRoutingMode.SPLIT_SHARD, 2, null, 0);

        List<DeleteRequest> requests = router.route(DELETE);

        assertEquals(1, requests.size());
        assertNull(requests.get(0).routing());
    }

    public void testBulkApiUsesUnroutedDelete() {
        var router = new DeleteOperationRouter(
            DeleteRoutingStrategy.SHARD_TOPOLOGY,
            "target",
            ShardRoutingMode.BULK_API,
            2,
            new String[] { "ignored" },
            0
        );

        List<DeleteRequest> requests = router.route(DELETE);

        assertEquals(1, requests.size());
        assertEquals("target", requests.get(0).index());
        assertNull(requests.get(0).routing());
    }
}
