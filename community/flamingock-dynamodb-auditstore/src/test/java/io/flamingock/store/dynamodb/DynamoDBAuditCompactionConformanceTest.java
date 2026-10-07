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
package io.flamingock.store.dynamodb;

import io.flamingock.core.kit.audit.AuditEntryTestFactory;
import io.flamingock.core.kit.audit.compaction.AuditCompactionConformance;
import io.flamingock.core.kit.audit.compaction.AuditStorageCompactionFixture;
import io.flamingock.dynamodb.kit.DynamoDBAuditStorage;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.feature.Features;
import io.flamingock.internal.core.configuration.community.CommunityConfiguration;
import io.flamingock.internal.core.context.SimpleContext;
import io.flamingock.internal.util.FeatureFlag;
import io.flamingock.internal.util.dynamodb.DynamoDBUtil;
import io.flamingock.internal.util.dynamodb.entities.AuditEntryEntity;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.targetsystem.dynamodb.DynamoDBTargetSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.TableDescription;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Audit compaction against a real DynamoDB: the shared contract, plus the DynamoDB-specific guarantees the
 * shared suite structurally cannot express.
 * <p>
 * The shared fixture reads records as {@code AuditEntry}, which drops the partition key — so it can prove a
 * change ends up with one record, but not that the record was <em>rekeyed</em> from
 * {@code executionId#changeId#state} to the bare {@code changeId}. That is the whole of what makes DynamoDB
 * different here, so it is asserted directly on the stored entities.
 * <p>
 * The call-sequence guarantees (survivor written before anything is deleted, every scan page processed,
 * nothing deleted when a write fails) are covered by
 * {@code io.flamingock.store.dynamodb.internal.DynamoDBAuditCompactorTest} against a mocked table, where the
 * sequence is observable rather than merely inferable.
 */
@Testcontainers
class DynamoDBAuditCompactionConformanceTest {

    private static final String STAGE_ID = "compaction-stage";
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 1, 1, 12, 0, 0);
    private static final LocalDateTime T1 = LocalDateTime.of(2026, 1, 1, 12, 5, 0);

    @Container
    static final GenericContainer<?> dynamoDBContainer = DynamoDBTestContainer.createContainer();

    private DynamoDbClient client;
    private String auditTableName;
    private DynamoDBAuditStore auditStore;
    private DynamoDBAuditStorage auditStorage;
    private AuditCompactionConformance conformance;

    @BeforeEach
    void setUp() {
        client = DynamoDBTestContainer.createClient(dynamoDBContainer);
        auditTableName = tableName("compactionAudit");

        SimpleContext context = new SimpleContext();
        context.addDependency(RunnerId.generate());
        context.addDependency(new CommunityConfiguration());

        DynamoDBTargetSystem targetSystem = new DynamoDBTargetSystem("dynamodb", client);
        targetSystem.initialize(context);
        auditStore = DynamoDBAuditStore.from(targetSystem)
                .withAuditRepositoryName(auditTableName)
                .withLockRepositoryName(tableName("compactionLock"))
                .withJournalRepositoryName(tableName("compactionJournal"));
        auditStore.initialize(context);

        // Creates the audit table (and waits for it) through the production path, so the storage below binds
        // to a table that already exists.
        auditStore.getAuditCompactor();

        auditStorage = new DynamoDBAuditStorage(client, auditTableName, true, 5L, 5L);
        conformance = new AuditCompactionConformance(
                new AuditStorageCompactionFixture(auditStore, auditStorage, STAGE_ID));
    }

    @AfterEach
    void tearDown() {
        // FeatureFlag is process-global and every DynamoDB test shares this JVM. The current-state-write
        // property restores it itself, but leaving it enabled on an unexpected failure path would silently
        // change how the next test's store behaves.
        FeatureFlag.remove(Features.JOURNAL_EVENTS);
        if (client != null) {
            client.listTables().tableNames().forEach(name ->
                    client.deleteTable(DeleteTableRequest.builder().tableName(name).build()));
            client.close();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // The shared contract
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("seeding round-trips faithfully")
    void seedingRoundTripsFaithfully() {
        conformance.verifySeedingRoundTripsFaithfully();
    }

    @Test
    @DisplayName("the audit snapshot is unchanged by compaction")
    void snapshotPreserved() {
        conformance.verifySnapshotPreserved();
    }

    @Test
    @DisplayName("exactly one record survives per change, and it is the right one")
    void oneRecordPerChange() {
        conformance.verifyOneRecordPerChange();
    }

    @Test
    @DisplayName("compaction is a fixpoint")
    void isFixpoint() {
        conformance.verifyIsFixpoint();
    }

    @Test
    @DisplayName("the ordinary current-state write still works after compaction")
    void subsequentCurrentStateWriteSucceeds() {
        conformance.verifySubsequentCurrentStateWriteSucceeds();
    }

    @Test
    @DisplayName("changes absent from the pipeline are preserved")
    void unknownChangesPreserved() {
        conformance.verifyUnknownChangesPreserved();
    }

    @Test
    @DisplayName("a change that already has one record is left untouched")
    void alreadySingleRecordUntouched() {
        conformance.verifyAlreadySingleRecordUntouched();
    }

    @Test
    @DisplayName("compacting an empty store is a no-op")
    void emptyStoreIsNoOp() {
        conformance.verifyEmptyStoreIsNoOp();
    }

    @Test
    @DisplayName("an exact createdAt tie is broken by status priority")
    void timestampTieBreaksOnStatusPriority() {
        conformance.verifyTimestampTieBreaksOnStatusPriority();
    }

    @Test
    @DisplayName("system-change entries keep their flag")
    void systemChangeEntriesPreserved() {
        conformance.verifySystemChangeEntriesPreserved();
    }

    @Test
    @DisplayName("a ledger larger than one batch is fully compacted")
    void largeLedgerIsCompacted() {
        conformance.verifyLargeLedgerIsCompacted(300, 3);
    }

    // ---------------------------------------------------------------------------------------------
    // DynamoDB-specific: the rekey
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("the surviving record is rekeyed to the bare changeId")
    void compactionRekeysSurvivorToChangeId() {
        seedLedger("change-a");

        requireCompacted();

        List<AuditEntryEntity> stored = storedAuditRecords();
        assertEquals(1, stored.size(), "expected a single surviving record, found " + describe(stored));
        assertEquals("change-a", stored.get(0).getPartitionKey(),
                "the survivor must be keyed by changeId alone, which is what the current-state write path uses");
        assertEquals(AuditEntry.Status.APPLIED.name(), stored.get(0).getState());
    }

    @Test
    @DisplayName("no ledger-keyed record is left behind")
    void compactionRemovesLedgerKeyedRecords() {
        seedLedger("change-a");

        requireCompacted();

        for (AuditEntryEntity record : storedAuditRecords()) {
            assertEquals(record.getChangeId(), record.getPartitionKey(),
                    "a record still keyed by executionId#changeId#state survived compaction: "
                            + record.getPartitionKey());
        }
    }

    @Test
    @DisplayName("an interrupted run converges on the next pass")
    void convergesAfterCrashBetweenPutAndDelete() {
        // Reproduce exactly what a crash between the survivor write and the deletes leaves behind: the
        // survivor already stored under the changeId, with its ledger rows still present.
        seedLedger("change-a");
        AuditEntryEntity rekeyedSurvivor = AuditEntryEntity.fromAuditEntry(
                AuditEntryTestFactory.createDeterministicAuditEntry(
                        "exec-1", "change-a", AuditEntry.Status.APPLIED, T1, false));
        rekeyedSurvivor.setPartitionKey("change-a");
        auditTable().putItem(rekeyedSurvivor);
        assertEquals(3, storedAuditRecords().size(), "the half-finished state should hold three records");

        requireCompacted();

        List<AuditEntryEntity> stored = storedAuditRecords();
        assertEquals(1, stored.size(), "compaction must converge, found " + describe(stored));
        assertEquals("change-a", stored.get(0).getPartitionKey());
        assertEquals(AuditEntry.Status.APPLIED.name(), stored.get(0).getState());
    }

    @Test
    @DisplayName("compaction leaves the table schema untouched")
    void compactionLeavesTableSchemaUntouched() {
        seedLedger("change-a");
        TableDescription before = describeAuditTable();

        requireCompacted();

        TableDescription after = describeAuditTable();
        assertEquals(before.keySchema(), after.keySchema());
        assertEquals(before.attributeDefinitions(), after.attributeDefinitions());
        assertTrue(after.globalSecondaryIndexes() == null || after.globalSecondaryIndexes().isEmpty(),
                "compaction must not create indexes");
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    /** Seeds a ledger-shaped history for one change: STARTED then APPLIED, same execution. */
    private void seedLedger(String changeId) {
        auditStorage.addAuditEntry(AuditEntryTestFactory.createDeterministicAuditEntry(
                "exec-1", changeId, AuditEntry.Status.STARTED, T0, false));
        auditStorage.addAuditEntry(AuditEntryTestFactory.createDeterministicAuditEntry(
                "exec-1", changeId, AuditEntry.Status.APPLIED, T1, false));
    }

    private void requireCompacted() {
        io.flamingock.internal.util.Result result = auditStore.getAuditCompactor().compact();
        if (result.isError()) {
            throw new AssertionError("compact() failed: "
                    + ((io.flamingock.internal.util.Result.Error) result).getError());
        }
    }

    private software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable<AuditEntryEntity> auditTable() {
        return new DynamoDBUtil(client).getEnhancedClient()
                .table(auditTableName, TableSchema.fromBean(AuditEntryEntity.class));
    }

    /** Raw entities, so the partition key is visible — the shared fixture maps it away. */
    private List<AuditEntryEntity> storedAuditRecords() {
        return auditTable()
                .scan(ScanEnhancedRequest.builder().consistentRead(true).build())
                .items()
                .stream()
                .collect(Collectors.toList());
    }

    private TableDescription describeAuditTable() {
        return client.describeTable(DescribeTableRequest.builder().tableName(auditTableName).build()).table();
    }

    private static String describe(List<AuditEntryEntity> records) {
        List<String> keys = new ArrayList<>();
        for (AuditEntryEntity record : records) {
            keys.add(record.getPartitionKey() + "(" + record.getState() + ")");
        }
        return keys.toString();
    }

    private static String tableName(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }
}
