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
package io.flamingock.store.couchbase;

import com.couchbase.client.core.io.CollectionIdentifier;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.json.JsonObject;
import io.flamingock.core.kit.audit.AuditEntryTestFactory;
import io.flamingock.core.kit.audit.compaction.AuditCompactionConformance;
import io.flamingock.core.kit.audit.compaction.AuditStorageCompactionFixture;
import io.flamingock.couchbase.kit.CouchbaseAuditStorage;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.feature.Features;
import io.flamingock.internal.common.couchbase.CouchbaseAuditMapper;
import io.flamingock.internal.common.couchbase.CouchbaseCollectionHelper;
import io.flamingock.internal.core.configuration.community.CommunityConfiguration;
import io.flamingock.internal.core.context.SimpleContext;
import io.flamingock.internal.util.FeatureFlag;
import io.flamingock.internal.util.Result;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.targetsystem.couchbase.CouchbaseTargetSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.couchbase.BucketDefinition;
import org.testcontainers.couchbase.CouchbaseContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Audit compaction against a real Couchbase: the shared contract, plus the Couchbase-specific guarantees
 * the shared suite structurally cannot express.
 * <p>
 * The shared fixture reads records through {@code AuditStorage}, which maps to {@code AuditEntry} and so
 * drops the physical key - it can prove a change ends up with one document, but not that the document was
 * <em>rekeyed</em> from {@code executionId#changeId#state} to the bare {@code changeId}. That is the whole
 * of what makes Couchbase different here, so it is asserted directly with a raw {@code META().id} query -
 * the same trap noted for {@code CouchbaseCollectionHelper#selectAllDocuments}, which projects fields only.
 * <p>
 * The call-sequence guarantees (survivor written before anything is deleted, a collision never deleted) are
 * covered by {@code io.flamingock.store.couchbase.internal.CouchbaseAuditCompactorTest} against a mocked
 * cluster/collection, where the sequence is observable rather than merely inferable.
 */
@Testcontainers
class CouchbaseAuditCompactionConformanceTest {

    private static final String BUCKET_NAME = "test";
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 1, 1, 12, 0, 0);
    private static final LocalDateTime T1 = LocalDateTime.of(2026, 1, 1, 12, 5, 0);

    @Container
    static final CouchbaseContainer couchbaseContainer = new CouchbaseContainer("couchbase/server:7.2.4")
            .withBucket(new BucketDefinition(BUCKET_NAME));

    private static Cluster cluster;

    private String auditCollectionName;
    private CouchbaseAuditStore auditStore;
    private CouchbaseAuditStorage auditStorage;
    private AuditCompactionConformance conformance;

    @BeforeEach
    void setUp() {
        if (cluster == null) {
            cluster = Cluster.connect(
                    couchbaseContainer.getConnectionString(),
                    couchbaseContainer.getUsername(),
                    couchbaseContainer.getPassword());
            cluster.bucket(BUCKET_NAME).waitUntilReady(Duration.ofSeconds(10));
        }
        auditCollectionName = collectionName("compactionAudit");

        SimpleContext context = new SimpleContext();
        context.addDependency(RunnerId.generate());
        context.addDependency(new CommunityConfiguration());

        CouchbaseTargetSystem targetSystem = new CouchbaseTargetSystem("couchbase", cluster, BUCKET_NAME);
        targetSystem.initialize(context);
        auditStore = CouchbaseAuditStore.from(targetSystem)
                .withAuditRepositoryName(auditCollectionName)
                .withLockRepositoryName(collectionName("compactionLock"))
                .withJournalRepositoryName(collectionName("compactionJournal"));
        auditStore.initialize(context);

        // Creates the audit collection through the production path, so the storage below binds to a
        // collection that already exists.
        auditStore.getAuditCompactor();

        auditStorage = new CouchbaseAuditStorage(cluster, BUCKET_NAME, CollectionIdentifier.DEFAULT_SCOPE, auditCollectionName);
        conformance = new AuditCompactionConformance(
                new AuditStorageCompactionFixture(auditStore, auditStorage, "compaction-stage"));
    }

    @AfterEach
    void tearDown() {
        // FeatureFlag is process-global and every Couchbase test shares this JVM.
        FeatureFlag.remove(Features.JOURNAL_EVENTS);
    }

    // ---------------------------------------------------------------------------------------------
    // The shared contract
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("audit compaction satisfies the shared contract")
    void compactionSatisfiesContract() {
        conformance.verifyAll();
    }

    // ---------------------------------------------------------------------------------------------
    // Couchbase-specific: the rekey
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("the surviving document is rekeyed to the bare changeId")
    void compactionRekeysSurvivorToChangeId() {
        seedLedger("change-a");

        requireCompacted();

        List<StoredRow> stored = storedRows();
        assertEquals(1, stored.size(), "expected a single surviving document, found " + describe(stored));
        assertEquals("change-a", stored.get(0).id,
                "the survivor must be keyed by changeId alone, which is what the current-state write path uses");
        assertEquals(AuditEntry.Status.APPLIED.name(), stored.get(0).document.getString("state"));
    }

    @Test
    @DisplayName("no ledger-keyed document is left behind")
    void compactionRemovesLedgerKeyedDocuments() {
        seedLedger("change-a");

        requireCompacted();

        for (StoredRow row : storedRows()) {
            assertEquals(row.document.getString("changeId"), row.id,
                    "a document still keyed by executionId#changeId#state survived compaction: " + row.id);
        }
    }

    @Test
    @DisplayName("an interrupted run converges on the next pass")
    void convergesAfterCrashBetweenWriteAndDelete() {
        // Reproduce exactly what a crash between the survivor write and the delete leaves behind: the
        // survivor already stored under the changeId, with its ledger document still present.
        seedLedger("change-a");
        AuditEntry rekeyedSurvivor = AuditEntryTestFactory.createDeterministicAuditEntry(
                "exec-1", "change-a", AuditEntry.Status.APPLIED, T1, false);
        upsertAt("change-a", rekeyedSurvivor);
        assertEquals(3, storedRows().size(), "the half-finished state should hold three documents");

        requireCompacted();

        List<StoredRow> stored = storedRows();
        assertEquals(1, stored.size(), "compaction must converge, found " + describe(stored));
        assertEquals("change-a", stored.get(0).id);
        assertEquals(AuditEntry.Status.APPLIED.name(), stored.get(0).document.getString("state"));
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

    private void upsertAt(String key, AuditEntry entry) {
        cluster.bucket(BUCKET_NAME).scope(CollectionIdentifier.DEFAULT_SCOPE).collection(auditCollectionName)
                .upsert(key, new CouchbaseAuditMapper().toDocument(entry));
    }

    private void requireCompacted() {
        Result result = auditStore.getAuditCompactor().compact();
        if (result.isError()) {
            throw new AssertionError("compact() failed: " + ((Result.Error) result).getError());
        }
    }

    /** Raw rows with {@code META().id}, so the key is visible - the shared fixture maps it away. */
    private List<StoredRow> storedRows() {
        List<JsonObject> rows = CouchbaseCollectionHelper.selectAllDocumentsWithId(
                cluster, BUCKET_NAME, CollectionIdentifier.DEFAULT_SCOPE, auditCollectionName);
        List<StoredRow> result = new ArrayList<>();
        for (JsonObject row : rows) {
            String id = row.getString("id");
            row.removeKey("id");
            result.add(new StoredRow(id, row));
        }
        return result;
    }

    private static String describe(List<StoredRow> rows) {
        List<String> keys = new ArrayList<>();
        for (StoredRow row : rows) {
            keys.add(row.id + "(" + row.document.getString("state") + ")");
        }
        return keys.toString();
    }

    private static String collectionName(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }

    private static final class StoredRow {
        final String id;
        final JsonObject document;

        StoredRow(String id, JsonObject document) {
            this.id = id;
            this.document = document;
        }
    }
}
