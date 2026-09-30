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
import org.opensearch.test.OpenSearchTestCase;

import java.util.List;

public class TranslogDeleteOperationRouterTests extends OpenSearchTestCase {

    public void testRoutesCustomRoutingExactlyOnce() {
        Translog.Delete delete = new Translog.Delete("doc", 1, 1, 2, "tenant-7");

        List<DeleteRequest> requests = router().route(delete);

        assertTrue(DeleteRoutingAccessor.isAvailable());
        assertEquals("tenant-7", DeleteRoutingAccessor.read(delete));
        assertEquals(1, requests.size());
        assertEquals("doc", requests.get(0).id());
        assertEquals("tenant-7", requests.get(0).routing());
    }

    public void testRoutesNullRoutingExactlyOnce() {
        Translog.Delete delete = new Translog.Delete("doc", 1, 1, 2, null);

        List<DeleteRequest> requests = router().route(delete);

        assertNull(DeleteRoutingAccessor.read(delete));
        assertEquals(1, requests.size());
        assertNull(requests.get(0).routing());
    }

    private static DeleteOperationRouter router() {
        return new DeleteOperationRouter(
            DeleteRoutingStrategy.TRANSLOG_ROUTING,
            "target",
            ShardRoutingMode.SPLIT_SHARD,
            2,
            new String[] { "ignored" },
            0
        );
    }
}
