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
package io.flamingock.store.dynamodb.internal;

import io.flamingock.api.RecoveryStrategy;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.internal.util.Result;
import io.flamingock.internal.util.dynamodb.entities.AuditEntryEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit coverage for {@link DynamoDBAuditCompactor} against a mocked {@code DynamoDbTable}.
 * <p>
 * Deliberately Docker-free. Compaction is destructive and partially non-atomic, and the properties that
 * matter most — that the survivor is written before anything is deleted, that every scan page is processed,
 * that a failure cannot delete first — are all about the <em>sequence of calls</em> the compactor makes.
 * A mocked table observes that sequence directly and deterministically, where a container can only let it be
 * inferred from the end state. The container-backed
 * {@code io.flamingock.store.dynamodb.DynamoDBAuditCompactionConformanceTest} covers the end state and the
 * shared contract.
 * <p>
 * Hand-building the scan result follows the precedent in
 * {@link DynamoDBJournalEventStoreTest} ({@code PageIterable.create} over {@code Page.create}).
 */
class DynamoDBAuditCompactorTest {

    private static final String CHANGE_A = "change-a";
    private static final String CHANGE_B = "change-b";
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 1, 1, 12, 0, 0);
    private static final LocalDateTime T1 = LocalDateTime.of(2026, 1, 1, 12, 5, 0);

    @Test
    @DisplayName("the survivor is written before any superseded record is deleted")
    void putHappensBeforeDeletes() {
        DynamoDbTable<AuditEntryEntity> table = tableReturning(
                ledger(CHANGE_A, "exec-1", AuditEntry.Status.STARTED, T0),
                ledger(CHANGE_A, "exec-1", AuditEntry.Status.APPLIED, T1));

        assertOk(new DynamoDBAuditCompactor(table).compact());

        InOrder inOrder = Mockito.inOrder(table);
        inOrder.verify(table).putItem(Mockito.any(AuditEntryEntity.class));
        inOrder.verify(table, Mockito.times(2)).deleteItem(Mockito.any(Key.class));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    @DisplayName("the scan is strongly consistent")
    void scanUsesConsistentRead() {
        DynamoDbTable<AuditEntryEntity> table = tableReturning(
                ledger(CHANGE_A, "exec-1", AuditEntry.Status.APPLIED, T0));

        assertOk(new DynamoDBAuditCompactor(table).compact());

        ArgumentCaptor<ScanEnhancedRequest> captor = ArgumentCaptor.forClass(ScanEnhancedRequest.class);
        Mockito.verify(table).scan(captor.capture());
        ScanEnhancedRequest request = captor.getValue();
        assertEquals(Boolean.TRUE, request.consistentRead(),
                "An eventually consistent scan could miss a change's latest record, write an earlier one as"
                        + " the current state and delete the record it missed");
        // A limit or a filter would leave part of the table uncompacted and still report success, which is
        // the quietest way compaction could be wrong. Compaction is total by contract.
        assertEquals(null, request.limit(), "the scan must not be bounded");
        assertEquals(null, request.filterExpression(), "compaction covers every change in the table");
    }

    @Test
    @DisplayName("every scan page is processed, not just the first")
    void compactionProcessesEveryScanPage() {
        // change-b exists only on the second page. Processing one page would leave it uncompacted and still
        // report success, which is the quietest way this could go wrong.
        Page<AuditEntryEntity> first = Page.create(
                Collections.singletonList(ledger(CHANGE_A, "exec-1", AuditEntry.Status.APPLIED, T0)),
                continuationToken());
        Page<AuditEntryEntity> second = Page.create(Arrays.asList(
                ledger(CHANGE_B, "exec-1", AuditEntry.Status.STARTED, T0),
                ledger(CHANGE_B, "exec-1", AuditEntry.Status.APPLIED, T1)));
        DynamoDbTable<AuditEntryEntity> table = tableReturningPages(first, second);

        assertOk(new DynamoDBAuditCompactor(table).compact());

        ArgumentCaptor<AuditEntryEntity> written = ArgumentCaptor.forClass(AuditEntryEntity.class);
        Mockito.verify(table, Mockito.times(2)).putItem(written.capture());
        List<String> compactedChanges = new ArrayList<>();
        for (AuditEntryEntity entity : written.getAllValues()) {
            compactedChanges.add(entity.getPartitionKey());
        }
        assertTrue(compactedChanges.contains(CHANGE_B),
                "change-b lives only on the second scan page and was not compacted: " + compactedChanges);
    }

    @Test
    @DisplayName("the survivor is the stored record itself, with only its partition key changed")
    void survivorKeepsEveryAttribute() {
        AuditEntryEntity applied = ledger(CHANGE_A, "exec-1", AuditEntry.Status.APPLIED, T1);
        DynamoDbTable<AuditEntryEntity> table = tableReturning(
                ledger(CHANGE_A, "exec-1", AuditEntry.Status.STARTED, T0), applied);

        assertOk(new DynamoDBAuditCompactor(table).compact());

        ArgumentCaptor<AuditEntryEntity> written = ArgumentCaptor.forClass(AuditEntryEntity.class);
        Mockito.verify(table).putItem(written.capture());
        // Identity, not equality, and that is the whole point: rebuilding the entity from its AuditEntry
        // would reset the partition key and materialise defaults for attributes the stored record never had
        // (txStrategy -> NON_TX, recoveryStrategy -> MANUAL_INTERVENTION). Asserting the very instance is
        // written is the only way to rule that out, since no field-by-field comparison can distinguish a
        // faithful copy from a laundered one.
        assertSame(applied, written.getValue(), "the stored record itself must be rewritten, not a copy");
        assertEquals(CHANGE_A, written.getValue().getPartitionKey());
    }

    @Test
    @DisplayName("on an exact createdAt tie the higher status priority survives")
    void winnerIsChosenByStatusPriorityOnTimestampTie() {
        AuditEntryEntity applied = ledger(CHANGE_A, "exec-1", AuditEntry.Status.APPLIED, T0);
        AuditEntryEntity rolledBack = ledger(CHANGE_A, "exec-1", AuditEntry.Status.ROLLED_BACK, T0);
        DynamoDbTable<AuditEntryEntity> table = tableReturning(applied, rolledBack);

        assertOk(new DynamoDBAuditCompactor(table).compact());

        ArgumentCaptor<AuditEntryEntity> written = ArgumentCaptor.forClass(AuditEntryEntity.class);
        Mockito.verify(table).putItem(written.capture());
        assertEquals(AuditEntry.Status.ROLLED_BACK.name(), written.getValue().getState(),
                "selecting on createdAt alone would resolve this tie arbitrarily");
    }

    @Test
    @DisplayName("a change already in current-state form is not rewritten")
    void alreadyCurrentStateFormIsSkipped() {
        AuditEntryEntity current = ledger(CHANGE_A, "exec-1", AuditEntry.Status.APPLIED, T0);
        current.setPartitionKey(CHANGE_A);
        DynamoDbTable<AuditEntryEntity> table = tableReturning(current);

        assertOk(new DynamoDBAuditCompactor(table).compact());

        Mockito.verify(table, Mockito.never()).putItem(Mockito.any(AuditEntryEntity.class));
        Mockito.verify(table, Mockito.never()).deleteItem(Mockito.any(Key.class));
    }

    @Test
    @DisplayName("a failed write deletes nothing")
    void failureOnPutAbortsWithoutDeleting() {
        DynamoDbTable<AuditEntryEntity> table = tableReturning(
                ledger(CHANGE_A, "exec-1", AuditEntry.Status.STARTED, T0),
                ledger(CHANGE_A, "exec-1", AuditEntry.Status.APPLIED, T1));
        Mockito.doThrow(new IllegalStateException("put failed"))
                .when(table).putItem(Mockito.any(AuditEntryEntity.class));

        Result result = new DynamoDBAuditCompactor(table).compact();

        assertTrue(result.isError(), "a failed survivor write must be reported");
        Mockito.verify(table, Mockito.never()).deleteItem(Mockito.any(Key.class));
    }

    @Test
    @DisplayName("a failed delete is reported, but the survivor is already safe")
    void failureOnDeleteReportsErrorAfterSurvivorIsSafe() {
        DynamoDbTable<AuditEntryEntity> table = tableReturning(
                ledger(CHANGE_A, "exec-1", AuditEntry.Status.STARTED, T0),
                ledger(CHANGE_A, "exec-1", AuditEntry.Status.APPLIED, T1));
        Mockito.doThrow(new IllegalStateException("delete failed"))
                .when(table).deleteItem(Mockito.any(Key.class));

        Result result = new DynamoDBAuditCompactor(table).compact();

        assertTrue(result.isError());
        // The duplicate left behind is recoverable; a missing record would not be.
        Mockito.verify(table).putItem(Mockito.any(AuditEntryEntity.class));
    }

    @Test
    @DisplayName("the first failing change stops the rest")
    void failFastStopsAtTheFirstFailingChange() {
        DynamoDbTable<AuditEntryEntity> table = tableReturning(
                ledger(CHANGE_A, "exec-1", AuditEntry.Status.STARTED, T0),
                ledger(CHANGE_A, "exec-1", AuditEntry.Status.APPLIED, T1),
                ledger(CHANGE_B, "exec-1", AuditEntry.Status.STARTED, T0),
                ledger(CHANGE_B, "exec-1", AuditEntry.Status.APPLIED, T1));
        Mockito.doThrow(new IllegalStateException("put failed"))
                .when(table).putItem(Mockito.any(AuditEntryEntity.class));

        Result result = new DynamoDBAuditCompactor(table).compact();

        assertTrue(result.isError());
        // One attempt only: change-b must not have been touched after change-a failed.
        Mockito.verify(table, Mockito.times(1)).putItem(Mockito.any(AuditEntryEntity.class));
        Mockito.verify(table, Mockito.never()).deleteItem(Mockito.any(Key.class));
    }

    @Test
    @DisplayName("a record with no changeId fails with a message naming it, deleting nothing")
    void recordWithBlankChangeIdFailsWithAClearMessage() {
        AuditEntryEntity corrupt = ledger(CHANGE_A, "exec-1", AuditEntry.Status.APPLIED, T0);
        corrupt.setChangeId(null);
        corrupt.setPartitionKey("corrupt-key");
        DynamoDbTable<AuditEntryEntity> table = tableReturning(corrupt);

        Result result = new DynamoDBAuditCompactor(table).compact();

        assertTrue(result.isError());
        assertTrue(((Result.Error) result).getError().getMessage().contains("corrupt-key"),
                "the error must name the offending record so an operator can find it: "
                        + ((Result.Error) result).getError().getMessage());
        Mockito.verify(table, Mockito.never()).deleteItem(Mockito.any(Key.class));
        Mockito.verify(table, Mockito.never()).putItem(Mockito.any(AuditEntryEntity.class));
    }

    @Test
    @DisplayName("an empty table compacts without touching anything")
    void emptyTableIsANoOp() {
        DynamoDbTable<AuditEntryEntity> table = tableReturning();

        assertOk(new DynamoDBAuditCompactor(table).compact());

        Mockito.verify(table, Mockito.never()).putItem(Mockito.any(AuditEntryEntity.class));
        Mockito.verify(table, Mockito.never()).deleteItem(Mockito.any(Key.class));
    }

    @Test
    @DisplayName("every superseded record is deleted by its own key")
    void supersededRecordsAreDeletedByTheirOwnKeys() {
        AuditEntryEntity started = ledger(CHANGE_A, "exec-1", AuditEntry.Status.STARTED, T0);
        AuditEntryEntity retried = ledger(CHANGE_A, "exec-2", AuditEntry.Status.APPLIED, T1);
        DynamoDbTable<AuditEntryEntity> table = tableReturning(started, retried);

        assertOk(new DynamoDBAuditCompactor(table).compact());

        ArgumentCaptor<Key> deleted = ArgumentCaptor.forClass(Key.class);
        Mockito.verify(table, Mockito.times(2)).deleteItem(deleted.capture());
        List<String> deletedKeys = new ArrayList<>();
        for (Key key : deleted.getAllValues()) {
            deletedKeys.add(key.partitionKeyValue().s());
        }
        assertTrue(deletedKeys.contains(AuditEntryEntity.partitionKey("exec-1", CHANGE_A, AuditEntry.Status.STARTED)),
                "deleted keys were " + deletedKeys);
        assertTrue(deletedKeys.contains(AuditEntryEntity.partitionKey("exec-2", CHANGE_A, AuditEntry.Status.APPLIED)),
                "the survivor's own old key must go too, after it has been rewritten: " + deletedKeys);
    }

    @Test
    @DisplayName("a change left half-rekeyed by an interrupted run is finished off")
    void compactsAChangeAlreadyPartiallyRekeyed() {
        // The exact state an interrupted run leaves: the survivor already written under the changeId, its
        // old ledger row not yet deleted. Both records carry identical content.
        AuditEntryEntity rekeyed = ledger(CHANGE_A, "exec-1", AuditEntry.Status.APPLIED, T1);
        rekeyed.setPartitionKey(CHANGE_A);
        AuditEntryEntity leftover = ledger(CHANGE_A, "exec-1", AuditEntry.Status.APPLIED, T1);
        DynamoDbTable<AuditEntryEntity> table = tableReturning(rekeyed, leftover);

        assertOk(new DynamoDBAuditCompactor(table).compact());

        ArgumentCaptor<Key> deleted = ArgumentCaptor.forClass(Key.class);
        Mockito.verify(table, Mockito.times(1)).deleteItem(deleted.capture());
        assertEquals(AuditEntryEntity.partitionKey("exec-1", CHANGE_A, AuditEntry.Status.APPLIED),
                deleted.getValue().partitionKeyValue().s(),
                "the leftover ledger row must go, whichever of the two identical records was picked");
    }

    @Test
    @DisplayName("already-compacted and ledger-shaped changes are both handled in one pass")
    void mixedCompactedAndLedgerChangesAreBothHandled() {
        AuditEntryEntity alreadyDone = ledger(CHANGE_A, "exec-1", AuditEntry.Status.APPLIED, T0);
        alreadyDone.setPartitionKey(CHANGE_A);
        DynamoDbTable<AuditEntryEntity> table = tableReturning(
                alreadyDone,
                ledger(CHANGE_B, "exec-1", AuditEntry.Status.STARTED, T0),
                ledger(CHANGE_B, "exec-1", AuditEntry.Status.APPLIED, T1));

        assertOk(new DynamoDBAuditCompactor(table).compact());

        ArgumentCaptor<AuditEntryEntity> written = ArgumentCaptor.forClass(AuditEntryEntity.class);
        Mockito.verify(table, Mockito.times(1)).putItem(written.capture());
        assertEquals(CHANGE_B, written.getValue().getPartitionKey(),
                "only the ledger-shaped change should have been rewritten");
        Mockito.verify(table, Mockito.times(2)).deleteItem(Mockito.any(Key.class));
    }

    @Test
    @DisplayName("the compactor touches nothing but scan, put and delete")
    void compactorTouchesOnlyScanPutAndDelete() {
        DynamoDbTable<AuditEntryEntity> table = tableReturning(
                ledger(CHANGE_A, "exec-1", AuditEntry.Status.STARTED, T0),
                ledger(CHANGE_A, "exec-1", AuditEntry.Status.APPLIED, T1));

        assertOk(new DynamoDBAuditCompactor(table).compact());

        // Proves two contract clauses by construction rather than by observation: compaction cannot create
        // or alter an index or table (it never reaches a DynamoDbClient), and it cannot emit journal events
        // (it has no collaborator that could). Stronger than inspecting the schema afterwards, and it needs
        // no container.
        Mockito.verify(table).scan(Mockito.any(ScanEnhancedRequest.class));
        Mockito.verify(table).putItem(Mockito.any(AuditEntryEntity.class));
        Mockito.verify(table, Mockito.times(2)).deleteItem(Mockito.any(Key.class));
        Mockito.verifyNoMoreInteractions(table);
    }

    @Test
    @DisplayName("a ledger key that is also another change's id is never deleted")
    void ledgerKeyCollidingWithAnotherChangeIdIsNotDeleted() {
        // Contrived but unrecoverable if it ever happened: change ids may contain '#', so one change's ledger
        // key can be character-for-character another change's id, and therefore that change's current-state
        // key. Deleting it would erase the only record of a different change.
        String collidingId = AuditEntryEntity.partitionKey("exec-1", CHANGE_A, AuditEntry.Status.APPLIED);
        AuditEntryEntity victim = ledger(collidingId, "exec-9", AuditEntry.Status.APPLIED, T0);
        victim.setPartitionKey(collidingId);

        DynamoDbTable<AuditEntryEntity> table = tableReturning(
                ledger(CHANGE_A, "exec-1", AuditEntry.Status.STARTED, T0),
                ledger(CHANGE_A, "exec-1", AuditEntry.Status.APPLIED, T1),
                victim);

        assertOk(new DynamoDBAuditCompactor(table).compact());

        ArgumentCaptor<Key> deleted = ArgumentCaptor.forClass(Key.class);
        Mockito.verify(table, Mockito.atLeastOnce()).deleteItem(deleted.capture());
        for (Key key : deleted.getAllValues()) {
            assertTrue(!collidingId.equals(key.partitionKeyValue().s()),
                    "deleted the current-state record of another change: " + collidingId);
        }
    }

    @Test
    @DisplayName("the survivor is the same whichever order the scan returns records in")
    void survivorDoesNotDependOnScanOrder() {
        // DynamoDB guarantees no scan order. If selection depended on it, compaction could pick a different
        // survivor on a re-run and so fail to converge.
        AuditEntryEntity rekeyed = ledger(CHANGE_A, "exec-1", AuditEntry.Status.APPLIED, T1);
        rekeyed.setPartitionKey(CHANGE_A);
        String ledgerKey = AuditEntryEntity.partitionKey("exec-1", CHANGE_A, AuditEntry.Status.APPLIED);

        for (boolean ledgerFirst : new boolean[]{true, false}) {
            AuditEntryEntity current = ledger(CHANGE_A, "exec-1", AuditEntry.Status.APPLIED, T1);
            current.setPartitionKey(CHANGE_A);
            AuditEntryEntity leftover = ledger(CHANGE_A, "exec-1", AuditEntry.Status.APPLIED, T1);
            DynamoDbTable<AuditEntryEntity> table = ledgerFirst
                    ? tableReturning(leftover, current)
                    : tableReturning(current, leftover);

            assertOk(new DynamoDBAuditCompactor(table).compact());

            ArgumentCaptor<Key> deleted = ArgumentCaptor.forClass(Key.class);
            Mockito.verify(table, Mockito.times(1)).deleteItem(deleted.capture());
            assertEquals(ledgerKey, deleted.getValue().partitionKeyValue().s(),
                    "with ledgerFirst=" + ledgerFirst + " the leftover ledger row must be the one removed");
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private static DynamoDbTable<AuditEntryEntity> tableReturning(AuditEntryEntity... records) {
        return tableReturningPages(Page.create(Arrays.asList(records)));
    }

    @SafeVarargs
    @SuppressWarnings("unchecked")
    private static DynamoDbTable<AuditEntryEntity> tableReturningPages(Page<AuditEntryEntity>... pages) {
        DynamoDbTable<AuditEntryEntity> table = Mockito.mock(DynamoDbTable.class);
        final List<Page<AuditEntryEntity>> pageList = Arrays.asList(pages);
        Mockito.when(table.scan(Mockito.any(ScanEnhancedRequest.class)))
                .thenReturn(PageIterable.create(pageList::iterator));
        return table;
    }

    private static Map<String, AttributeValue> continuationToken() {
        return Collections.singletonMap("partitionKey", AttributeValue.builder().s("next").build());
    }

    private static AuditEntryEntity ledger(String changeId,
                                           String executionId,
                                           AuditEntry.Status state,
                                           LocalDateTime createdAt) {
        return AuditEntryEntity.fromAuditEntry(new AuditEntry(
                executionId,
                "test-stage",
                changeId,
                "test-author",
                createdAt,
                state,
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
                RecoveryStrategy.MANUAL_INTERVENTION,
                null));
    }

    private static void assertOk(Result result) {
        if (result.isError()) {
            throw new AssertionError("compact() failed: " + ((Result.Error) result).getError());
        }
    }
}
