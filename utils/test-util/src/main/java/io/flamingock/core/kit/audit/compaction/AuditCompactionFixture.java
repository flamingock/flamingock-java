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

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.util.Result;

import java.util.List;

/**
 * What {@link AuditCompactionConformance} needs from a store in order to verify it.
 * <p>
 * Kept to the smallest set that can express the contract: seed a ledger, read it back physically, read it
 * back semantically, compact, exercise the ordinary write path, and isolate one scenario from the next.
 * Most stores can get all six from {@link AuditStorageCompactionFixture} without writing any of them.
 */
public interface AuditCompactionFixture {

    /**
     * Inserts one record directly, bypassing the store's own write path.
     * <p>
     * Bypassing matters: the current-state write path is precisely what collapses records, so going through
     * it could not produce the ledger shape being migrated away from.
     */
    void seedLedgerEntry(AuditEntry entry);

    /**
     * Returns the stored records as they physically exist — one element per record, with no aggregation.
     * <p>
     * Distinct from {@link #auditReader()} on purpose. The reader collapses records to the effective state
     * per change, so it cannot tell a compacted store from an uncompacted one; only a physical read can.
     */
    List<AuditEntry> readStoredRecords();

    /**
     * The store's own reader, the semantic view the operational path consumes.
     */
    io.flamingock.internal.common.core.audit.AuditReader auditReader();

    /**
     * Invokes compaction on the store under test.
     */
    Result compact();

    /**
     * Writes a current state through the store's ordinary write path.
     * <p>
     * Needed because "compaction leaves the normal write path working" is a real guarantee that a store can
     * break — notably SQL, whose current-state update fails when it matches more than one row.
     */
    Result writeCurrentState(AuditEntry entry);

    /**
     * Empties the audit store, so each scenario starts from a known state.
     */
    void reset();
}
