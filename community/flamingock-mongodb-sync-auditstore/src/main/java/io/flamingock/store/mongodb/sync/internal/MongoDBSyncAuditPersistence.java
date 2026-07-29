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
import io.flamingock.internal.common.core.transaction.TransactionWrapper;
import io.flamingock.internal.core.configuration.community.CommunityConfigurable;
import io.flamingock.internal.core.context.BasicRuntimeContext;
import io.flamingock.internal.core.external.store.audit.community.AbstractCommunityAuditPersistence;
import io.flamingock.internal.core.journal.JournalEventSequencer;
import io.flamingock.internal.util.FeatureFlag;
import io.flamingock.internal.util.Result;
import io.flamingock.internal.util.id.RunnerId;

import java.util.List;

public class MongoDBSyncAuditPersistence extends AbstractCommunityAuditPersistence {

    private final MongoDBSyncAuditRepository auditRepository;
    private final MongoDBSyncJournalEventStore journalEventStore;
    private final JournalEventSequencer journalEventSequencer;
    private final TransactionWrapper txWrapper;
    private final boolean autoCreate;


    public MongoDBSyncAuditPersistence(CommunityConfigurable localConfiguration,
                                       MongoDBSyncAuditRepository auditRepository,
                                       MongoDBSyncJournalEventStore journalEventStore,
                                       JournalEventSequencer journalEventSequencer,
                                       TransactionWrapper txWrapper,
                                       boolean autoCreate) {
        super(localConfiguration);
        this.auditRepository = auditRepository;
        this.journalEventStore = journalEventStore;
        this.journalEventSequencer = journalEventSequencer;
        this.txWrapper = txWrapper;
        this.autoCreate = autoCreate;
    }

    @Override
    protected void doInitialize(RunnerId runnerId) {
        auditRepository.initialize(autoCreate);
        // Creating the indexes is what brings the journal collection into existence — there is no explicit
        // createCollection call — so skipping this keeps it from ever appearing. It must stay in step with the
        // append in writeEntry: skipping setup while still appending would let insertOne create the collection
        // implicitly and without indexes, voiding the unique (streamId, streamSequence) and eventId guarantees.
        FeatureFlag.ifEnabled(Features.JOURNAL_EVENTS, () -> journalEventStore.initialize(autoCreate));
    }


    @Override
    public List<AuditEntry> getAuditHistory() {
        return auditRepository.getAuditHistory();
    }

    @Override
    public Result writeEntry(AuditEntry auditEntry) {
        RuntimeContext baseContext = new BasicRuntimeContext("write-changeState-" + auditEntry.getChangeId());
        // The transaction is kept unconditionally, even when the journal is disabled. It exists for the
        // journal — the audit entry and its event must be atomic — and the audit write on its own is a single
        // upsert that needs no transaction. Consequence: this store requires a replica set (or mongos) either
        // way, whereas before the journal an unsessioned replaceOne also worked on a standalone mongod.
        return txWrapper.wrapInTransaction(baseContext, runtimeContext -> {
            ClientSession clientSession = runtimeContext.getContext().getRequiredDependencyValue(ClientSession.class);
            // Read once rather than per branch: the journal append and the audit write shape are two halves of
            // one model. With events, the audit record is the change's current state and the journal is the
            // history; without them, the audit record set is itself the history.
            if (FeatureFlag.isEnabled(Features.JOURNAL_EVENTS)) {
                JournalEvent<AuditEntry> journalEvent = journalEventSequencer.newEvent(auditEntry);
                journalEventStore.write(clientSession, journalEvent);
                return auditRepository.save(clientSession, auditEntry);
            }
            return auditRepository.saveAsHistory(clientSession, auditEntry);
        });

    }

}
