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
package io.flamingock.core.e2e;

import io.flamingock.core.kit.audit.compaction.AuditCompactionConformance;
import io.flamingock.core.kit.audit.compaction.AuditStorageCompactionFixture;
import io.flamingock.core.kit.inmemory.InternalInMemoryAuditStorage;
import io.flamingock.core.kit.inmemory.InternalInMemoryLockStorage;
import io.flamingock.core.kit.inmemory.InternalInMemoryTestAuditStore;
import io.flamingock.internal.common.core.context.Dependency;
import io.flamingock.internal.core.context.SimpleContext;
import io.flamingock.internal.util.id.RunnerId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Runs the shared audit-compaction contract against the in-memory audit store.
 * <p>
 * The in-memory store is the reference implementation, so this is what proves the contract and the
 * conformance suite are themselves coherent — before any database-backed store implements compaction and
 * has to tell its own bugs apart from the suite's.
 * <p>
 * One property per test so a failure names the clause it broke, which is also the shape the four NoSQL
 * stores will use.
 */
class InMemoryAuditCompactionConformanceTest {

    private static final String STAGE_ID = "compaction-stage";

    private AuditCompactionConformance conformance;

    @BeforeEach
    void setUp() {
        InternalInMemoryAuditStorage auditStorage = new InternalInMemoryAuditStorage();
        InternalInMemoryTestAuditStore auditStore =
                new InternalInMemoryTestAuditStore(auditStorage, new InternalInMemoryLockStorage());

        SimpleContext context = new SimpleContext();
        context.addDependency(new Dependency(RunnerId.class, RunnerId.generate("compaction-conformance")));
        auditStore.initialize(context);

        conformance = new AuditCompactionConformance(
                new AuditStorageCompactionFixture(auditStore, auditStorage, STAGE_ID));
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
}
