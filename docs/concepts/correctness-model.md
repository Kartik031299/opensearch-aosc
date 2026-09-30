# Correctness Model

AOSC's correctness model is based on backfill, source operation-history replay, and a write-blocked cutover.

## What AOSC Tries to Preserve

For supported routing and transform configurations, AOSC aims for the target index to reflect the source index at cutover time after applying the configured transform.

This is not a blanket guarantee for every index configuration. Custom routing, unsupported transform behavior, application writes that do not retry, and manual interference can break the expected result.

## Backfill

Each shard worker reads source documents from its source primary and writes transformed documents to the target. Backfill writes use document IDs and routing values from the source document and use idempotent index operations.

## Operation-History Replay

AOSC uses source-shard operation history to replay writes that happen during backfill. Workers track sequence-number progress and replay index/create/delete operations into the target.

Retention leases are used to keep required operation history available while workers are active.

## Routing

Backfill and replayed index operations preserve `_routing` when it is present. OpenSearch 3.9+ also records routing for deletes. A new migration on an all-3.9+ cluster replays each delete with that recorded value; pre-3.9, mixed-version, and older persisted migrations use shard topology because their delete history may not contain routing.

AOSC records a topology mode separately from its delete-routing strategy:

| Mode | Topology | Correctness boundary |
|------|----------|----------------------|
| `SAME_SHARD` | Source and target have the same shard count. | Legacy replay can address the corresponding target shard. |
| `SPLIT_SHARD` | Target shard count is a power-of-2 multiple of the source shard count. | Legacy replay can fan out within the source shard's target group when routing metadata is compatible. |
| `BULK_API` | Shrink, non-multiple change, or non-power-of-2 expansion. | `TRANSLOG_ROUTING` uses the recorded value. Legacy `SHARD_TOPOLOGY` routes deletes by `_id` and can leave stale custom-routed documents. |

See [Routing and Replay](./routing-and-replay) for the detailed model and the data-loss consent gate.

## Convergence

After the first replay pass, workers continue replaying until the gap to the source global checkpoint is below the configured threshold. At cutover, AOSC write-blocks the source and requires final catch-up to close the gap.

## Cutover

The coordinator:

1. Restores target settings needed for readiness.
2. Applies a source write block.
3. Flushes the source.
4. Signals workers to replay final operations.
5. Validates source and target document counts, optionally filtered by `validation_query`.
6. Swaps the alias from source to target.
7. Removes the source write block when configured.

## Important Boundaries

- Backfill and replay write target documents by ID/source/routing; they do not preserve OpenSearch internal `_version` or `_seq_no` as target metadata.
- Built-in transforms are update-script based and emit one target document per source document.
- AOSC relies on applications retrying writes rejected during the cutover write block.
- Post-cutover rollback is not automatic and can lose writes if handled incorrectly.
- The target index remains after cancellation or failure for inspection and manual cleanup.
