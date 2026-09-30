# Comparison and Tradeoffs

AOSC is one option for index migration. It is not a replacement for every OpenSearch migration mechanism.

## Comparison

| Need | Common Option | Notes |
|------|---------------|-------|
| Add a compatible mapping field | OpenSearch mapping update | Often no migration is needed. |
| Copy a small or offline index | `_reindex` | Simpler operationally when write reconciliation is not needed. |
| Change shard count only | Split/shrink where applicable | Native operations may be better when their constraints fit. |
| Transform documents in the same cluster while source stays live | AOSC or custom pipeline | AOSC handles backfill, replay, and alias cutover. |
| Cross-cluster movement | Remote reindex, snapshot/restore, CCR, or custom replication | AOSC is same-cluster only. |

## AOSC vs `_reindex`

`_reindex` is simpler and well-known. It is a good default when the source can be made quiet or when you have a separate reconciliation path for writes that occur during reindex.

AOSC adds operation-history replay and coordinated cutover for live same-cluster migrations, at the cost of installing a plugin and accepting AOSC's routing, transform, and cutover constraints.

## AOSC vs Split/Shrink

Split and shrink are native operations for specific shard-count changes. They have their own constraints and do not solve document reshaping. Use them when the change fits the native operation and you do not need AOSC's replay workflow.

## AOSC vs Manual Reindex and Alias Swap

A manual workflow gives you full control but requires you to design backfill, replay/reconciliation, validation, retry behavior, and rollback. AOSC packages those steps into the plugin, but only for supported same-cluster use cases.

## When AOSC Is a Reasonable Fit

- The source must continue receiving writes during most of the migration.
- The target index needs different mappings, settings, shard count, or transformed source documents.
- The application writes through an alias and retries write-block rejections.
- The migration is same-cluster.
- You can test the transform and routing behavior before migration.

## When to Avoid AOSC

- The source can be stopped and `_reindex` is enough.
- The migration is cross-cluster.
- Alias-based cutover is not possible.
- Your custom-routing model cannot tolerate the documented same-ID collision or legacy delete-replay boundaries during a shard-count change.
- You need a full dry-run mode or formal metrics export.
