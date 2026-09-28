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
import io.flamingock.internal.common.core.external.ExecutionWrapper;
import io.flamingock.internal.core.context.BasicRuntimeContext;
import io.flamingock.internal.core.external.store.audit.community.AbstractCommunityAuditPersistence;
import io.flamingock.internal.core.journal.JournalEventSequencer;
import io.flamingock.internal.util.FeatureFlag;
import io.flamingock.internal.util.Result;

import java.util.List;

public class CouchbaseAuditPersistence extends AbstractCommunityAuditPersistence {

    private final CouchbaseAuditor auditor;
    private final CouchbaseJournalWriter journalWriter;
    private final JournalEventSequencer journalEventSequencer;
    private final ExecutionWrapper txWrapper;


    public CouchbaseAuditPersistence(CouchbaseAuditor auditor,
                                     JournalEventSequencer journalEventSequencer,
                                     ExecutionWrapper txWrapper,
                                     CouchbaseJournalWriter journalWriter) {
        this.auditor = auditor;
        this.journalWriter = journalWriter;
        this.journalEventSequencer = journalEventSequencer;
        this.txWrapper = txWrapper;
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
            synchronized (journalEventSequencer) {
                try {
                    JournalEvent<AuditEntry> event = journalEventSequencer.newEvent(auditEntry);
                    Result result = txWrapper.wrapExecution(baseContext, runtimeContext -> {
                        TransactionAttemptContext ctx = runtimeContext.getContext()
                                .getRequiredDependencyValue(TransactionAttemptContext.class);
                        journalWriter.write(ctx, event);
                        return auditor.contributeToTransaction(ctx, auditEntry);
                    });
                    // Result cannot be a FailedStep: a normal wrapper return means commit. Keep the
                    // transaction and confirmation under the same stream lock as journal-only writes.
                    journalEventSequencer.confirm();
                    return result;
                } catch (RuntimeException | Error failure) {
                    journalEventSequencer.markWriteOutcomeUncertain();
                    throw failure;
                }
            }
        } else {
            return auditor.append(auditEntry);
        }
    }
}
