# Monitor a Migration

Use `_status` for one source index and `_list` for an overview.

## Status Endpoint

```bash
curl -s 'http://localhost:9200/_plugins/_aosc/my-index-v1/_status' | jq '.'
```

Example shape, truncated to one shard:

```json
{
  "migration_id": "21b526c5-4794-4a55-9186-2020f702a731",
  "source_index": "my-index-v1",
  "target_index": "my-index-v2",
  "alias": "my-index",
  "phase": "COMPLETED",
  "shard_routing_mode": "SAME_SHARD",
  "delete_routing_strategy": "TRANSLOG_ROUTING",
  "options": {
    "convergence_threshold_per_shard": 1000,
    "max_convergence_rounds_per_shard": 1000
  },
  "cutover_context": {
    "source_doc_count": 689217412,
    "target_doc_count": 689217412,
    "doc_count_validation_passed": true,
    "alias_swap_succeeded": true,
    "cutover_start_millis": 1781030652331,
    "cutover_end_millis": 1781030665206
  },
  "shards": {
    "0": {
      "phase": "COMPLETED",
      "last_replayed_seq_no": 8453324,
      "target_seq_no": 8453324,
      "backfill_cutoff_seq_no": 8448260,
      "backfill": { "status": "COMPLETED", "documents_indexed": 1012245, "rounds": 2025 },
      "replay": { "status": "COMPLETED", "operations_applied": 415, "current_gap": 0 },
      "convergence": { "status": "COMPLETED", "operations_applied": 4649, "rounds": 3978, "current_gap": 0 },
      "catching_up": { "status": "COMPLETED", "operations_applied": 0, "current_gap": 0 }
    }
  }
}
```

On large shard counts, the full `_status` response can be several MB because every shard includes phase counters and transition history.

## Fields to Watch

| Field | Meaning |
|-------|---------|
| `phase` | Coordinator phase. |
| `shard_routing_mode` | Source/target shard topology: `SAME_SHARD`, `SPLIT_SHARD`, or `BULK_API`. |
| `delete_routing_strategy` | Whether deletes use recorded routing (`TRANSLOG_ROUTING`) or legacy shard topology (`SHARD_TOPOLOGY`). This value is fixed at migration start. |
| `cutover_context.*` | Document-count validation and alias-swap timing after completion starts. |
| `error_message` | Coordinator-level failure message when present. |
| `shards.*.phase` | Per-shard worker phase. |
| `shards.*.error` | Shard-level failure message when present. |
| `shards.*.last_replayed_seq_no` and `shards.*.target_seq_no` | Replay checkpoint and target sequence number. |
| `shards.*.backfill.documents_indexed` | Backfill document counter. |
| `shards.*.replay.operations_applied` | Replay operation counter. |
| `shards.*.convergence.current_gap` | Current convergence gap. |
| `shards.*.convergence.rounds` | Number of convergence rounds. |
| `shards.*.catching_up.operations_applied` | Final catch-up operation counter. |
| `shards.*.*.bulk_retries` | Bulk write retry counters for the phase. |

## Useful Queries

Show coordinator phase:

```bash
curl -s 'http://localhost:9200/_plugins/_aosc/my-index-v1/_status' | jq '.phase'
```

Show non-terminal shards:

```bash
curl -s 'http://localhost:9200/_plugins/_aosc/my-index-v1/_status' \
  | jq '.shards | to_entries[] | select(.value.phase | IN("COMPLETED", "CANCELLED", "FAILED") | not)'
```

Show failed shards:

```bash
curl -s 'http://localhost:9200/_plugins/_aosc/my-index-v1/_status' \
  | jq '.shards | to_entries[] | select(.value.phase == "FAILING" or .value.phase == "FAILED")'
```

Summarize shard phases:

```bash
curl -s 'http://localhost:9200/_plugins/_aosc/my-index-v1/_status' \
  | jq '.shards | to_entries | group_by(.value.phase) | map({phase: .[0].value.phase, count: length})'
```

Compute the cutover-context duration after completion starts:

```bash
curl -s 'http://localhost:9200/_plugins/_aosc/my-index-v1/_status' \
  | jq '(.cutover_context.cutover_end_millis - .cutover_context.cutover_start_millis) / 1000'
```

List active migrations:

```bash
curl -s 'http://localhost:9200/_plugins/_aosc/_list?status=ACTIVE' | jq '.'
```

## Status Source

Active migrations are served from the coordinator cache when available. Terminal or older migrations are read from `.aosc-migrations`.

If the status endpoint returns not found, check `_list` and cluster logs to determine whether the migration never started, has already been cleaned up, or the source index name is wrong.
