/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package com.atlassian.opensearch.aosc.service.coordinator;

import com.atlassian.opensearch.aosc.model.MigrationDocument;
import com.atlassian.opensearch.aosc.model.MigrationRequestOptions;
import com.atlassian.opensearch.aosc.model.ShardRoutingMode;
import com.atlassian.opensearch.aosc.model.phase.CoordinatorPhase;
import com.atlassian.opensearch.aosc.utils.AoscLogger;

import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.index.engine.VersionConflictEngineException;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.mockito.invocation.InvocationOnMock;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MigrationDocumentServiceTests extends OpenSearchTestCase {

    // ---- Constants ----
    public void testMigrationsIndexConstant() {
        assertEquals(".aosc-migrations", MigrationDocumentService.MIGRATIONS_INDEX);
    }

    public void testSchemaResourceConstant() {
        assertEquals("/aosc-migrations-schema.json", MigrationDocumentService.SCHEMA_RESOURCE);
    }

    public void testConstructorRejectsNullClient() {
        expectThrows(
            NullPointerException.class,
            () -> new MigrationDocumentService(AoscLogger.create(MigrationDocumentService.class), null)
        );
    }

    // ---- Monolith document: no child doc ID scheme ----
    // shardProgressDocId was removed — shard progress is now embedded in the migration document.

    // ---- Schema resource ----
    public void testSchemaResourceIsLoadable() {
        assertNotNull(MigrationDocumentService.class.getResourceAsStream(MigrationDocumentService.SCHEMA_RESOURCE));
    }

    // ---- B047: createMigrationDocument must be idempotent across CM failover ----

    /**
     * Regression test for B047: when the previous CM persisted the migration document
     * and then died before publishing the {@code INITIALIZING → ACTIVE} cluster-state
     * update, the new CM re-enters {@code onInitializing} via {@code justBecameCM} and
     * tries to create the same document again. The service must treat the resulting
     * {@link VersionConflictEngineException} as success rather than failing the
     * migration.
     */
    public void testCreateMigrationDocumentSwallowsVersionConflictOnReentry() throws Exception {
        Client client = mockClient();
        AtomicInteger calls = new AtomicInteger();
        doAnswer((InvocationOnMock invocation) -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            int call = calls.incrementAndGet();
            if (call == 1) {
                // Simulate the first CM successfully creating the document.
                listener.onResponse(mock(IndexResponse.class));
            } else {
                // Simulate the second CM hitting create=true after failover.
                listener.onFailure(
                    new VersionConflictEngineException(new ShardId(".aosc-migrations", "_na_", 0), "mig-1", "document already exists")
                );
            }
            return null;
        }).when(client).index(any(IndexRequest.class), any());

        MigrationDocumentService service = newService(client);

        MigrationDocument doc = newDocument("mig-1");

        // First create succeeds.
        MigrationDocument first = await(service.createMigrationDocument(doc));
        assertEquals("mig-1", first.migrationId());

        // Second create (post-failover re-entry) must NOT throw — must return the same doc.
        MigrationDocument second = await(service.createMigrationDocument(doc));
        assertEquals("mig-1", second.migrationId());
        assertEquals(2, calls.get());
    }

    public void testCreateMigrationDocumentPropagatesNonVersionConflictErrors() {
        Client client = mockClient();
        doAnswer((InvocationOnMock invocation) -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("boom"));
            return null;
        }).when(client).index(any(IndexRequest.class), any());

        MigrationDocumentService service = newService(client);
        MigrationDocument doc = newDocument("mig-2");

        ExecutionException ex = expectThrows(ExecutionException.class, () -> service.createMigrationDocument(doc).get(5, TimeUnit.SECONDS));
        assertTrue("expected the original RuntimeException to propagate, got: " + ex.getCause(), ex.getCause() instanceof RuntimeException);
        assertEquals("boom", ex.getCause().getMessage());
    }

    // ---- helpers ----

    private static MigrationDocumentService newService(Client client) {
        return new MigrationDocumentService(AoscLogger.create(MigrationDocumentService.class), client);
    }

    private static Client mockClient() {
        Client client = mock(Client.class);
        ThreadContext threadContext = new ThreadContext(Settings.EMPTY);
        org.opensearch.threadpool.ThreadPool threadPool = mock(ThreadPool.class);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        when(client.threadPool()).thenReturn(threadPool);
        return client;
    }

    private static MigrationDocument newDocument(String id) {
        return MigrationDocument.builder()
            .migrationId(id)
            .sourceIndex("src")
            .targetIndex("tgt")
            .alias("a")
            .phase(CoordinatorPhase.INITIALIZING)
            .options(new MigrationRequestOptions())
            .shardRoutingMode(ShardRoutingMode.BULK_API)
            .startTimeMillis(1L)
            .lastUpdatedMillis(1L)
            .build();
    }

    private static <T> T await(CompletableFuture<T> future) throws InterruptedException, ExecutionException, TimeoutException {
        return future.get(5, TimeUnit.SECONDS);
    }

}
