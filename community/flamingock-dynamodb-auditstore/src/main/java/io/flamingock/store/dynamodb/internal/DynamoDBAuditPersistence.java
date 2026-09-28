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
package io.flamingock.store.dynamodb.internal;

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.context.RuntimeContext;
import io.flamingock.internal.common.core.feature.Features;
import io.flamingock.internal.common.core.external.ExecutionWrapper;
import io.flamingock.internal.core.context.BasicRuntimeContext;
import io.flamingock.internal.core.external.store.audit.community.AbstractCommunityAuditPersistence;
import io.flamingock.internal.core.journal.JournalEventSequencer;
import io.flamingock.internal.util.FeatureFlag;
import io.flamingock.internal.util.Result;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;

import java.util.List;

public class DynamoDBAuditPersistence extends AbstractCommunityAuditPersistence {

    private final DynamoDBAuditRepository auditRepository;
    private final DynamoDBJournalWriter journalWriter;
    private JournalEventSequencer journalEventSequencer;
    private final ExecutionWrapper txWrapper;

    /**
     * Creates a persistence over explicitly supplied audit, journal and transaction collaborators.
     *
     * @param auditRepository             repository for audits
     * @param journalEventSequencer       sequencer for the stage journal stream
     * @param txWrapper                   transaction wrapper shared with the target system
     * @param journalWriter               explicitly configured journal writer
     */
    public DynamoDBAuditPersistence(DynamoDBAuditRepository auditRepository,
                                    JournalEventSequencer journalEventSequencer,
                                    ExecutionWrapper txWrapper,
                                    DynamoDBJournalWriter journalWriter) {
        this.auditRepository = auditRepository;
        this.journalWriter = journalWriter;
        this.journalEventSequencer = journalEventSequencer;
        this.txWrapper = txWrapper;
    }

    @Override
    public List<AuditEntry> getAuditHistory() {
        return auditRepository.getAuditHistory();
    }

    @Override
    public Result writeEntry(AuditEntry auditEntry) {
        if (isJournalEventsEnabled()) {
            synchronized (journalEventSequencer) {
                try {
                    RuntimeContext baseContext = new BasicRuntimeContext("write-changeState-" + auditEntry.getChangeId());
                    Result result = txWrapper.wrapExecution(baseContext, runtimeContext -> {
                        TransactWriteItemsEnhancedRequest.Builder builder = runtimeContext.getContext()
                                .getRequiredDependencyValue(TransactWriteItemsEnhancedRequest.Builder.class);
                        journalWriter.write(builder, journalEventSequencer, auditEntry);
                        return auditRepository.contributeToTransaction(builder, auditEntry);
                    });
                    journalEventSequencer.confirm();
                    return result;
                } catch (RuntimeException | Error exception) {
                    journalEventSequencer.markWriteOutcomeUncertain();
                    throw exception;
                }
            }
        }
        return auditRepository.append(auditEntry);
    }

    private static boolean isJournalEventsEnabled() {
        try {
            return FeatureFlag.isEnabled(Features.JOURNAL_EVENTS, false);
        } catch (RuntimeException exception) {
            return false;
        }
    }

}
