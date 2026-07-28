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
import io.flamingock.internal.common.core.journal.JournalEvent;
import io.flamingock.internal.common.core.transaction.TransactionWrapper;
import io.flamingock.internal.core.configuration.community.CommunityConfigurable;
import io.flamingock.internal.core.context.BasicRuntimeContext;
import io.flamingock.internal.core.external.store.audit.community.AbstractCommunityAuditPersistence;
import io.flamingock.internal.core.journal.JournalEventSequencer;
import io.flamingock.internal.util.Result;
import io.flamingock.internal.util.id.RunnerId;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        journalEventStore.initialize(autoCreate);
    }


    @Override
    public List<AuditEntry> getAuditHistory() {
        return auditRepository.getAuditHistory();
    }

    @Override
    public Result writeEntry(AuditEntry auditEntry) {
        RuntimeContext baseContext = new BasicRuntimeContext("write-changeState-" + auditEntry.getChangeId());
        return txWrapper.wrapInTransaction(baseContext, runtimeContext -> {
            ClientSession clientSession = runtimeContext.getContext().getRequiredDependencyValue(ClientSession.class);
            JournalEvent<AuditEntry> journalEvent = journalEventSequencer.newEvent(auditEntry);
            journalEventStore.write(clientSession, journalEvent);
            return auditRepository.writeEntry(clientSession, auditEntry);
        });

    }

}
