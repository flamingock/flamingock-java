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
package io.flamingock.store.mongodb.sync;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import io.flamingock.core.kit.audit.AuditEntryTestFactory;
import io.flamingock.core.kit.audit.compaction.AuditCompactionConformance;
import io.flamingock.core.kit.audit.compaction.AuditStorageCompactionFixture;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.context.Dependency;
import io.flamingock.internal.common.core.feature.Features;
import io.flamingock.internal.core.configuration.community.CommunityConfiguration;
import io.flamingock.internal.core.context.SimpleContext;
import io.flamingock.internal.util.FeatureFlag;
import io.flamingock.internal.util.Result;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.mongodb.kit.MongoDBSyncAuditStorage;
import io.flamingock.targetsystem.mongodb.sync.MongoDBSyncTargetSystem;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Audit compaction against a real MongoDB: the shared contract, plus the guarantees that are specific to
 * this store.
 * <p>
 * The shared fixture reads documents as {@code AuditEntry}, which drops {@code _id} — so it can prove a
 * change ends up with one document, but not that the surviving document was <em>never rewritten</em>. That
 * is the whole of what makes MongoDB different here (nothing is written at all), so it is asserted
 * directly on the stored {@code _id}.
 * <p>
 * The call-sequence guarantees — that only deletes ever reach the collection, that the filter excludes the
 * survivor, that a failure is reported rather than thrown — are covered Docker-free by
 * {@code io.flamingock.store.mongodb.sync.internal.MongoDBSyncAuditCompactorTest}.
 */
@Testcontainers
class MongoDBSyncAuditCompactionConformanceTest {

