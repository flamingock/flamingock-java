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
import io.flamingock.internal.common.core.transaction.TransactionWrapper;
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

public class DynamoDBAuditPersistence extends AbstractCommunityAuditPersistence {

    private final DynamoDBAuditRepository auditRepository;
    private final DynamoDBJournalEventStore journalEventStore;
    private JournalEventSequencer journalEventSequencer;
    private final TransactionWrapper txWrapper;
    private final String auditTableName;
    private final long readCapacityUnits;
    private final long writeCapacityUnits;
    private final boolean autoCreate;

    /**
     * Creates a persistence over explicitly supplied audit, journal and transaction collaborators.
     *
     * @param localConfiguration          community configuration
     * @param auditRepository             repository for audits
     * @param journalEventStore           journal store receiving staged events
     * @param journalEventSequencer       sequencer for the stage journal stream
     * @param txWrapper                   transaction wrapper shared with the target system
     * @param auditTableName              audit table name
     * @param readCapacityUnits           audit and journal read capacity
     * @param writeCapacityUnits          audit and journal write capacity
     * @param autoCreate                  whether missing tables may be created
     */
    public DynamoDBAuditPersistence(CommunityConfigurable localConfiguration,
                                    DynamoDBAuditRepository auditRepository,
                                    DynamoDBJournalEventStore journalEventStore,
                                    JournalEventSequencer journalEventSequencer,
                                    TransactionWrapper txWrapper,
                                    String auditTableName,
                                    long readCapacityUnits,
                                    long writeCapacityUnits,
                                    boolean autoCreate) {
        super(localConfiguration);
        this.auditRepository = auditRepository;
        this.journalEventStore = journalEventStore;
        this.journalEventSequencer = journalEventSequencer;
        this.txWrapper = txWrapper;
        this.auditTableName = auditTableName;
        this.readCapacityUnits = readCapacityUnits;
        this.writeCapacityUnits = writeCapacityUnits;
        this.autoCreate = autoCreate;
    }

    @Override
    protected void doInitialize(RunnerId runnerId) {
        auditRepository.initialize(
                autoCreate,
                auditTableName,
                readCapacityUnits,
                writeCapacityUnits);
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
            RuntimeContext baseContext = new BasicRuntimeContext("write-changeState-" + auditEntry.getChangeId());
            Result result = txWrapper.wrapInTransaction(baseContext, runtimeContext -> {
                TransactWriteItemsEnhancedRequest.Builder builder = runtimeContext.getContext()
                        .getRequiredDependencyValue(TransactWriteItemsEnhancedRequest.Builder.class);
                JournalEvent<AuditEntry> journalEvent = journalEventSequencer.newEvent(auditEntry);
                journalEventStore.contributeToTransaction(builder, journalEvent);
                return auditRepository.contributeToTransaction(builder, auditEntry);
            });
            journalEventSequencer.confirm();
            return result;
        }
        return auditRepository.writeEntry(auditEntry);
    }

    private static boolean isJournalEventsEnabled() {
        try {
            return FeatureFlag.isEnabled(Features.JOURNAL_EVENTS, false);
        } catch (RuntimeException exception) {
            return false;
        }
    }

}
