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

    private final DynamoDBAuditor auditReader;
    private final DynamoDBAuditWriter auditWriter;
    private final String auditTableName;
    private final long readCapacityUnits;
    private final long writeCapacityUnits;
    private final boolean autoCreate;
    private final TransactionWrapper txWrapper;
    private final DynamoDBJournalEventStore journalEventStore;
    private final JournalEventSequencerFactory journalEventSequencerFactory;
    private final String stageId;
    private JournalEventSequencer journalEventSequencer;

    /**
     * Creates a persistence over explicitly supplied audit, journal and transaction collaborators.
     *
     * @param localConfiguration          community configuration
     * @param auditReader                 reader for the audit table
     * @param auditWriter                 writer for append and current-state writes
     * @param journalEventStore           journal store receiving staged events
     * @param txWrapper                   transaction wrapper shared with the target system
     * @param journalEventSequencerFactory factory for per-stage journal sequencers
     * @param stageId                     stage whose journal stream receives events
     * @param auditTableName              audit table name
     * @param readCapacityUnits           audit and journal read capacity
     * @param writeCapacityUnits          audit and journal write capacity
     * @param autoCreate                  whether missing tables may be created
     */
    public DynamoDBAuditPersistence(CommunityConfigurable localConfiguration,
                                     DynamoDBAuditor auditReader,
                                     DynamoDBAuditWriter auditWriter,
                                     DynamoDBJournalEventStore journalEventStore,
                                     TransactionWrapper txWrapper,
                                     JournalEventSequencerFactory journalEventSequencerFactory,
                                     String stageId,
                                     String auditTableName,
                                     long readCapacityUnits,
                                     long writeCapacityUnits,
                                     boolean autoCreate) {
        super(localConfiguration);
        this.auditReader = auditReader;
        this.auditWriter = auditWriter;
        this.journalEventStore = journalEventStore;
        this.auditTableName = auditTableName;
        this.readCapacityUnits = readCapacityUnits;
        this.writeCapacityUnits = writeCapacityUnits;
        this.autoCreate = autoCreate;
        this.txWrapper = txWrapper;
        this.journalEventSequencerFactory = journalEventSequencerFactory;
        this.stageId = stageId;
    }

    @Override
    protected void doInitialize(RunnerId runnerId) {
        auditReader.initialize(
                autoCreate,
                auditTableName,
                readCapacityUnits,
                writeCapacityUnits);
        auditWriter.initialize(
                autoCreate,
                auditTableName,
                readCapacityUnits,
                writeCapacityUnits);
        if (isJournalEventsEnabled()) {
            requireJournalWiring();
            journalEventStore.initialize(autoCreate);
            journalEventSequencer = journalEventSequencerFactory.forStream(stageId);
        }
    }

    @Override
    public List<AuditEntry> getAuditHistory() {
        return auditReader.getAuditHistory();
    }

    @Override
    public Result writeEntry(AuditEntry auditEntry) {
        if (isJournalEventsEnabled()) {
            requireJournalWiring();
            RuntimeContext baseContext = new BasicRuntimeContext("write-changeState-" + auditEntry.getChangeId());
            Result result = txWrapper.wrapInTransaction(baseContext, runtimeContext -> {
                TransactWriteItemsEnhancedRequest.Builder builder = runtimeContext.getContext()
                        .getRequiredDependencyValue(TransactWriteItemsEnhancedRequest.Builder.class);
                JournalEvent<AuditEntry> journalEvent = journalEventSequencer.newEvent(auditEntry);
                journalEventStore.contributeToTransaction(builder, journalEvent);
                auditWriter.contributeToTransaction(builder, auditEntry);
                return Result.OK();
            });
            journalEventSequencer.confirm();
            return result;
        }
        return auditWriter.writeEntry(auditEntry);
    }

    private void requireJournalWiring() {
        if (txWrapper == null || journalEventStore == null || journalEventSequencerFactory == null) {
            throw new IllegalStateException("Journal-enabled persistence requires transaction, journal, and sequencer wiring");
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
