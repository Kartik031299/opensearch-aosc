/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package com.atlassian.opensearch.aosc.model;

import com.atlassian.opensearch.aosc.model.phase.ShardPhase;

import org.opensearch.test.OpenSearchTestCase;

import java.util.EnumSet;
import java.util.Set;

public class ShardPhaseTests extends OpenSearchTestCase {

    private static final Set<ShardPhase> TERMINAL_PHASES = EnumSet.of(ShardPhase.COMPLETED, ShardPhase.CANCELLED, ShardPhase.FAILED);

    private static final Set<ShardPhase> HAPPY_PATH_PHASES = EnumSet.of(
        ShardPhase.PENDING,
        ShardPhase.ACQUIRING_LEASE,
        ShardPhase.BACKFILLING,
        ShardPhase.REPLAYING,
        ShardPhase.CONVERGING,
        ShardPhase.CONVERGED,
        ShardPhase.CATCHING_UP,
        ShardPhase.COMPLETING,
        ShardPhase.COMPLETED
    );

    public void testIsTerminalUnchanged() {
        for (ShardPhase phase : TERMINAL_PHASES) {
            assertTrue(phase + " should be terminal", phase.isTerminal());
        }
        for (ShardPhase phase : ShardPhase.values()) {
            if (!TERMINAL_PHASES.contains(phase)) {
                assertFalse(phase + " should not be terminal", phase.isTerminal());
            }
        }
    }

    public void testIsHappyPath() {
        for (ShardPhase phase : ShardPhase.values()) {
            assertEquals(phase + " happy-path membership", HAPPY_PATH_PHASES.contains(phase), phase.isHappyPath());
        }
        // Interrupt phases are explicitly excluded from the happy path.
        assertFalse(ShardPhase.CANCELLING.isHappyPath());
        assertFalse(ShardPhase.FAILING.isHappyPath());
        assertFalse(ShardPhase.CANCELLED.isHappyPath());
        assertFalse(ShardPhase.FAILED.isHappyPath());
    }

    public void testHappyPathOrderIsStrictlyIncreasing() {
        ShardPhase[] order = {
            ShardPhase.PENDING,
            ShardPhase.ACQUIRING_LEASE,
            ShardPhase.BACKFILLING,
            ShardPhase.REPLAYING,
            ShardPhase.CONVERGING,
            ShardPhase.CONVERGED,
            ShardPhase.CATCHING_UP,
            ShardPhase.COMPLETING,
            ShardPhase.COMPLETED };
        for (int i = 1; i < order.length; i++) {
            assertTrue(order[i - 1] + " < " + order[i], order[i - 1].happyPathOrder() < order[i].happyPathOrder());
        }
        // Interrupt phases are not on the happy path.
        assertEquals(-1, ShardPhase.FAILING.happyPathOrder());
        assertEquals(-1, ShardPhase.CANCELLING.happyPathOrder());
        assertEquals(-1, ShardPhase.FAILED.happyPathOrder());
        assertEquals(-1, ShardPhase.CANCELLED.happyPathOrder());
    }

    // The bug: a delayed COMPLETING arriving after COMPLETED must be recognised as a regression.
    public void testCompletingAfterCompletedIsRegression() {
        assertTrue(ShardPhase.COMPLETING.isHappyPathRegressionOf(ShardPhase.COMPLETED));
    }

    public void testForwardProgressIsNotRegression() {
        assertFalse(ShardPhase.COMPLETED.isHappyPathRegressionOf(ShardPhase.COMPLETING));
        assertFalse(ShardPhase.CATCHING_UP.isHappyPathRegressionOf(ShardPhase.CONVERGED));
        assertFalse(ShardPhase.BACKFILLING.isHappyPathRegressionOf(ShardPhase.ACQUIRING_LEASE));
    }

    public void testSamePhaseIsNotRegression() {
        for (ShardPhase phase : HAPPY_PATH_PHASES) {
            assertFalse(phase + " vs itself", phase.isHappyPathRegressionOf(phase));
        }
    }

    public void testNullCurrentIsNotRegression() {
        assertFalse(ShardPhase.PENDING.isHappyPathRegressionOf(null));
        assertFalse(ShardPhase.COMPLETED.isHappyPathRegressionOf(null));
    }

    // Interrupt phases (fail/cancel) must always be honoured — never treated as regressions
    // in either direction, so a real FAILED/CANCELLED is never dropped by the ordering guard.
    public void testInterruptPhasesAreNeverRegressions() {
        for (ShardPhase happy : HAPPY_PATH_PHASES) {
            assertFalse("FAILED over " + happy, ShardPhase.FAILED.isHappyPathRegressionOf(happy));
            assertFalse("FAILING over " + happy, ShardPhase.FAILING.isHappyPathRegressionOf(happy));
            assertFalse("CANCELLED over " + happy, ShardPhase.CANCELLED.isHappyPathRegressionOf(happy));
            assertFalse("CANCELLING over " + happy, ShardPhase.CANCELLING.isHappyPathRegressionOf(happy));
            // A happy-path update on top of an interrupt phase is also not a "happy-path regression".
            assertFalse(happy + " over FAILED", happy.isHappyPathRegressionOf(ShardPhase.FAILED));
        }
    }
}
