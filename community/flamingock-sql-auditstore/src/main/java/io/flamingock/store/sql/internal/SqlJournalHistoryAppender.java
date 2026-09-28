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

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.JournalHistoryAppender;
import io.flamingock.internal.common.core.external.ExecutionWrapper;
import io.flamingock.internal.common.core.journal.JournalEvent;
import io.flamingock.internal.core.context.BasicRuntimeContext;
import io.flamingock.internal.core.journal.JournalEventSequencer;
import io.flamingock.internal.core.journal.JournalEventSequencerFactory;
import io.flamingock.internal.util.Result;

import java.sql.Connection;

/** Writes journal events on either a caller's audit transaction or an independent audit transaction. */
public class SqlJournalHistoryAppender implements JournalHistoryAppender {
    private final SqlJournalEventStore journalStore;
    private final JournalEventSequencerFactory sequencers;
    private final ExecutionWrapper auditTxWrapper;

    public SqlJournalHistoryAppender(SqlJournalEventStore journalStore,
                                   JournalEventSequencerFactory sequencers,
                                   ExecutionWrapper auditTxWrapper) {
        this.journalStore = journalStore;
        this.sequencers = sequencers;
        this.auditTxWrapper = auditTxWrapper;
    }

    @Override
    public Result append(String streamId, AuditEntry entry) {
        JournalEventSequencer sequencer = sequencers.forStream(streamId);
        synchronized (sequencer) {
            try {
                Result result = auditTxWrapper.wrapExecution(new BasicRuntimeContext("append-journal-" + streamId),
                        context -> append(context.getContext().getRequiredDependencyValue(Connection.class),
                                sequencer, entry));
                sequencer.confirm();
                return result;
            } catch (RuntimeException | Error failure) {
                sequencer.markWriteOutcomeUncertain();
                throw failure;
            }
        }
    }

    /** Appends without committing; the caller confirms the sequence only after its transaction commits. */
    public Result append(Connection connection, JournalEventSequencer sequencer, AuditEntry entry) {
        JournalEvent<AuditEntry> event = sequencer.newEvent(entry);
        journalStore.append(connection, event);
        return Result.OK();
    }
}