    private static final String DATABASE_NAME = "test";
    private static final String AUDIT_COLLECTION = "compactionAuditLog";
    private static final String STAGE_ID = "compaction-stage";
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 1, 1, 12, 0, 0);
    private static final LocalDateTime T1 = LocalDateTime.of(2026, 1, 1, 12, 5, 0);

    @Container
    static final MongoDBContainer mongoDBContainer =
            new MongoDBContainer(DockerImageName.parse("mongo:6")).withReuse(true);

    private MongoClient mongoClient;
    private MongoDatabase database;
    private MongoDBSyncAuditStore auditStore;
    private MongoDBSyncAuditStorage auditStorage;
    private AuditCompactionConformance conformance;

    @BeforeEach
    void setUp() {
        mongoClient = MongoClients.create(mongoDBContainer.getConnectionString());
        database = mongoClient.getDatabase(DATABASE_NAME);

        auditStore = storeFor(new MongoDBSyncTargetSystem("mongodb", mongoClient, DATABASE_NAME));

        // Forces the audit collection and its unique (executionId, changeId, state) index into existence,
        // which happens lazily in MongoDBSyncAuditPersistence.doInitialize rather than in the store's own
        // initialize. Without this the collection would be created indexless by the test kit's raw inserts,
        // the store would not behave as it does in production, and indexesAreUntouched below would compare
        // _id_ with _id_ and prove nothing.
        auditStore.getPersistenceFactory().get(STAGE_ID);

        auditStorage = new MongoDBSyncAuditStorage(database, AUDIT_COLLECTION);
        conformance = new AuditCompactionConformance(
                new AuditStorageCompactionFixture(auditStore, auditStorage, STAGE_ID));
    }

    @AfterEach
    void tearDown() {
        // FeatureFlag is process-global and every test in this module shares the JVM. The current-state
        // write property restores it itself, but leaving it enabled on an unexpected failure path would
        // silently change how the next test's store behaves.
        FeatureFlag.remove(Features.JOURNAL_EVENTS);
        if (mongoClient != null) {
            database.drop();
            mongoClient.close();
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
    // MongoDB-specific
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("the surviving document is never rewritten — it keeps its original _id")
    void survivorKeepsItsOriginalId() {
        seedLedger("change-a");
        Object survivorIdBefore = idOf("change-a", AuditEntry.Status.APPLIED);
        assertNotNull(survivorIdBefore, "the document that should survive was not seeded");

        requireCompacted();

        List<Document> remaining = rawDocumentsFor("change-a");
        assertEquals(1, remaining.size(), "expected one surviving document, found " + remaining.size());
        assertEquals(survivorIdBefore, remaining.get(0).get("_id"),
                "the survivor must be the original document, untouched — a changed _id means compaction"
                        + " rewrote it instead of merely deleting its siblings");
    }

    @Test
    @DisplayName("compaction leaves the collection's indexes untouched")
    void indexesAreUntouched() {
        seedLedger("change-a");
        List<String> before = indexNames();
        assertTrue(before.size() > 1,
                "setup must have created the unique index, otherwise this test proves nothing: " + before);

        requireCompacted();

        assertEquals(before, indexNames(), "compaction must not create, drop or alter any index");
    }

    @Test
    @DisplayName("the current-state write works after compaction with transactions disabled")
    void currentStateWriteWorksAfterCompactionWithTransactionsDisabled() {
        // A dimension DynamoDB does not have. With transactions off the persistence takes the
        // non-transactional save(AuditEntry) overload, which carries the same one-document-per-change
        // expectation as the transactional one. The shared property only exercises the default, enabled path.
        MongoDBSyncAuditStore nonTransactionalStore = storeFor(
                new MongoDBSyncTargetSystem("mongodb", mongoClient, DATABASE_NAME)
                        .withTransactionsSupported(false));
        seedLedger("change-a");
        requireCompacted(nonTransactionalStore);

        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        try {
            Result written = nonTransactionalStore.getPersistenceFactory().get(STAGE_ID)
                    .writeEntry(AuditEntryTestFactory.createDeterministicAuditEntry(
                            "exec-after", "change-a", AuditEntry.Status.APPLIED, T1.plusMinutes(5), false));
            assertTrue(!written.isError(), "the non-transactional current-state write must still work");
            assertEquals(1, rawDocumentsFor("change-a").size(),
                    "the write must have replaced the single document, not added a second");
        } finally {
            FeatureFlag.remove(Features.JOURNAL_EVENTS);
        }
    }

    @Test
    @DisplayName("asking an uninitialized store for a compactor fails loudly")
    void getAuditCompactorBeforeInitializeThrows() {
        MongoDBSyncAuditStore uninitialized = MongoDBSyncAuditStore.from(
                new MongoDBSyncTargetSystem("mongodb", mongoClient, DATABASE_NAME));

        assertThrows(IllegalStateException.class, uninitialized::getAuditCompactor,
                "contract clause 13: the compactor is only available after initialize");
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private MongoDBSyncAuditStore storeFor(MongoDBSyncTargetSystem targetSystem) {
        SimpleContext context = new SimpleContext();
        context.addDependency(new Dependency(RunnerId.class, RunnerId.generate("compaction-conformance")));
        context.addDependency(new Dependency(CommunityConfiguration.class, new CommunityConfiguration()));
        targetSystem.initialize(context);

        MongoDBSyncAuditStore store = MongoDBSyncAuditStore.from(targetSystem)
                .withAuditRepositoryName(AUDIT_COLLECTION)
                .withLockRepositoryName("compactionLock")
                .withJournalRepositoryName("compactionJournal");
        store.initialize(context);
        return store;
    }

    /** Seeds a ledger-shaped history for one change: STARTED then APPLIED, same execution. */
    private void seedLedger(String changeId) {
        auditStorage.addAuditEntry(AuditEntryTestFactory.createDeterministicAuditEntry(
                "exec-1", changeId, AuditEntry.Status.STARTED, T0, false));
        auditStorage.addAuditEntry(AuditEntryTestFactory.createDeterministicAuditEntry(
                "exec-1", changeId, AuditEntry.Status.APPLIED, T1, false));
    }

    private void requireCompacted() {
        requireCompacted(auditStore);
    }

    private void requireCompacted(MongoDBSyncAuditStore store) {
        Result result = store.getAuditCompactor().compact();
        if (result.isError()) {
            throw new AssertionError("compact() failed: " + ((Result.Error) result).getError());
        }
    }

    /** Raw documents, so {@code _id} is visible — the shared fixture maps it away. */
    private List<Document> rawDocumentsFor(String changeId) {
        List<Document> documents = new ArrayList<>();
        database.getCollection(AUDIT_COLLECTION)
                .find(new Document("changeId", changeId))
                .forEach((java.util.function.Consumer<Document>) documents::add);
        return documents;
    }

    private Object idOf(String changeId, AuditEntry.Status state) {
        Document found = database.getCollection(AUDIT_COLLECTION)
                .find(new Document("changeId", changeId).append("state", state.name()))
                .first();
        return found == null ? null : found.get("_id");
    }

    private List<String> indexNames() {
        List<String> names = new ArrayList<>();
        database.getCollection(AUDIT_COLLECTION)
                .listIndexes()
                .forEach((java.util.function.Consumer<Document>) index -> names.add(index.getString("name")));
        java.util.Collections.sort(names);
        return names;
    }
}
