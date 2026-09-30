# Code Layout

AOSC source lives in a single `aosc-plugin/` module. Shared code is at `src/main/java/com/atlassian/opensearch/aosc/`; files that differ between OpenSearch 2.x and 3.x live in `src/main/java-2x/` and `src/main/java-3x/`. A second pair of capability directories, `java-translog-delete-pre39/` and `java-translog-delete-39plus/`, isolates the delete-routing API added in OpenSearch 3.9. Gradle selects both the major-version and capability directories from `-PopensearchVersion`. The same pattern applies to test source sets (`src/{test,itTest,smokeTest,scaleTest,benchmarkTest,yamlRestTest}/`). See [Running Tests](running-tests.md) for the test tiers and `opensearch-docker/` for the local cluster.

## Version Compatibility

Most code lives in shared source directories (`src/*/java/`) and compiles against both OpenSearch 2.x and 3.x. Files that use version-specific APIs live in selected compatibility directories. Gradle chooses them based on `-PopensearchVersion`.

**When to use shared vs compat dirs:**

- **Shared** (`java/`): Default location. Use when the code compiles identically against both versions.
- **Compat** (`java-2x/` + `java-3x/`): Use when the file must import a type that moved between versions (e.g., `org.opensearch.client.Client` → `org.opensearch.transport.client.Client`), or when an API signature changed (e.g., `TotalHits.value` field → method).
- **Capability compat** (`java-translog-delete-pre39/` + `java-translog-delete-39plus/`): Use only for the same fully qualified class whose implementation must compile with or without `Translog.Delete.routing()`. The 3.9+ directory is also selected for later OpenSearch versions until another compatibility boundary is needed.

**Compat utilities** abstract the real API differences so most code stays shared:

| Utility | Location | What it abstracts |
|---------|----------|-------------------|
| `AsyncClientHelper` | `main/java-{2,3}x` | Wraps the version-specific `Client`; all shared code receives this instead |
| `OsCompat` | `main/java-{2,3}x` | `TotalHits` field vs method, `storedFields()` accessor |
| `OsTestCompat` | `test/java-{2,3}x` | `ShardStats` constructor (6 vs 7 params) |
| `MockClientFactory` | `test/java-{2,3}x` | Mock `Client`/`AdminClient` creation, `AcknowledgedResponse` |
| `HttpCompat` | `smokeTest,benchmarkTest/java-{2,3}x` | Apache HttpClient 4 vs 5 packages |
| `DeleteRoutingAccessor` | `main/java-translog-delete-{pre39,39plus}` | Availability and direct access to `Translog.Delete.routing()` |

**Drift guard:** CI runs `scripts/check-compat-drift.sh` to verify that non-excepted 2.x/3.x compat file pairs differ only in import/package lines. If you add a real code difference to one of those files, add it to the exceptions list in that script. Capability overlays intentionally contain different implementations and are selected mutually exclusively.

## Main Packages

| Package | Responsibility |
|---------|----------------|
| `action.*` | Transport actions and request/response types. |
| `rest.*` | REST handlers that parse HTTP requests and call transport actions. |
| `model.*` | Migration documents, options, phases, routing mode, and DTOs. |
| `service.coordinator.*` | Cluster-manager-side orchestration and cutover. |
| `service.worker.*` | Data-node shard workers, backfill, replay, retention leases. |
| `service.bulk.*` | Bulk writer pipeline and write controllers. |
| `service.adaptive.*` | AIMD and adaptive control helpers. |
| `transform.*` | Identity and update-script transform functions. |
| `statemachine.*` | Async state-machine framework. |
| `utils.*` | Logging, JSON, async, and OpenSearch helper utilities. |

## Key Classes

| Class | Why it matters |
|-------|----------------|
| `AoscPlugin` | Plugin entry point and component registration. |
| `RestStartMigrationAction` | `POST /_plugins/_aosc/{index}/_start`. |
| `TransportStartMigrationAction` | Start validation and coordinator handoff. |
| `AoscCoordinatorService` | Manages active coordinators on the cluster-manager node. |
| `MigrationCoordinator` | Coordinator phase handlers and cutover orchestration. |
| `AoscShardService` | Creates and manages shard workers on data nodes. |
| `ShardMigrationWorker` | Worker state machine. |
| `BackfillEngine` | Reads source documents and emits target index requests. |
| `TranslogReplayEngine` | Replays source operation history into the target. |
| `TransformFactory` | Builds transform functions from `transform_script`. |
| `AoscSettings` | Cluster setting definitions and defaults. |

## Where Changes Usually Go

| Change | Likely files |
|--------|--------------|
| New REST endpoint | `rest/`, `action/`, REST specs/tests. |
| New start option | `MigrationRequestOptions`, validators, docs, tests. |
| Coordinator phase behavior | `MigrationCoordinator`, `CoordinatorPhase`, state-machine tests. |
| Worker phase behavior | `ShardMigrationWorker`, `ShardPhase`, worker tests. |
| Backfill or replay behavior | `BackfillEngine`, `TranslogReplayEngine`, bulk tests. |
| OpenSearch delete-routing API boundary | `DeleteRoutingAccessor` capability overlays and matching version-selected tests. |
| Transform behavior | `transform/`, `TransformFactory`, transform tests. |
| New setting | `AoscSettings`, configuration docs, tests. |

## Style Notes

- Use `AoscLogger` for plugin logging.
- Keep public wire fields stable and documented.
- Prefer existing async `ActionListener` and `CompletableFuture` bridging patterns.
- Run Spotless before sending a pull request.
