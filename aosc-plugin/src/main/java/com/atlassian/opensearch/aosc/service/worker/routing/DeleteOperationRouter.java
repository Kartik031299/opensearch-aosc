/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package com.atlassian.opensearch.aosc.service.worker.routing;

import com.atlassian.opensearch.aosc.compat.DeleteRoutingAccessor;
import com.atlassian.opensearch.aosc.model.DeleteRoutingStrategy;
import com.atlassian.opensearch.aosc.model.ShardRoutingMode;

import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.index.translog.Translog;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Routes replayed deletes according to the migration's immutable strategy. */
public final class DeleteOperationRouter {

    private final DeleteRoutingStrategy strategy;
    private final String targetIndex;
    private final ShardRoutingMode routingMode;
    private final int sourceShardCount;
    private final String[] syntheticRoutings;
    private final int sourceShardId;

    public DeleteOperationRouter(
        DeleteRoutingStrategy strategy,
        String targetIndex,
        ShardRoutingMode routingMode,
        int sourceShardCount,
        String[] syntheticRoutings,
        int sourceShardId
    ) {
        this.strategy = Objects.requireNonNull(strategy, "strategy");
        this.targetIndex = Objects.requireNonNull(targetIndex, "targetIndex");
        this.routingMode = Objects.requireNonNull(routingMode, "routingMode");
        this.sourceShardCount = sourceShardCount;
        this.syntheticRoutings = syntheticRoutings;
        this.sourceShardId = sourceShardId;
        if (strategy == DeleteRoutingStrategy.TRANSLOG_ROUTING && !DeleteRoutingAccessor.isAvailable()) {
            throw new IllegalStateException("Translog delete routing is unavailable in this OpenSearch build");
        }
    }

    public List<DeleteRequest> route(Translog.Delete delete) {
        if (strategy == DeleteRoutingStrategy.TRANSLOG_ROUTING) {
            DeleteRequest request = new DeleteRequest(targetIndex, delete.id());
            String routing = DeleteRoutingAccessor.read(delete);
            if (routing != null) {
                request.routing(routing);
            }
            return List.of(request);
        }

        switch (routingMode) {
            case SAME_SHARD:
                String routing = syntheticRoutings != null ? syntheticRoutings[sourceShardId] : null;
                return List.of(new DeleteRequest(targetIndex, delete.id()).routing(routing));
            case SPLIT_SHARD:
                return routeSplit(delete);
            case BULK_API:
            default:
                return List.of(new DeleteRequest(targetIndex, delete.id()));
        }
    }

    private List<DeleteRequest> routeSplit(Translog.Delete delete) {
        if (syntheticRoutings == null || sourceShardCount <= 0) {
            return List.of(new DeleteRequest(targetIndex, delete.id()));
        }
        int targetShardsPerSourceShard = syntheticRoutings.length / sourceShardCount;
        List<DeleteRequest> requests = new ArrayList<>(targetShardsPerSourceShard);
        for (int i = 0; i < targetShardsPerSourceShard; i++) {
            int targetShard = sourceShardId * targetShardsPerSourceShard + i;
            requests.add(new DeleteRequest(targetIndex, delete.id()).routing(syntheticRoutings[targetShard]));
        }
        return requests;
    }
}
