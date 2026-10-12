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
package io.flamingock.store.sql.internal;

import io.flamingock.core.kit.audit.AuditEntryTestFactory;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.context.Dependency;
import io.flamingock.internal.common.core.context.RuntimeContext;
import io.flamingock.internal.common.core.external.ExecutionWrapper;
import io.flamingock.internal.util.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit coverage for {@link SqlAuditCompactor} against a mocked {@link SqlAuditRepository} and
 * {@link ExecutionWrapper}.
 * <p>
 * Deliberately Docker-free. SQL does not rekey, so the property that matters most here is simpler than
 * DynamoDB's "survivor before delete": it is that each multi-record change is handled inside exactly one
 * transactional boundary ({@code ExecutionWrapper#wrapExecution}), that the right survivor is selected, and
 * that a failure on one change stops the rest without touching already-compacted or single-record changes.
 * The container-backed {@code SqlAuditCompactionConformanceTest} covers the end state and the shared
 * contract across every dialect.
 */
class SqlAuditCompactorTest {

    private static final String CHANGE_A = "change-a";
    private static final String CHANGE_B = "change-b";
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 1, 1, 12, 0, 0);
    private static final LocalDateTime T1 = LocalDateTime.of(2026, 1, 1, 12, 5, 0);

    @Test
    @DisplayName("a change already in current-state form is not rewritten")
    void alreadyCurrentStateFormIsSkipped() {
        SqlAuditRepository repository = repositoryReturning(
                entry(CHANGE_A, "exec-1", AuditEntry.Status.APPLIED, T0));
        ExecutionWrapper txWrapper = passthroughWrapper();

        assertOk(new SqlAuditCompactor(repository, txWrapper).compact());

        Mockito.verify(repository, Mockito.never())
                .replaceForCompaction(Mockito.any(), Mockito.anyString(), Mockito.any());
    }

    @Test
    @DisplayName("an empty table compacts without touching anything")
    void emptyTableIsANoOp() {
        SqlAuditRepository repository = repositoryReturning();
        ExecutionWrapper txWrapper = passthroughWrapper();

        assertOk(new SqlAuditCompactor(repository, txWrapper).compact());

        Mockito.verify(repository, Mockito.never())
                .replaceForCompaction(Mockito.any(), Mockito.anyString(), Mockito.any());
    }

    @Test
    @DisplayName("on an exact createdAt tie the higher status priority survives")
    void winnerIsChosenByStatusPriorityOnTimestampTie() {
        SqlAuditRepository repository = repositoryReturning(
                entry(CHANGE_A, "exec-1", AuditEntry.Status.APPLIED, T0),
                entry(CHANGE_A, "exec-1", AuditEntry.Status.ROLLED_BACK, T0));
        ExecutionWrapper txWrapper = passthroughWrapper();

        assertOk(new SqlAuditCompactor(repository, txWrapper).compact());

        ArgumentCaptor<AuditEntry> survivor = ArgumentCaptor.forClass(AuditEntry.class);
        Mockito.verify(repository).replaceForCompaction(Mockito.any(), Mockito.eq(CHANGE_A), survivor.capture());
        assertEquals(AuditEntry.Status.ROLLED_BACK, survivor.getValue().getState(),
                "selecting on createdAt alone would resolve this tie arbitrarily");
    }

    @Test
    @DisplayName("each multi-record change is replaced inside its own transactional boundary")
    void eachChangeIsCompactedInsideOneTransaction() {
        SqlAuditRepository repository = repositoryReturning(
                entry(CHANGE_A, "exec-1", AuditEntry.Status.STARTED, T0),
                entry(CHANGE_A, "exec-1", AuditEntry.Status.APPLIED, T1));
        ExecutionWrapper txWrapper = Mockito.mock(ExecutionWrapper.class);
        Mockito.when(txWrapper.wrapExecution(Mockito.any(), Mockito.any()))
                .thenAnswer(invocation -> applyWithMockConnection(invocation));

        assertOk(new SqlAuditCompactor(repository, txWrapper).compact());

        Mockito.verify(txWrapper, Mockito.times(1)).wrapExecution(Mockito.any(), Mockito.any());
        Mockito.verify(repository, Mockito.times(1))
                .replaceForCompaction(Mockito.any(Connection.class), Mockito.eq(CHANGE_A), Mockito.any());
    }

    @Test
    @DisplayName("already-compacted and ledger-shaped changes are both handled in one pass")
    void mixedCompactedAndLedgerChangesAreBothHandled() {
        SqlAuditRepository repository = repositoryReturning(
                entry(CHANGE_A, "exec-1", AuditEntry.Status.APPLIED, T0),
                entry(CHANGE_B, "exec-1", AuditEntry.Status.STARTED, T0),
                entry(CHANGE_B, "exec-1", AuditEntry.Status.APPLIED, T1));
        ExecutionWrapper txWrapper = passthroughWrapper();

        assertOk(new SqlAuditCompactor(repository, txWrapper).compact());

        Mockito.verify(repository, Mockito.never())
                .replaceForCompaction(Mockito.any(), Mockito.eq(CHANGE_A), Mockito.any());
        ArgumentCaptor<AuditEntry> survivor = ArgumentCaptor.forClass(AuditEntry.class);
        Mockito.verify(repository, Mockito.times(1))
                .replaceForCompaction(Mockito.any(), Mockito.eq(CHANGE_B), survivor.capture());
        assertEquals(AuditEntry.Status.APPLIED, survivor.getValue().getState());
    }

    @Test
    @DisplayName("the first failing change stops the rest")
    void failFastStopsAtTheFirstFailingChange() {
        SqlAuditRepository repository = repositoryReturning(
                entry(CHANGE_A, "exec-1", AuditEntry.Status.STARTED, T0),
                entry(CHANGE_A, "exec-1", AuditEntry.Status.APPLIED, T1),
                entry(CHANGE_B, "exec-1", AuditEntry.Status.STARTED, T0),
                entry(CHANGE_B, "exec-1", AuditEntry.Status.APPLIED, T1));
        ExecutionWrapper txWrapper = Mockito.mock(ExecutionWrapper.class);
        Mockito.when(txWrapper.wrapExecution(Mockito.any(), Mockito.any()))
                .thenThrow(new IllegalStateException("transaction failed"));

        Result result = new SqlAuditCompactor(repository, txWrapper).compact();

        assertTrue(result.isError(), "the failure must be reported");
        // One attempt only: change-b must not have been touched after change-a failed.
        Mockito.verify(txWrapper, Mockito.times(1)).wrapExecution(Mockito.any(), Mockito.any());
    }

    @Test
    @DisplayName("a record with no changeId fails with an actionable message, writing nothing")
    void recordWithBlankChangeIdFailsWithAClearMessage() {
        SqlAuditRepository repository = repositoryReturning(
                entry(null, "exec-1", AuditEntry.Status.APPLIED, T0));
        ExecutionWrapper txWrapper = passthroughWrapper();

        Result result = new SqlAuditCompactor(repository, txWrapper).compact();

        assertTrue(result.isError());
        assertTrue(((Result.Error) result).getError().getMessage().contains("changeId"),
                "the error must name what is wrong: " + ((Result.Error) result).getError().getMessage());
        Mockito.verify(repository, Mockito.never())
                .replaceForCompaction(Mockito.any(), Mockito.anyString(), Mockito.any());
    }

    @Test
    @DisplayName("the survivor is the same whichever order the history returns records in")
    void survivorDoesNotDependOnHistoryOrder() {
        AuditEntry first = entry(CHANGE_A, "exec-1", AuditEntry.Status.APPLIED, T1);
        AuditEntry second = entry(CHANGE_A, "exec-1", AuditEntry.Status.ROLLBACK_FAILED, T1);

        for (boolean firstWins : new boolean[]{true, false}) {
            SqlAuditRepository repository = firstWins
                    ? repositoryReturning(first, second)
                    : repositoryReturning(second, first);
            ExecutionWrapper txWrapper = passthroughWrapper();

            assertOk(new SqlAuditCompactor(repository, txWrapper).compact());

            ArgumentCaptor<AuditEntry> survivor = ArgumentCaptor.forClass(AuditEntry.class);
            Mockito.verify(repository).replaceForCompaction(Mockito.any(), Mockito.eq(CHANGE_A), survivor.capture());
            assertEquals(AuditEntry.Status.ROLLBACK_FAILED, survivor.getValue().getState(),
                    "higher status priority must win regardless of scan order, firstWins=" + firstWins);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private static SqlAuditRepository repositoryReturning(AuditEntry... history) {
        SqlAuditRepository repository = Mockito.mock(SqlAuditRepository.class);
        Mockito.when(repository.getAuditHistory()).thenReturn(Arrays.asList(history));
        Mockito.when(repository.replaceForCompaction(Mockito.any(), Mockito.anyString(), Mockito.any()))
                .thenReturn(Result.OK());
        return repository;
    }

    /** An {@link ExecutionWrapper} that simply runs the operation against a mocked {@link Connection}. */
    private static ExecutionWrapper passthroughWrapper() {
        ExecutionWrapper txWrapper = Mockito.mock(ExecutionWrapper.class);
        Mockito.when(txWrapper.wrapExecution(Mockito.any(), Mockito.any()))
                .thenAnswer(SqlAuditCompactorTest::applyWithMockConnection);
        return txWrapper;
    }

    @SuppressWarnings("unchecked")
    private static Object applyWithMockConnection(org.mockito.invocation.InvocationOnMock invocation) {
        RuntimeContext runtimeContext = invocation.getArgument(0);
        runtimeContext.addDependency(new Dependency(Mockito.mock(Connection.class)));
        Function<RuntimeContext, Object> operation = invocation.getArgument(1);
        return operation.apply(runtimeContext);
    }

    private static AuditEntry entry(String changeId, String executionId, AuditEntry.Status status, LocalDateTime createdAt) {
        return AuditEntryTestFactory.createDeterministicAuditEntry(executionId, changeId, status, createdAt, false);
    }

    private static void assertOk(Result result) {
        if (result.isError()) {
            throw new AssertionError("compact() failed: " + ((Result.Error) result).getError());
        }
    }
}
