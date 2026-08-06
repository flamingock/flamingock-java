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
package io.flamingock.store.couchbase.internal;

import com.couchbase.client.java.transactions.TransactionAttemptContext;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.context.RuntimeContext;
import io.flamingock.internal.common.core.feature.Features;
import io.flamingock.internal.common.core.journal.JournalEvent;
import io.flamingock.internal.common.core.transaction.TransactionWrapper;
import io.flamingock.internal.core.configuration.community.CommunityConfigurable;
import io.flamingock.internal.core.context.BasicRuntimeContext;
import io.flamingock.internal.core.external.store.audit.community.AbstractCommunityAuditPersistence;
import io.flamingock.internal.core.journal.JournalEventSequencer;
import io.flamingock.internal.util.FeatureFlag;
import io.flamingock.internal.util.Result;
import io.flamingock.internal.util.id.RunnerId;

import java.util.List;

public class CouchbaseAuditPersistence extends AbstractCommunityAuditPersistence {

    private final CouchbaseAuditor auditor;
    private final CouchbaseJournalEventStore journalEventStore;
    private final JournalEventSequencer journalEventSequencer;
    private final TransactionWrapper txWrapper;
    private final String scopeName;
    private final String auditRepositoryName;
    private final String journalRepositoryName;
    private final boolean autoCreate;


    public CouchbaseAuditPersistence(CommunityConfigurable localConfiguration,
                                     CouchbaseAuditor auditor,
                                     CouchbaseJournalEventStore journalEventStore,
                                     JournalEventSequencer journalEventSequencer,
                                     TransactionWrapper txWrapper,
                                     String scopeName,
                                     String auditRepositoryName,
                                     String journalRepositoryName,
                                     boolean autoCreate) {
        super(localConfiguration);
        this.auditor = auditor;
        this.journalEventStore = journalEventStore;
        this.journalEventSequencer = journalEventSequencer;
        this.txWrapper = txWrapper;
        this.scopeName = scopeName;
        this.auditRepositoryName = auditRepositoryName;
        this.journalRepositoryName = journalRepositoryName;
        this.autoCreate = autoCreate;
    }

    @Override
    protected void doInitialize(RunnerId runnerId) {
        auditor.initialize(autoCreate, scopeName, auditRepositoryName);
        // Creating the collection/indexes is what brings the journal collection into existence, so skipping
        // this keeps it from ever appearing while the flag is off. It must stay in step with the append in
        // writeEntry: skipping setup while still appending would let ctx.insert create the collection
        // implicitly and without indexes, voiding the stream-position and eventId-lookup guarantees.
        FeatureFlag.ifEnabled(Features.JOURNAL_EVENTS, () -> journalEventStore.initialize(autoCreate, scopeName, journalRepositoryName));
    }


    @Override
    public List<AuditEntry> getAuditHistory() {
        return auditor.getAuditHistory();
    }

    @Override
    public Result writeEntry(AuditEntry auditEntry) {
        // Read once rather than per branch: the journal append and the audit write shape are two halves of one
        // model. With events, the audit record is the change's current state and the journal is the history;
        // without them, the audit record set is itself the history.
        if (FeatureFlag.isEnabled(Features.JOURNAL_EVENTS)) {
            RuntimeContext baseContext = new BasicRuntimeContext("write-changeState-" + auditEntry.getChangeId());
            Result result = txWrapper.wrapInTransaction(baseContext, runtimeContext -> {
                TransactionAttemptContext ctx = runtimeContext.getContext().getRequiredDependencyValue(TransactionAttemptContext.class);
                JournalEvent<AuditEntry> journalEvent = journalEventSequencer.newEvent(auditEntry);
                journalEventStore.contributeToTransaction(ctx, journalEvent);
                return auditor.contributeToTransaction(ctx, auditEntry);
            });
            // Spends the stream position, and only a committed transaction attempt may reach this line. A
            // normal return from wrapInTransaction does NOT in general mean commit — CouchbaseTxWrapper
            // returns normally after a deliberate rollback too, when the operation's result is a FailedStep.
            // It is sound here because this operation returns a Result, which can never be a FailedStep, so
            // the only way to return normally is a committed attempt; a failing attempt is caught and
            // rethrown as TransactionFailedException (see CouchbaseTxWrapper — it doesn't yet wrap that as
            // DatabaseTransactionException, a known deviation from the TransactionWrapper contract, tracked
            // separately from this ticket). Keep that true: an operation that could return a failed step
            // would silently burn a position and gap the stream, and a contiguous sequence is what lets a
            // consumer tell "in flight" from "lost".
            journalEventSequencer.confirm();
            return result;
        } else {
            return auditor.append(auditEntry);
        }
    }
}
