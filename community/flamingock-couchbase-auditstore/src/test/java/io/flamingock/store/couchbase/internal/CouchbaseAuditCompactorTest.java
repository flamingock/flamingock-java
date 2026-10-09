/*
 * Copyright 2026 Flamingock (https://www.flamingock.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.flamingock.store.couchbase.internal;

import com.couchbase.client.core.error.DocumentNotFoundException;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.query.QueryOptions;
import com.couchbase.client.java.query.QueryResult;
import com.couchbase.client.java.transactions.TransactionAttemptContext;
import com.couchbase.client.java.transactions.TransactionGetResult;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.internal.common.core.context.Dependency;
import io.flamingock.internal.common.core.context.RuntimeContext;
import io.flamingock.internal.common.core.external.ExecutionWrapper;
import io.flamingock.internal.common.couchbase.CouchbaseAuditMapper;
import io.flamingock.internal.util.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit coverage for {@link CouchbaseAuditCompactor} against a mocked {@code Cluster}/{@code Collection}/
 * {@code TransactionAttemptContext}.
 * <p>
 * Deliberately Docker-free. Couchbase rekeys like DynamoDB, so the properties that matter most mirror
 * DynamoDB's bar: the right survivor is selected, an already-rekeyed single document is left alone, a
 * half-finished rekey (survivor already at the changeId, old ledger row still present) converges, and a
 * failure on one change stops the rest without touching other changes. The container-backed
 * {@code CouchbaseAuditCompactionConformanceTest} covers the end state, the actual rekey, and the shared
 * contract.
 */
class CouchbaseAuditCompactorTest {

