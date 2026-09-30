/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package com.atlassian.opensearch.aosc.compat;

import org.opensearch.index.translog.Translog;

/** OpenSearch compatibility boundary for translog delete routing. */
public final class DeleteRoutingAccessor {

    private DeleteRoutingAccessor() {}

    public static boolean isAvailable() {
        return true;
    }

    public static String read(Translog.Delete delete) {
        return delete.routing();
    }
}
