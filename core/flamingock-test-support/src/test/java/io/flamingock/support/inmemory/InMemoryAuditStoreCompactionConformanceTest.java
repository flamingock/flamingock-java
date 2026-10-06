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
package io.flamingock.support.inmemory;

import io.flamingock.core.kit.audit.compaction.AuditCompactionConformance;
import io.flamingock.core.kit.audit.compaction.AuditCompactionFixture;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditReader;
import io.flamingock.internal.common.core.context.Dependency;
import io.flamingock.internal.core.context.SimpleContext;
import io.flamingock.internal.util.Result;
import io.flamingock.internal.util.id.RunnerId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Runs the shared audit-compaction contract against {@link InMemoryAuditStore}, the store users are pointed
 * at for their own tests.
 * <p>
 * Lives in this package so it can reach the package-private {@code InMemoryAuditStorage} for raw seeding and
 * physical read-back, rather than widening that class's visibility for a test.
 * <p>
 * Covers every property except the current-state-write one, and that exclusion is a fact about this store
 * rather than a gap: it only ever appends — {@code InMemoryAuditWriter.writeEntry} delegates straight to
 * {@code addAuditEntry} — and it does not override {@code getPersistenceFactory()}, so there is no
 * current-state write path for compaction to leave working. That property is exercised where a store has
 * one: {@code InMemoryAuditCompactionConformanceTest} in {@code e2e/core-e2e} runs the full set against the
 * journal-aware in-memory store.
 */
class InMemoryAuditStoreCompactionConformanceTest {

    private AuditCompactionConformance conformance;

    @BeforeEach
    void setUp() {
        InMemoryAuditStore auditStore = InMemoryAuditStore.create();
        SimpleContext context = new SimpleContext();
        context.addDependency(new Dependency(RunnerId.class, RunnerId.generate("compaction-conformance")));
        auditStore.initialize(context);

        conformance = new AuditCompactionConformance(new InMemoryFixture(auditStore));
    }

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

    /**
     * Wires the contract to this store directly, because its storage is package-private and does not
     * implement the shared {@code AuditStorage} interface that {@code AuditStorageCompactionFixture} expects.
     */
    private static final class InMemoryFixture implements AuditCompactionFixture {

        private final InMemoryAuditStore auditStore;
        private final InMemoryAuditStorage auditStorage;

        private InMemoryFixture(InMemoryAuditStore auditStore) {
            this.auditStore = auditStore;
            this.auditStorage = auditStore.getAuditStorage();
        }

        @Override
        public void seedLedgerEntry(AuditEntry entry) {
            auditStorage.addAuditEntry(entry);
        }

        @Override
        public List<AuditEntry> readStoredRecords() {
            return auditStorage.getAuditEntries();
        }

        @Override
        public AuditReader auditReader() {
            return auditStore.getAuditReader();
        }

        @Override
        public Result compact() {
            return auditStore.getAuditCompactor().compact();
        }

        @Override
        public Result writeCurrentState(AuditEntry entry) {
            throw new UnsupportedOperationException(
                    "InMemoryAuditStore has no current-state write path, so the property that uses this is"
                            + " deliberately not run here");
        }

        @Override
        public void reset() {
            auditStorage.clear();
        }
    }
}
