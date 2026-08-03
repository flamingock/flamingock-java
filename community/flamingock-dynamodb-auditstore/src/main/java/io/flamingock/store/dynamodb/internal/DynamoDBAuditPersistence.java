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
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.List;

public class DynamoDBAuditPersistence extends AbstractCommunityAuditPersistence {

    private final DynamoDbClient client;
    private final String auditTableName;
    private final long readCapacityUnits;
    private final long writeCapacityUnits;
    private final boolean autoCreate;
    private final TransactionWrapper txWrapper;
    private final DynamoDBJournalEventStore journalEventStore;
    private final JournalEventSequencer journalEventSequencer;
    private final JournalEventSequencerFactory journalEventSequencerFactory;

    private DynamoDBAuditor auditor;

    public DynamoDBAuditPersistence(DynamoDbClient client,
                                    String auditTableName,
                                    long readCapacityUnits,
                                    long writeCapacityUnits,
                                    boolean autoCreate,
                                    CommunityConfigurable localConfiguration) {
        this(client, null, null, null, null, auditTableName, readCapacityUnits, writeCapacityUnits,
                autoCreate, localConfiguration);
    }

    /**
     * Creates a persistence that can atomically stage an audit record and its journal event.
     *
     * @param client               DynamoDB client used by the audit and journal stores
     * @param txWrapper            transaction wrapper shared with the target system
     * @param journalEventStore    journal store receiving staged events
     * @param journalEventSequencer per-stage sequencer for the journal stream
     * @param journalEventSequencerFactory factory used to route imported events to their destination stage
     * @param auditTableName       audit table name
     * @param readCapacityUnits   audit and journal read capacity
     * @param writeCapacityUnits  audit and journal write capacity
     * @param autoCreate           whether missing tables may be created
     * @param localConfiguration   community configuration
     */
    public DynamoDBAuditPersistence(DynamoDbClient client,
                                    TransactionWrapper txWrapper,
                                    DynamoDBJournalEventStore journalEventStore,
                                    JournalEventSequencer journalEventSequencer,
                                    JournalEventSequencerFactory journalEventSequencerFactory,
                                    String auditTableName,
                                    long readCapacityUnits,
                                    long writeCapacityUnits,
                                    boolean autoCreate,
                                    CommunityConfigurable localConfiguration) {
        super(localConfiguration);
        this.client = client;
        this.auditTableName = auditTableName;
        this.readCapacityUnits = readCapacityUnits;
        this.writeCapacityUnits = writeCapacityUnits;
        this.autoCreate = autoCreate;
        this.txWrapper = txWrapper;
        this.journalEventStore = journalEventStore;
        this.journalEventSequencer = journalEventSequencer;
        this.journalEventSequencerFactory = journalEventSequencerFactory;
    }

    /**
     * Creates a persistence with a fixed journal stream sequencer.
     *
     * @param client               DynamoDB client used by the audit and journal stores
     * @param txWrapper            transaction wrapper shared with the target system
     * @param journalEventStore    journal store receiving staged events
     * @param journalEventSequencer sequencer for the persistence stream
     * @param auditTableName       audit table name
     * @param readCapacityUnits   audit and journal read capacity
     * @param writeCapacityUnits  audit and journal write capacity
     * @param autoCreate           whether missing tables may be created
     * @param localConfiguration   community configuration
     */
    public DynamoDBAuditPersistence(DynamoDbClient client,
                                    TransactionWrapper txWrapper,
                                    DynamoDBJournalEventStore journalEventStore,
                                    JournalEventSequencer journalEventSequencer,
                                    String auditTableName,
                                    long readCapacityUnits,
                                    long writeCapacityUnits,
                                    boolean autoCreate,
                                    CommunityConfigurable localConfiguration) {
        this(client, txWrapper, journalEventStore, journalEventSequencer, null, auditTableName,
                readCapacityUnits, writeCapacityUnits, autoCreate, localConfiguration);
    }

    @Override
    protected void doInitialize(RunnerId runnerId) {
        auditor = new DynamoDBAuditor(client);
        auditor.initialize(
                autoCreate,
                auditTableName,
                readCapacityUnits,
                writeCapacityUnits);
        if (journalEventStore != null) {
            FeatureFlag.ifEnabled(Features.JOURNAL_EVENTS, () -> journalEventStore.initialize(autoCreate));
        }
    }

    @Override
    public List<AuditEntry> getAuditHistory() {
        return auditor.getAuditHistory();
    }

    @Override
    public Result writeEntry(AuditEntry auditEntry) {
        if (FeatureFlag.isEnabled(Features.JOURNAL_EVENTS)) {
            if (txWrapper == null || journalEventStore == null || journalEventSequencer == null) {
                throw new IllegalStateException("Journal-enabled persistence requires transaction and journal wiring");
            }
            JournalEventSequencer sequencer = journalEventSequencer;
            RuntimeContext baseContext = new BasicRuntimeContext("write-changeState-" + auditEntry.getChangeId());
            Result result = txWrapper.wrapInTransaction(baseContext, runtimeContext -> {
                TransactWriteItemsEnhancedRequest.Builder builder = runtimeContext.getContext()
                        .getRequiredDependencyValue(TransactWriteItemsEnhancedRequest.Builder.class);
                JournalEvent<AuditEntry> journalEvent = sequencer.newEvent(auditEntry);
                journalEventStore.write(builder, journalEvent);
                auditor.stageWrite(builder, auditEntry);
                return Result.OK();
            });
            sequencer.confirm();
            return result;
        }
        return auditor.writeEntry(auditEntry);
    }

}
