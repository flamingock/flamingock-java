/*
 * Copyright 2025 Flamingock (https://www.flamingock.io)
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
package io.flamingock.core.kit.inmemory;

import io.flamingock.core.kit.audit.AuditStorage;
import io.flamingock.core.kit.audit.TestAuditReader;
import io.flamingock.core.kit.audit.TestAuditWriter;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.feature.Features;
import io.flamingock.internal.common.core.journal.JournalEvent;
import io.flamingock.internal.core.external.store.audit.community.CommunityAuditPersistence;
import io.flamingock.internal.core.journal.JournalEventSequencer;
import io.flamingock.internal.util.FeatureFlag;
import io.flamingock.internal.util.Result;

import java.util.List;

public class InternalInMemoryTestAuditPersistence implements CommunityAuditPersistence {

    private final AuditStorage auditStorage;
    private final TestAuditWriter auditWriter;
    private final TestAuditReader auditReader;
    private final InternalInMemoryJournalEventStore journalEventStore;
    private final JournalEventSequencer journalEventSequencer;

    public InternalInMemoryTestAuditPersistence(AuditStorage auditStorage) {
        this(auditStorage, null, null);
    }

    /**
     * @param journalEventStore    the journal to append to when {@code Features.JOURNAL_EVENTS} is enabled, or
     *                             {@code null} to always use the plain append path (equivalent to the
     *                             single-arg constructor)
     * @param journalEventSequencer the position source for {@code journalEventStore}'s stream; required
     *                              whenever {@code journalEventStore} is non-null
     */
    public InternalInMemoryTestAuditPersistence(AuditStorage auditStorage,
                                                 InternalInMemoryJournalEventStore journalEventStore,
                                                 JournalEventSequencer journalEventSequencer) {
        this.auditStorage = auditStorage;
        this.auditWriter = new TestAuditWriter(auditStorage);
        this.auditReader = new TestAuditReader(auditStorage);
        this.journalEventStore = journalEventStore;
        this.journalEventSequencer = journalEventSequencer;
    }

    @Override
    public List<AuditEntry> getAuditHistory() {
        return auditReader.getAuditHistory();
    }

    @Override
    public Result writeEntry(AuditEntry auditEntry) {
        // Mirrors MongoDBSyncAuditPersistence#writeEntry: with events, the audit record is the change's
        // current state and the journal is the history; without them, the audit record set is itself the
        // history (one row per state transition, via the plain append path below).
        if (journalEventStore != null && journalEventSequencer != null && FeatureFlag.isEnabled(Features.JOURNAL_EVENTS)) {
            JournalEvent<AuditEntry> journalEvent = journalEventSequencer.newEvent(auditEntry);
            journalEventStore.write(journalEvent);
            if (auditStorage instanceof InternalInMemoryAuditStorage) {
                ((InternalInMemoryAuditStorage) auditStorage).upsertAuditEntry(auditEntry);
            } else {
                // Defensive fallback for a custom AuditStorage without upsert support — still correct, just
                // without the current-state collapsing.
                auditStorage.addAuditEntry(auditEntry);
            }
            journalEventSequencer.confirm();
            return Result.OK();
        }
        return auditWriter.writeEntry(auditEntry);
    }
}