    private static final String CHANGE_A = "change-a";
    private static final String CHANGE_B = "change-b";
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 1, 1, 12, 0, 0);
    private static final LocalDateTime T1 = LocalDateTime.of(2026, 1, 1, 12, 5, 0);
    private static final CouchbaseAuditMapper MAPPER = new CouchbaseAuditMapper();

    @Test
    @DisplayName("a change already in current-state form is not rewritten")
    void alreadyCurrentStateFormIsSkipped() {
        Collection collection = mockCollection();
        Cluster cluster = clusterReturning(collection,
                ledgerRow(CHANGE_A, CHANGE_A, "exec-1", AuditEntry.Status.APPLIED, T0));
        // No transaction may even be opened for an already-compacted change - a plain mock with no
        // stubbing and a verifyNoInteractions check is what actually enforces that, rather than checking
        // a txContext that would stay untouched anyway even if a no-op transaction were opened.
        ExecutionWrapper txWrapper = Mockito.mock(ExecutionWrapper.class);

        assertOk(new CouchbaseAuditCompactor(cluster, collection, txWrapper).compact());

        Mockito.verifyNoInteractions(txWrapper);
    }

    @Test
    @DisplayName("an empty collection compacts without touching anything")
    void emptyCollectionIsANoOp() {
        Collection collection = mockCollection();
        Cluster cluster = clusterReturning(collection);
        ExecutionWrapper txWrapper = Mockito.mock(ExecutionWrapper.class);

        assertOk(new CouchbaseAuditCompactor(cluster, collection, txWrapper).compact());

        Mockito.verifyNoInteractions(txWrapper);
    }

    @Test
    @DisplayName("on an exact createdAt tie the higher status priority survives")
    void winnerIsChosenByStatusPriorityOnTimestampTie() {
        Collection collection = mockCollection();
        Cluster cluster = clusterReturning(collection,
                ledgerRow(CHANGE_A, "exec-1#" + CHANGE_A + "#APPLIED", "exec-1", AuditEntry.Status.APPLIED, T0),
                ledgerRow(CHANGE_A, "exec-1#" + CHANGE_A + "#ROLLED_BACK", "exec-1", AuditEntry.Status.ROLLED_BACK, T0));
        TransactionAttemptContext txContext = Mockito.mock(TransactionAttemptContext.class);
        Mockito.when(txContext.get(collection, CHANGE_A)).thenThrow(new DocumentNotFoundException(null));
        ExecutionWrapper txWrapper = passthroughWrapper(txContext);

        assertOk(new CouchbaseAuditCompactor(cluster, collection, txWrapper).compact());

        ArgumentCaptor<Object> written = ArgumentCaptor.forClass(Object.class);
        Mockito.verify(txContext).insert(Mockito.eq(collection), Mockito.eq(CHANGE_A), written.capture());
        JsonObject survivorDoc = (JsonObject) written.getValue();
        assertEquals(AuditEntry.Status.ROLLED_BACK.name(), survivorDoc.getString("state"),
                "selecting on createdAt alone would resolve this tie arbitrarily");
    }

    @Test
    @DisplayName("the survivor is inserted at the changeId when not already present, and superseded rows deleted")
    void survivorInsertedAndSupersededRowsDeleted() {
        Collection collection = mockCollection();
        String ledgerKeyStarted = "exec-1#" + CHANGE_A + "#STARTED";
        String ledgerKeyApplied = "exec-1#" + CHANGE_A + "#APPLIED";
        Cluster cluster = clusterReturning(collection,
                ledgerRow(CHANGE_A, ledgerKeyStarted, "exec-1", AuditEntry.Status.STARTED, T0),
                ledgerRow(CHANGE_A, ledgerKeyApplied, "exec-1", AuditEntry.Status.APPLIED, T1));
        TransactionAttemptContext txContext = Mockito.mock(TransactionAttemptContext.class);
        Mockito.when(txContext.get(collection, CHANGE_A)).thenThrow(new DocumentNotFoundException(null));
        TransactionGetResult startedHandle = Mockito.mock(TransactionGetResult.class);
        TransactionGetResult appliedHandle = Mockito.mock(TransactionGetResult.class);
        Mockito.when(txContext.get(collection, ledgerKeyStarted)).thenReturn(startedHandle);
        Mockito.when(txContext.get(collection, ledgerKeyApplied)).thenReturn(appliedHandle);
        ExecutionWrapper txWrapper = passthroughWrapper(txContext);

        assertOk(new CouchbaseAuditCompactor(cluster, collection, txWrapper).compact());

        Mockito.verify(txContext).insert(Mockito.eq(collection), Mockito.eq(CHANGE_A), Mockito.any());
        Mockito.verify(txContext).remove(startedHandle);
        Mockito.verify(txContext).remove(appliedHandle);
    }

    @Test
    @DisplayName("the survivor is replaced in place when a current-state document already exists")
    void survivorReplacedWhenCurrentStateDocumentExists() {
        Collection collection = mockCollection();
        String ledgerKey = "exec-2#" + CHANGE_A + "#APPLIED";
        Cluster cluster = clusterReturning(collection,
                ledgerRow(CHANGE_A, CHANGE_A, "exec-1", AuditEntry.Status.APPLIED, T0),
                ledgerRow(CHANGE_A, ledgerKey, "exec-2", AuditEntry.Status.APPLIED, T1));
        TransactionAttemptContext txContext = Mockito.mock(TransactionAttemptContext.class);
        TransactionGetResult existingCurrentState = Mockito.mock(TransactionGetResult.class);
        TransactionGetResult ledgerHandle = Mockito.mock(TransactionGetResult.class);
        Mockito.when(txContext.get(collection, CHANGE_A)).thenReturn(existingCurrentState);
        Mockito.when(txContext.get(collection, ledgerKey)).thenReturn(ledgerHandle);
        ExecutionWrapper txWrapper = passthroughWrapper(txContext);

        assertOk(new CouchbaseAuditCompactor(cluster, collection, txWrapper).compact());

        Mockito.verify(txContext).replace(Mockito.eq(existingCurrentState), Mockito.any());
        Mockito.verify(txContext).remove(ledgerHandle);
        Mockito.verify(txContext, Mockito.never()).insert(Mockito.any(), Mockito.anyString(), Mockito.any());
    }

    @Test
    @DisplayName("a change left half-rekeyed by an interrupted run is finished off")
    void compactsAChangeAlreadyPartiallyRekeyed() {
        Collection collection = mockCollection();
        String leftoverLedgerKey = "exec-1#" + CHANGE_A + "#APPLIED";
        Cluster cluster = clusterReturning(collection,
                ledgerRow(CHANGE_A, CHANGE_A, "exec-1", AuditEntry.Status.APPLIED, T1),
                ledgerRow(CHANGE_A, leftoverLedgerKey, "exec-1", AuditEntry.Status.APPLIED, T1));
        TransactionAttemptContext txContext = Mockito.mock(TransactionAttemptContext.class);
        TransactionGetResult leftoverHandle = Mockito.mock(TransactionGetResult.class);
        Mockito.when(txContext.get(collection, leftoverLedgerKey)).thenReturn(leftoverHandle);
        ExecutionWrapper txWrapper = passthroughWrapper(txContext);

        assertOk(new CouchbaseAuditCompactor(cluster, collection, txWrapper).compact());

        // The survivor is already at the changeId key - no write needed, only the leftover ledger row goes.
        Mockito.verify(txContext, Mockito.never()).insert(Mockito.any(), Mockito.anyString(), Mockito.any());
        Mockito.verify(txContext, Mockito.never()).replace(Mockito.any(), Mockito.any());
        Mockito.verify(txContext).remove(leftoverHandle);
    }

    @Test
    @DisplayName("a single document still at its ledger key is rekeyed, not skipped as already current-state")
    void singleLedgerKeyedDocumentIsStillRekeyed() {
        Collection collection = mockCollection();
        String ledgerKey = "exec-1#" + CHANGE_A + "#APPLIED";
        Cluster cluster = clusterReturning(collection,
                ledgerRow(CHANGE_A, ledgerKey, "exec-1", AuditEntry.Status.APPLIED, T0));
        TransactionAttemptContext txContext = Mockito.mock(TransactionAttemptContext.class);
        Mockito.when(txContext.get(collection, CHANGE_A)).thenThrow(new DocumentNotFoundException(null));
        TransactionGetResult ledgerHandle = Mockito.mock(TransactionGetResult.class);
        Mockito.when(txContext.get(collection, ledgerKey)).thenReturn(ledgerHandle);
        ExecutionWrapper txWrapper = passthroughWrapper(txContext);

        assertOk(new CouchbaseAuditCompactor(cluster, collection, txWrapper).compact());

        // A single record whose physical key is still the ledger key, not the changeId, must be rekeyed:
        // treating "one record" as "already current-state" (dropping the key check) would skip this and
        // leave it un-rekeyed, so a later current-state write would insert a second document at changeId.
        Mockito.verify(txContext).insert(Mockito.eq(collection), Mockito.eq(CHANGE_A), Mockito.any());
        Mockito.verify(txContext).remove(ledgerHandle);
    }

    @Test
    @DisplayName("the first failing change stops the rest")
    void failFastStopsAtTheFirstFailingChange() {
        Collection collection = mockCollection();
        Cluster cluster = clusterReturning(collection,
                ledgerRow(CHANGE_A, "exec-1#" + CHANGE_A + "#STARTED", "exec-1", AuditEntry.Status.STARTED, T0),
                ledgerRow(CHANGE_A, "exec-1#" + CHANGE_A + "#APPLIED", "exec-1", AuditEntry.Status.APPLIED, T1),
                ledgerRow(CHANGE_B, "exec-1#" + CHANGE_B + "#STARTED", "exec-1", AuditEntry.Status.STARTED, T0),
                ledgerRow(CHANGE_B, "exec-1#" + CHANGE_B + "#APPLIED", "exec-1", AuditEntry.Status.APPLIED, T1));
        ExecutionWrapper txWrapper = Mockito.mock(ExecutionWrapper.class);
        Mockito.when(txWrapper.wrapExecution(Mockito.any(), Mockito.any()))
                .thenThrow(new IllegalStateException("transaction failed"));

        Result result = new CouchbaseAuditCompactor(cluster, collection, txWrapper).compact();

        assertTrue(result.isError());
        Mockito.verify(txWrapper, Mockito.times(1)).wrapExecution(Mockito.any(), Mockito.any());
    }

    @Test
    @DisplayName("a document with no changeId fails with an actionable message, touching nothing")
    void documentWithBlankChangeIdFailsWithAClearMessage() {
        Collection collection = mockCollection();
        JsonObject corrupt = MAPPER.toDocument(entry("exec-1", CHANGE_A, AuditEntry.Status.APPLIED, T0));
        corrupt.removeKey("changeId");
        corrupt.put("id", "corrupt-key");
        Cluster cluster = Mockito.mock(Cluster.class);
        QueryResult queryResult = Mockito.mock(QueryResult.class);
        Mockito.when(queryResult.rowsAsObject()).thenReturn(singletonRow(corrupt));
        Mockito.when(cluster.query(Mockito.anyString(), Mockito.any(QueryOptions.class))).thenReturn(queryResult);
        ExecutionWrapper txWrapper = Mockito.mock(ExecutionWrapper.class);

        Result result = new CouchbaseAuditCompactor(cluster, collection, txWrapper).compact();

        assertTrue(result.isError());
        assertTrue(((Result.Error) result).getError().getMessage().contains("corrupt-key"),
                "the error must name the offending document: " + ((Result.Error) result).getError().getMessage());
        Mockito.verifyNoInteractions(txWrapper);
    }

    @Test
    @DisplayName("a ledger key that is also another change's id is never deleted")
    void ledgerKeyCollidingWithAnotherChangeIdIsNotDeleted() {
        Collection collection = mockCollection();
        String collidingId = "exec-1#" + CHANGE_A + "#APPLIED";
        Cluster cluster = clusterReturning(collection,
                ledgerRow(CHANGE_A, "exec-1#" + CHANGE_A + "#STARTED", "exec-1", AuditEntry.Status.STARTED, T0),
                ledgerRow(CHANGE_A, collidingId, "exec-1", AuditEntry.Status.APPLIED, T1),
                ledgerRow(collidingId, collidingId, "exec-9", AuditEntry.Status.APPLIED, T0));
        TransactionAttemptContext txContext = Mockito.mock(TransactionAttemptContext.class);
        Mockito.when(txContext.get(Mockito.eq(collection), Mockito.anyString()))
                .thenAnswer(invocation -> Mockito.mock(TransactionGetResult.class));
        Mockito.when(txContext.get(collection, CHANGE_A)).thenThrow(new DocumentNotFoundException(null));
        Mockito.when(txContext.get(collection, collidingId)).thenThrow(new DocumentNotFoundException(null));
        ExecutionWrapper txWrapper = passthroughWrapper(txContext);

        assertOk(new CouchbaseAuditCompactor(cluster, collection, txWrapper).compact());

        ArgumentCaptor<TransactionGetResult> removed = ArgumentCaptor.forClass(TransactionGetResult.class);
        Mockito.verify(txContext, Mockito.atLeastOnce()).remove(removed.capture());
        // collidingId's own document must never be fetched-and-removed while compacting change-a.
        Mockito.verify(txContext, Mockito.never()).get(collection, collidingId);
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private static Collection mockCollection() {
        Collection collection = Mockito.mock(Collection.class);
        Mockito.when(collection.name()).thenReturn("flamingockAuditLog");
        Mockito.when(collection.bucketName()).thenReturn("test-bucket");
        Mockito.when(collection.scopeName()).thenReturn("_default");
        return collection;
    }

    private static Cluster clusterReturning(Collection collection, JsonObject... rows) {
        Cluster cluster = Mockito.mock(Cluster.class);
        QueryResult queryResult = Mockito.mock(QueryResult.class);
        Mockito.when(queryResult.rowsAsObject()).thenReturn(new ArrayList<>(Arrays.asList(rows)));
        Mockito.when(cluster.query(Mockito.anyString(), Mockito.any(QueryOptions.class))).thenReturn(queryResult);
        return cluster;
    }

    private static List<JsonObject> singletonRow(JsonObject row) {
        List<JsonObject> rows = new ArrayList<>();
        rows.add(row);
        return rows;
    }

    /** A row as the compactor's own {@code META().id} query would return it: document fields plus {@code id}. */
    private static JsonObject ledgerRow(String changeId, String id, String executionId,
                                        AuditEntry.Status status, LocalDateTime createdAt) {
        JsonObject document = MAPPER.toDocument(entry(executionId, changeId, status, createdAt));
        return document.put("id", id);
    }

    private static AuditEntry entry(String executionId, String changeId, AuditEntry.Status status, LocalDateTime createdAt) {
        return new AuditEntry(
                executionId,
                "test-stage",
                changeId,
                "test-author",
                createdAt,
                status,
                AuditEntry.ChangeType.STANDARD_CODE,
                "TestChangeClass",
                "testMethod",
                "TestSourceFile",
                0L,
                "localhost",
                null,
                false,
                null,
                AuditTxType.NON_TX,
                "test-target-system",
                "001",
                io.flamingock.api.RecoveryStrategy.MANUAL_INTERVENTION,
                null);
    }

    private static ExecutionWrapper passthroughWrapper(TransactionAttemptContext txContext) {
        ExecutionWrapper txWrapper = Mockito.mock(ExecutionWrapper.class);
        Mockito.when(txWrapper.wrapExecution(Mockito.any(), Mockito.any()))
                .thenAnswer(invocation -> applyWithMockTxContext(invocation, txContext));
        return txWrapper;
    }

    @SuppressWarnings("unchecked")
    private static Object applyWithMockTxContext(InvocationOnMock invocation, TransactionAttemptContext txContext) {
        RuntimeContext runtimeContext = invocation.getArgument(0);
        runtimeContext.addDependency(new Dependency(txContext));
        Function<RuntimeContext, Object> operation = invocation.getArgument(1);
        return operation.apply(runtimeContext);
    }

    private static void assertOk(Result result) {
        if (result.isError()) {
            throw new AssertionError("compact() failed: " + ((Result.Error) result).getError());
        }
    }
}
