/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package com.atlassian.opensearch.aosc.model.phase;

import java.util.List;
import java.util.Set;

/**
 * Per-shard worker lifecycle phases. Persisted in cluster state (Tier 0)
 * as part of AoscMigrationsClusterState.ShardMigrationClusterState.
 *
 * 12 states: linear pipeline with convergence loop, plus cancel/fail edges.
 */
public enum ShardPhase {
    PENDING,
    ACQUIRING_LEASE,
    BACKFILLING,
    REPLAYING,
    CONVERGING,
    CONVERGED,
    CATCHING_UP,
    COMPLETING,
    COMPLETED,
    CANCELLING,
    FAILING,
    CANCELLED,
    FAILED;

    /** Terminal phases: no further transitions are possible. */
    public static final Set<ShardPhase> TERMINALS = Set.of(COMPLETED, CANCELLED, FAILED);

    // Happy-path progression, in order. Declared explicitly (not derived from enum ordinal) so
    // reordering constants or adding interrupt states can't silently change progression semantics.
    private static final List<ShardPhase> HAPPY_PATH = List.of(
        PENDING,
        ACQUIRING_LEASE,
        BACKFILLING,
        REPLAYING,
        CONVERGING,
        CONVERGED,
        CATCHING_UP,
        COMPLETING,
        COMPLETED
    );

    /** Returns true if this is a terminal state (no further transitions). */
    public boolean isTerminal() {
        return TERMINALS.contains(this);
    }

    /** True if this phase is part of the linear happy-path progression (not an interrupt phase). */
    public boolean isHappyPath() {
        return HAPPY_PATH.contains(this);
    }

    /** Position in the happy-path progression, or {@code -1} for interrupt phases. */
    public int happyPathOrder() {
        return HAPPY_PATH.indexOf(this);
    }

    /**
     * True if recording {@code this} for a shard already at {@code current} would move it backward
     * along the happy path — a stale, out-of-order update to ignore (e.g. COMPLETING after COMPLETED).
     * Interrupt phases are never regressions, so fail/cancel are always honoured.
     */
    public boolean isHappyPathRegressionOf(ShardPhase current) {
        if (current == null) {
            return false;
        }
        return this.isHappyPath() && current.isHappyPath() && this.happyPathOrder() < current.happyPathOrder();
    }

}
