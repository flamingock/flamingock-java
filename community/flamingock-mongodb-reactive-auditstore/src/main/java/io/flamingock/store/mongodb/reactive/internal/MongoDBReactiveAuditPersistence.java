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
package io.flamingock.store.mongodb.reactive.internal;

import com.mongodb.reactivestreams.client.ClientSession;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.context.RuntimeContext;
import io.flamingock.internal.common.core.feature.Features;
import io.flamingock.internal.common.core.external.ExecutionWrapper;
import io.flamingock.internal.core.context.BasicRuntimeContext;
import io.flamingock.internal.core.external.store.audit.community.AbstractCommunityAuditPersistence;
import io.flamingock.internal.core.journal.JournalEventSequencer;
import io.flamingock.internal.util.FeatureFlag;
import io.flamingock.internal.util.Result;

import java.util.List;
import java.util.Objects;

public class MongoDBReactiveAuditPersistence extends AbstractCommunityAuditPersistence {

    private final MongoDBReactiveAuditRepository auditRepository;
    private final MongoDBReactiveJournalEventStore journalEventStore;
    private final MongoDBReactiveJournalWriter journalWriter;
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
    public MongoDBReactiveAuditPersistence(MongoDBReactiveAuditRepository auditRepository,
                                           MongoDBReactiveJournalEventStore journalEventStore,
                                           JournalEventSequencer journalEventSequencer,
                                           boolean supportsTransactions,
                                           ExecutionWrapper txWrapper) {
        this.auditRepository = auditRepository;
        this.journalEventStore = journalEventStore;
        this.journalWriter = new MongoDBReactiveJournalWriter(journalEventStore);
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
		if (FeatureFlag.isDisabled(Features.JOURNAL_EVENTS, false)) {
            return auditRepository.append(auditEntry);
        }

        if (journalEventStore == null || journalEventSequencer == null) {
            throw new IllegalStateException("MongoDB reactive journal writes require a sequencer");
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

        // Result cannot represent FailedStep. The transaction wrapper has therefore committed successfully
        // whenever control reaches this line; only then is the in-memory stream position spent.
        journalEventSequencer.confirm();
        return result;
    }

    /**
     * Persists journal and current audit state when the concrete MongoDB deployment does not support
     * transactions. The journal is written and its sequence confirmed first so a failure cannot leave an
     * audit state without its corresponding historical event.
     */
    private Result writeJournalAndAuditWithoutTransaction(AuditEntry auditEntry) {
        journalWriter.write(journalEventSequencer, auditEntry);
        return auditRepository.save(auditEntry);
    }
}
