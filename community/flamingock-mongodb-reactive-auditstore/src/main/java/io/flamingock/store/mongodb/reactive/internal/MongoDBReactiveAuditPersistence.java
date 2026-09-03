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

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.reactivestreams.client.ClientSession;
import com.mongodb.reactivestreams.client.MongoDatabase;
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

import static io.flamingock.internal.common.mongodb.journal.JournalEventPersistenceConstants.DEFAULT_JOURNAL_STORE_NAME;

public class MongoDBReactiveAuditPersistence extends AbstractCommunityAuditPersistence {

    private final MongoDBReactiveAuditRepository auditRepository;
    private final MongoDBReactiveJournalEventStore journalEventStore;
    private final JournalEventSequencer journalEventSequencer;
    private final TransactionWrapper txWrapper;
    private final boolean autoCreate;

    public MongoDBReactiveAuditPersistence(CommunityConfigurable localConfiguration,
                                           MongoDBReactiveAuditRepository auditRepository,
                                           MongoDBReactiveJournalEventStore journalEventStore,
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

    /**
     * Backward-compatible constructor for callers that only need the historical audit path.
     */
    public MongoDBReactiveAuditPersistence(CommunityConfigurable localConfiguration,
                                         MongoDatabase database,
                                         String auditCollectionName,
                                         ReadConcern readConcern,
                                         ReadPreference readPreference,
                                         WriteConcern writeConcern,
                                         boolean autoCreate) {
        this(
                localConfiguration,
                new MongoDBReactiveAuditRepository(database, auditCollectionName, readConcern, readPreference, writeConcern),
                new MongoDBReactiveJournalEventStore(database, DEFAULT_JOURNAL_STORE_NAME,
                        readConcern, readPreference, writeConcern),
                null,
                null,
                autoCreate);
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
        if (!isJournalEventsEnabled()) {
            return auditRepository.append(auditEntry);
        }

        if (journalEventStore == null || journalEventSequencer == null || txWrapper == null) {
            throw new IllegalStateException("MongoDB reactive journal writes require a transaction wrapper and sequencer");
        }

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

    private static boolean isJournalEventsEnabled() {
        try {
            return FeatureFlag.isEnabled(Features.JOURNAL_EVENTS, false);
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
