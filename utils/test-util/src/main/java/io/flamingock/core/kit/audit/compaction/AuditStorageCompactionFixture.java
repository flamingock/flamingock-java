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
package io.flamingock.core.kit.audit.compaction;

import io.flamingock.core.kit.audit.AuditStorage;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditReader;
import io.flamingock.internal.core.external.store.CommunityAuditStore;
import io.flamingock.internal.util.Result;

import java.util.List;

/**
 * The fixture every store can use as-is: a {@link CommunityAuditStore} for the operations under test and
 * that store's {@link AuditStorage} for raw seeding and physical read-back.
 * <p>
 * Both already exist in each store's test setup, so wiring the conformance suite into a store is one line
 * rather than six method implementations.
 */
public class AuditStorageCompactionFixture implements AuditCompactionFixture {

    private final CommunityAuditStore auditStore;
    private final AuditStorage auditStorage;
    private final String stageId;

    /**
     * @param auditStore   the initialized store under test
     * @param auditStorage raw access to the same underlying audit table
     * @param stageId      the stage used to obtain a persistence for the ordinary write path; any stage id
     *                     works, since compaction is table-global and the write is only used to prove the
     *                     write path still functions
     */
    public AuditStorageCompactionFixture(CommunityAuditStore auditStore,
                                         AuditStorage auditStorage,
                                         String stageId) {
        this.auditStore = auditStore;
        this.auditStorage = auditStorage;
        this.stageId = stageId;
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
        // Resolved per call rather than cached: with journal events enabled the persistence carries a
        // per-stage journal sequencer, and reusing one built before compaction would hand out a stale
        // stream position.
        return auditStore.getPersistenceFactory().get(stageId).writeEntry(entry);
    }

    @Override
    public void reset() {
        auditStorage.clear();
    }
}
