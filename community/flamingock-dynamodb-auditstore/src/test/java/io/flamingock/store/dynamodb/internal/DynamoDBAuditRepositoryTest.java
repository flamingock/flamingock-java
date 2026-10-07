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

import io.flamingock.internal.util.dynamodb.entities.AuditEntryEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins how {@link DynamoDBAuditRepository} reads the audit table.
 * <p>
 * Added because the read's correctness properties were enforced nowhere. The audit table is the operational
 * source of truth — {@code CommunityExecutionPlanner} decides what still needs executing from
 * {@code getAuditSnapshotByChangeId()}, which aggregates whatever
 * {@link DynamoDBAuditRepository#getAuditHistory()} returns — so a read that silently returns less than the
 * whole table, or a stale view of it, makes the planner mis-decide. Neither failure mode shows up as an
 * error; both just produce a wrong answer.
 * <p>
 * {@code DynamoDBAuditCompactor} carries the same three requirements and has its own equivalents in
 * {@code DynamoDBAuditCompactorTest}. They are deliberately asserted separately rather than through one
 * shared helper: the two reads share a flag value, not a reason. Here a stale or truncated read misleads the
 * planner; there it makes compaction pick the wrong survivor and delete the real one. The consequences differ
 * enough that collapsing them into one symbol would hide why either matters.
 */
class DynamoDBAuditRepositoryTest {

    @Test
    @DisplayName("the audit history read is strongly consistent, unbounded and unfiltered")
    void getAuditHistoryReadsTheWholeTableConsistently() {
        DynamoDbTable<AuditEntryEntity> table = mockedEmptyTable();
        DynamoDBAuditRepository repository = repositoryBackedBy(table);

        repository.getAuditHistory();

        ArgumentCaptor<ScanEnhancedRequest> captor = ArgumentCaptor.forClass(ScanEnhancedRequest.class);
        Mockito.verify(table).scan(captor.capture());
        ScanEnhancedRequest request = captor.getValue();

        assertEquals(Boolean.TRUE, request.consistentRead(),
                "an eventually consistent read can report a state the change has already moved past,"
                        + " which makes the planner decide against stale history");
        assertEquals(null, request.limit(),
                "a bounded read would silently drop history and change the reconstructed state");
        assertEquals(null, request.filterExpression(),
                "the audit history is the whole table; filtering it would hide changes from the planner");
    }

    /**
     * Assigns the table handle directly — it is {@code protected} and this test shares its package, so no
     * reflection is needed. The constructor is given a mocked client because it only wraps it; nothing in
     * this test reaches DynamoDB.
     */
    private static DynamoDBAuditRepository repositoryBackedBy(DynamoDbTable<AuditEntryEntity> table) {
        DynamoDBAuditRepository repository = new DynamoDBAuditRepository(
                Mockito.mock(DynamoDbClient.class), "auditTable", 5L, 5L);
        repository.table = table;
        return repository;
    }

    @SuppressWarnings("unchecked")
    private static DynamoDbTable<AuditEntryEntity> mockedEmptyTable() {
        DynamoDbTable<AuditEntryEntity> table = Mockito.mock(DynamoDbTable.class);
        final List<Page<AuditEntryEntity>> pages =
                Collections.singletonList(Page.create(Collections.<AuditEntryEntity>emptyList()));
        Mockito.when(table.scan(Mockito.any(ScanEnhancedRequest.class)))
                .thenReturn(PageIterable.create(pages::iterator));
        return table;
    }
}
