/*
 * Copyright 2023 Flamingock (https://www.flamingock.io)
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
package io.flamingock.store.mongodb.sync.internal;

import com.mongodb.client.ClientSession;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.context.RuntimeContext;
import io.flamingock.internal.common.core.feature.Features;
import io.flamingock.internal.common.core.journal.JournalEvent;
import io.flamingock.internal.common.core.external.ExecutionWrapper;
import io.flamingock.internal.core.context.BasicRuntimeContext;
import io.flamingock.internal.core.external.store.audit.community.AbstractCommunityAuditPersistence;
import io.flamingock.internal.core.journal.JournalEventSequencer;
import io.flamingock.internal.util.FeatureFlag;
import io.flamingock.internal.util.Result;

import java.util.List;
import java.util.Objects;

public class MongoDBSyncAuditPersistence extends AbstractCommunityAuditPersistence {

    private final MongoDBSyncAuditRepository auditRepository;
    private final MongoDBSyncJournalWriter journalWriter;
    private final JournalEventSequencer journalEventSequencer;
    private final boolean supportsTransactions;
    private final ExecutionWrapper txWrapper;

    /**
     * @param auditRepository audit state repository
     * @param journalEventStore journal event store
     * @param journalEventSequencer sequencer for this persistence stream
     * @param supportsTransactions whether journal and audit writes must share a MongoDB transaction
     * @param txWrapper transaction wrapper; must be non-null if and only if transactions are supported
     */
    public MongoDBSyncAuditPersistence(MongoDBSyncAuditRepository auditRepository,
                                       MongoDBSyncJournalEventStore journalEventStore,
                                       JournalEventSequencer journalEventSequencer,
                                       boolean supportsTransactions,
                                       ExecutionWrapper txWrapper) {
        this.auditRepository = auditRepository;
        this.journalWriter = new MongoDBSyncJournalWriter(journalEventStore);
        this.journalEventSequencer = journalEventSequencer;
        this.supportsTransactions = supportsTransactions;
        this.txWrapper = validateTransactionWrapper(supportsTransactions, txWrapper);
    }

    private static ExecutionWrapper validateTransactionWrapper(boolean supportsTransactions,
                                                               ExecutionWrapper txWrapper) {
        if (supportsTransactions) {
            return Objects.requireNonNull(
                    txWrapper,
                    "txWrapper is required when transactions are supported"
            );
        }
        if (txWrapper != null) {
            throw new IllegalArgumentException("txWrapper must be null when transactions are not supported");
        }
        return null;
    }

    @Override
    public List<AuditEntry> getAuditHistory() {
        return auditRepository.getAuditHistory();
    }

    @Override
    public Result writeEntry(AuditEntry auditEntry) {
        // Read once rather than per branch: the journal append and the audit write shape are two halves of one
        // model. With events, the audit record is the change's current state and the journal is the history;
        // without them, the audit record set is itself the history.
        if (FeatureFlag.isDisabled(Features.JOURNAL_EVENTS, false)) {
            return auditRepository.append(auditEntry);
        }
        if (journalEventSequencer == null) {
            throw new IllegalStateException("MongoDB sync journal writes require a sequencer");
        }
        synchronized (journalEventSequencer) {
            try {
                return supportsTransactions ? writeJournalAndAuditInTransaction(auditEntry)
                        : writeJournalAndAuditWithoutTransaction(auditEntry);
            } catch (RuntimeException | Error failure) {
                journalEventSequencer.markWriteOutcomeUncertain();
                throw failure;
            }
        }
    }

    private Result writeJournalAndAuditInTransaction(AuditEntry auditEntry) {
        RuntimeContext baseContext = new BasicRuntimeContext("write-changeState-" + auditEntry.getChangeId());
        Result result = txWrapper.wrapExecution(baseContext, runtimeContext -> {
            ClientSession clientSession = runtimeContext.getContext().getRequiredDependencyValue(ClientSession.class);
            journalWriter.write(clientSession, journalEventSequencer, auditEntry);
            return auditRepository.save(clientSession, auditEntry);
        });
        // Spends the stream position, and only a committed transaction may reach this line. In general a
        // normal return from wrapExecution does NOT mean commit — a FailedStep result is returned
        // after a rollback, without an exception. It is sound here because this operation returns a
        // Result, which can never be a FailedStep, so the commit branch is the only graceful path; a
        // failing commit is caught and rethrown as DatabaseTransactionException. Keep that true: an
        // operation that could return a failed step would silently burn a position and gap the stream,
        // and a contiguous sequence is what lets a consumer tell "in flight" from "lost".
        journalEventSequencer.confirm();
        return result;
    }

    /**
     * Persists journal and current audit state when the concrete MongoDB deployment does not support
     * transactions. The journal is written and its sequence confirmed first so a failure cannot leave an
     * audit state without its corresponding historical event. Consequently, a later audit-state failure can
     * leave a durable journal event while the current-state projection remains stale; this is the explicitly
     * accepted best-effort behavior for non-transactional deployments.
     */
    private Result writeJournalAndAuditWithoutTransaction(AuditEntry auditEntry) {
        journalWriter.write(journalEventSequencer, auditEntry);
        return auditRepository.save(auditEntry);
    }

}
