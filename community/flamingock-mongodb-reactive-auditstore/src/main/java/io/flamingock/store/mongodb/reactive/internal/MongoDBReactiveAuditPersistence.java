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
import io.flamingock.internal.common.core.journal.JournalEvent;
import io.flamingock.internal.common.core.transaction.TransactionWrapper;
import io.flamingock.internal.core.context.BasicRuntimeContext;
import io.flamingock.internal.core.configuration.community.CommunityConfigurable;
import io.flamingock.internal.core.external.store.audit.community.AbstractCommunityAuditPersistence;
import io.flamingock.internal.core.journal.JournalEventSequencer;
import io.flamingock.internal.util.FeatureFlag;
import io.flamingock.internal.util.Result;
import io.flamingock.internal.util.id.RunnerId;

import java.util.List;
import java.util.Objects;

public class MongoDBReactiveAuditPersistence extends AbstractCommunityAuditPersistence {

    private final MongoDBReactiveAuditRepository auditRepository;
    private final MongoDBReactiveJournalEventStore journalEventStore;
    private final JournalEventSequencer journalEventSequencer;
    private final boolean supportsTransactions;
    private final TransactionWrapper txWrapper;
    private final boolean autoCreate;

    /**
     * @param localConfiguration local Community configuration
     * @param auditRepository audit state repository
     * @param journalEventStore journal event store
     * @param journalEventSequencer sequencer for this persistence stream
     * @param supportsTransactions whether journal and audit writes must share a MongoDB transaction
     * @param txWrapper transaction wrapper; must be non-null if and only if transactions are supported
     * @param autoCreate whether required MongoDB collections and indexes may be created
     */
    public MongoDBReactiveAuditPersistence(CommunityConfigurable localConfiguration,
                                           MongoDBReactiveAuditRepository auditRepository,
                                           MongoDBReactiveJournalEventStore journalEventStore,
                                           JournalEventSequencer journalEventSequencer,
                                           boolean supportsTransactions,
                                           TransactionWrapper txWrapper,
                                           boolean autoCreate) {
        super(localConfiguration);
        this.auditRepository = auditRepository;
        this.journalEventStore = journalEventStore;
        this.journalEventSequencer = journalEventSequencer;
        this.supportsTransactions = supportsTransactions;
        this.txWrapper = validateTransactionWrapper(supportsTransactions, txWrapper);
        this.autoCreate = autoCreate;
    }

    private static TransactionWrapper validateTransactionWrapper(boolean supportsTransactions,
                                                                 TransactionWrapper txWrapper) {
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
    protected void doInitialize(RunnerId runnerId) {
        auditRepository.initialize(autoCreate);
		if (FeatureFlag.isEnabled(Features.JOURNAL_EVENTS, false)) {
            journalEventStore.initialize(autoCreate);
        }
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

        if (!supportsTransactions) {
            return writeJournalAndAuditWithoutTransaction(auditEntry);
        }

        return writeJournalAndAuditInTransaction(auditEntry);
    }

    private Result writeJournalAndAuditInTransaction(AuditEntry auditEntry) {
        RuntimeContext baseContext = new BasicRuntimeContext("write-changeState-" + auditEntry.getChangeId());
        Result result = txWrapper.wrapInTransaction(baseContext, runtimeContext -> {
            ClientSession clientSession = runtimeContext.getContext().getRequiredDependencyValue(ClientSession.class);
            JournalEvent<AuditEntry> journalEvent = journalEventSequencer.newEvent(auditEntry);
            journalEventStore.append(clientSession, journalEvent);
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
        JournalEvent<AuditEntry> journalEvent = journalEventSequencer.newEvent(auditEntry);
        journalEventStore.append(journalEvent);
        journalEventSequencer.confirm();
        return auditRepository.save(auditEntry);
    }
}
