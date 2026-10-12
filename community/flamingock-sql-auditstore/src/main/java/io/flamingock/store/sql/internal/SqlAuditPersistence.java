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
package io.flamingock.store.sql.internal;

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.context.RuntimeContext;
import io.flamingock.internal.common.core.feature.Features;
import io.flamingock.internal.common.core.journal.JournalEvent;
import io.flamingock.internal.common.core.external.ExecutionWrapper;
import io.flamingock.internal.core.configuration.community.CommunityConfigurable;
import io.flamingock.internal.core.context.BasicRuntimeContext;
import io.flamingock.internal.core.external.store.audit.community.AbstractCommunityAuditPersistence;
import io.flamingock.internal.core.journal.JournalEventSequencer;
import io.flamingock.internal.util.FeatureFlag;
import io.flamingock.internal.util.Result;
import io.flamingock.internal.util.id.RunnerId;

import java.sql.Connection;
import java.util.List;
import java.util.UUID;

public class SqlAuditPersistence extends AbstractCommunityAuditPersistence {

    private final SqlAuditRepository auditRepository;
    private final SqlJournalEventStore journalEventStore;
    private final JournalEventSequencer journalEventSequencer;
    private final ExecutionWrapper txWrapper;
    private final boolean journalEventsEnabled;

    /**
     * Creates persistence over collaborators whose schema readiness belongs to the store and stage factory.
     *
     * @param localConfiguration    community configuration
     * @param auditRepository       ready audit table writer/reader
     * @param journalEventStore     ready relational Journal Event store
     * @param journalEventSequencer stage-scoped sequence allocator
     * @param txWrapper             transaction wrapper shared with the SQL target system
     * @param journalEventsEnabled  feature flag snapshot captured for this stage
     */
    public SqlAuditPersistence(CommunityConfigurable localConfiguration,
                               SqlAuditRepository auditRepository,
                               SqlJournalEventStore journalEventStore,
                               JournalEventSequencer journalEventSequencer,
                               ExecutionWrapper txWrapper,
                               boolean journalEventsEnabled) {
        super(localConfiguration);
        this.auditRepository = auditRepository;
        this.journalEventStore = journalEventStore;
        this.journalEventSequencer = journalEventSequencer;
        this.txWrapper = txWrapper;
        this.journalEventsEnabled = journalEventsEnabled;
    }

    @Override
    protected void doInitialize(RunnerId runnerId) {
        if (auditRepository == null) {
            throw new IllegalStateException("SQL persistence is missing its audit repository");
        }
        if (journalEventsEnabled) {
            if (journalEventStore == null || journalEventSequencer == null || txWrapper == null) {
                throw new IllegalStateException("Journal-enabled SQL persistence is missing transaction collaborators");
            }
        }
    }

    @Override
    public List<AuditEntry> getAuditHistory() {
        return auditRepository.getAuditHistory();
    }

    // Keep the lock through transaction commit: save uses a caller-owned connection.
    @Override
    public synchronized Result writeEntry(AuditEntry auditEntry) {
        if (!journalEventsEnabled) {
            return auditRepository.append(auditEntry);
        }

        RuntimeContext baseContext = new BasicRuntimeContext("write-changeState-" + auditEntry.getChangeId());
        Result result = txWrapper.wrapExecution(baseContext, runtimeContext -> {
            Connection connection = runtimeContext.getContext().getRequiredDependencyValue(Connection.class);
            appendEventFrom(auditEntry, connection);
            Result currentStateResult = auditRepository.save(connection, auditEntry);
            if (currentStateResult instanceof Result.Error) {
                throw new IllegalStateException("Failed to replace local current audit state",
                        ((Result.Error) currentStateResult).getError());
            }
            return currentStateResult == null ? Result.OK() : currentStateResult;
        });
        journalEventSequencer.confirm();
        return result;
    }

    /**
     * Appends historical audit keys and payload without replacing the current-state projection.
     *
     * @param entry source history entry
     * @return repository result, independently of JOURNAL_EVENTS
     */
    @Override
    public synchronized Result append(AuditEntry entry) {
        return auditRepository.append(entry);
    }

    /**
     * Appends only a CHANGE_STATE event on this persistence stream using an owned transaction.
     *
     * @param payload original audit payload
     * @return successful durable append result
     */
    @Override
    public synchronized Result appendEventFrom(AuditEntry payload) {
        if (!FeatureFlag.isEnabled(Features.JOURNAL_EVENTS, false)) {
            throw new IllegalStateException("Journal history requires JOURNAL_EVENTS to be enabled");
        }
        RuntimeContext context = new BasicRuntimeContext("append-journal-" + UUID.randomUUID());
        Result result = txWrapper.wrapExecution(context, runtimeContext -> {
            Connection connection = runtimeContext.getContext().getRequiredDependencyValue(Connection.class);
            return appendEventFrom(payload, connection);
        });
        journalEventSequencer.confirm();
        return result;
    }

    private Result appendEventFrom(AuditEntry payload, Connection connection) {
        JournalEvent<AuditEntry> event = journalEventSequencer.newEvent(payload);
        journalEventStore.append(connection, event);
        return Result.OK();
    }
}
