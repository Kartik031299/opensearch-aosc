/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package com.atlassian.opensearch.aosc.compat;

import com.atlassian.opensearch.aosc.model.DeleteRoutingStrategy;
import com.atlassian.opensearch.aosc.model.ShardRoutingMode;
import com.atlassian.opensearch.aosc.service.worker.routing.DeleteOperationRouter;

import org.opensearch.index.translog.Translog;
import org.opensearch.test.OpenSearchTestCase;

public class DeleteRoutingAccessorTests extends OpenSearchTestCase {

    public void testUnavailableAndFailsClosed() {
        assertFalse(DeleteRoutingAccessor.isAvailable());
        expectThrows(UnsupportedOperationException.class, () -> DeleteRoutingAccessor.read(new Translog.Delete("doc", 1, 1)));
        expectThrows(
            IllegalStateException.class,
            () -> new DeleteOperationRouter(DeleteRoutingStrategy.TRANSLOG_ROUTING, "target", ShardRoutingMode.BULK_API, 2, null, 0)
        );
    }
}
