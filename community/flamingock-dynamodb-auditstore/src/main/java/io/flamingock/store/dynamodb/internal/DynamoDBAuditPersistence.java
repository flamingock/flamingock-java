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
import io.flamingock.internal.common.core.journal.JournalEvent;
import io.flamingock.internal.common.core.external.ExecutionWrapper;
import io.flamingock.internal.core.configuration.community.CommunityConfigurable;
import io.flamingock.internal.core.context.BasicRuntimeContext;
import io.flamingock.internal.core.external.store.audit.community.AbstractCommunityAuditPersistence;
import io.flamingock.internal.core.journal.JournalEventSequencer;
import io.flamingock.internal.core.journal.JournalEventSequencerFactory;
import io.flamingock.internal.util.FeatureFlag;
import io.flamingock.internal.util.Result;
import io.flamingock.internal.util.id.RunnerId;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;

import java.util.List;
import java.util.UUID;

public class DynamoDBAuditPersistence extends AbstractCommunityAuditPersistence {

    private final DynamoDBAuditRepository auditRepository;
    private final DynamoDBJournalEventStore journalEventStore;
    private final JournalEventSequencerFactory journalEventSequencerFactory;
    private final String stageId;
    private final ExecutionWrapper txWrapper;
    private final boolean autoCreate;

    /**
     * Creates a persistence over explicitly supplied audit, journal and transaction collaborators.
     *
     * @param localConfiguration          community configuration
     * @param auditRepository             repository for audits
     * @param journalEventStore           journal store receiving staged events
     * @param journalEventSequencerFactory store-shared factory for destination streams
     * @param stageId                     stream used by normal audit writing
     * @param txWrapper                   transaction wrapper shared with the target system
     * @param autoCreate                  whether missing tables may be created
     */
    public DynamoDBAuditPersistence(CommunityConfigurable localConfiguration,
                                    DynamoDBAuditRepository auditRepository,
                                    DynamoDBJournalEventStore journalEventStore,
                                    JournalEventSequencerFactory journalEventSequencerFactory,
                                    String stageId,
                                    ExecutionWrapper txWrapper,
                                    boolean autoCreate) {
        super(localConfiguration);
        this.auditRepository = auditRepository;
        this.journalEventStore = journalEventStore;
        this.journalEventSequencerFactory = journalEventSequencerFactory;
        this.stageId = stageId;
        this.txWrapper = txWrapper;
        this.autoCreate = autoCreate;
    }

    @Override
    protected void doInitialize(RunnerId runnerId) {
        auditRepository.initialize(autoCreate);
        if (isJournalEventsEnabled()) {
            journalEventStore.initialize(autoCreate);
        }
    }

    @Override
    public List<AuditEntry> getAuditHistory() {
        return auditRepository.getAuditHistory();
    }

    @Override
    public Result writeEntry(AuditEntry auditEntry) {
        if (isJournalEventsEnabled()) {
            JournalEventSequencer journalEventSequencer = prepareJournalEventSequencer(stageId);
            RuntimeContext baseContext = new BasicRuntimeContext("write-changeState-" + auditEntry.getChangeId());
            Result result = txWrapper.wrapExecution(baseContext, runtimeContext -> {
                TransactWriteItemsEnhancedRequest.Builder builder = runtimeContext.getContext()
                        .getRequiredDependencyValue(TransactWriteItemsEnhancedRequest.Builder.class);
                appendEventFrom(auditEntry, journalEventSequencer, builder);
                Result auditResult = auditRepository.contributeToTransaction(builder, auditEntry);
                if (auditResult.isError()) {
                    throw new IllegalStateException("DynamoDB history write failed", ((Result.Error) auditResult).getError());
                }
                return auditResult;
            });
            journalEventSequencer.confirm();
            return result;
        }
        return auditRepository.writeEntry(auditEntry);
    }

    /**
     * Appends supplied history using the historical execution/change/state key, independently of the journal flag.
     *
     * @param entry historical audit entry to preserve
     * @return the repository write result
     */
    @Override
    public Result append(AuditEntry entry) {
        return auditRepository.writeEntry(entry);
    }

    /**
     * Creates and durably appends a CHANGE_STATE event to the payload's stage journal without writing audits.
     *
     * @param payload source audit entry preserved in the event
     * @return the journal-only transaction result after successful durable completion
     * @throws IllegalStateException if journal events are disabled or a write result reports failure
     */
    @Override
    public Result appendEventFrom(AuditEntry payload) {
        JournalEventSequencer journalEventSequencer = prepareJournalEventSequencer(payload.getStageId());
        RuntimeContext context = new BasicRuntimeContext("append-journal-" + UUID.randomUUID());
        Result result = txWrapper.wrapExecution(context, runtimeContext -> {
            TransactWriteItemsEnhancedRequest.Builder builder = runtimeContext.getContext()
                    .getRequiredDependencyValue(TransactWriteItemsEnhancedRequest.Builder.class);
            return appendEventFrom(payload, journalEventSequencer, builder);
        });
        journalEventSequencer.confirm();
        return result;
    }

    private Result appendEventFrom(AuditEntry payload,
                                   JournalEventSequencer journalEventSequencer,
                                   TransactWriteItemsEnhancedRequest.Builder builder) {
        requireJournalEnabled();
        JournalEvent<AuditEntry> event = journalEventSequencer.newEvent(payload);
        Result result = journalEventStore.contributeToTransaction(builder, event);
        if (result.isError()) {
            throw new IllegalStateException("DynamoDB history write failed", ((Result.Error) result).getError());
        }
        return result;
    }

    private JournalEventSequencer prepareJournalEventSequencer(String stageId) {
        requireJournalEnabled();
        journalEventStore.initialize(autoCreate);
        return journalEventSequencerFactory.forStream(stageId);
    }

    private static void requireJournalEnabled() {
        if (!isJournalEventsEnabled()) {
            throw new IllegalStateException("Journal history requires JOURNAL_EVENTS to be enabled");
        }
    }

    private static boolean isJournalEventsEnabled() {
        try {
            return FeatureFlag.isEnabled(Features.JOURNAL_EVENTS, false);
        } catch (RuntimeException exception) {
            return false;
        }
    }

}
