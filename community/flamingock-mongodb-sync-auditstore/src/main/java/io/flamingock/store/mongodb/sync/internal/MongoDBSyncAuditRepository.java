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

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.result.UpdateResult;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.mongodb.CollectionInitializator;
import io.flamingock.internal.common.mongodb.MongoDBAuditMapper;
import io.flamingock.internal.common.mongodb.MongoDBSyncCollectionHelper;
import io.flamingock.internal.common.mongodb.MongoDBDocumentHelper;
import io.flamingock.internal.util.Result;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import static io.flamingock.internal.util.constants.AuditEntryFieldConstants.KEY_CHANGE_ID;
import static io.flamingock.internal.util.constants.AuditEntryFieldConstants.KEY_EXECUTION_ID;
import static io.flamingock.internal.util.constants.AuditEntryFieldConstants.KEY_STATE;

public class MongoDBSyncAuditRepository {

    private static final Logger logger = FlamingockLoggerFactory.getLogger("MongoDBSyncAuditor");

    private final MongoCollection<Document> collection;
    private final MongoDBAuditMapper<MongoDBDocumentHelper> mapper = new MongoDBAuditMapper<>(() -> new MongoDBDocumentHelper(new Document()));

    public MongoDBSyncAuditRepository(MongoDatabase database,
                                      String collectionName,
                                      ReadConcern readConcern,
                                      ReadPreference readPreference,
                                      WriteConcern writeConcern) {
        this.collection = database.getCollection(collectionName)
                .withReadConcern(readConcern)
                .withReadPreference(readPreference)
                .withWriteConcern(writeConcern);
    }

    protected void initialize(boolean autoCreate) {
        CollectionInitializator<MongoDBDocumentHelper> initializer = new CollectionInitializator<>(
                new MongoDBSyncCollectionHelper(collection),
                () -> new MongoDBDocumentHelper(new Document()),
                new String[]{KEY_EXECUTION_ID, KEY_CHANGE_ID, KEY_STATE}
        );
        if (autoCreate) {
            initializer.initialize();
        } else {
            initializer.justValidateCollection();
        }

    }

    /**
     * Keeps a single record per change, overwritten on every state transition — the change's current state.
     * <p>
     * The history of how it got there lives in the journal, so this is only correct when journal events are
     * being written; see {@code MongoDBSyncAuditPersistence.writeEntry}.
     * <p>
     * Keyed on {@code changeId} alone, which is safe because {@code LoadedPipeline.validate()} rejects
     * duplicate change ids across all stages. Nothing at the database level enforces one-record-per-change —
     * the existing unique index is on {@code (executionId, changeId, state)}, which is the rule for
     * {@link #append}, not for this. The single-writer guarantee comes from the stage lock.
     */
    Result save(ClientSession clientSession, AuditEntry auditEntry) {
        Bson filter = Filters.eq(KEY_CHANGE_ID, auditEntry.getChangeId());

        Document entryDocument = mapper.toDocument(auditEntry).getDocument();

        UpdateResult result = collection.replaceOne(clientSession, filter, entryDocument, new ReplaceOptions().upsert(true));
        logger.debug("Save changeState[{}] with result" +
                "\n[upsertId:{}, matches: {}, modifies: {}, acknowledged: {}]", auditEntry, result.getUpsertedId(), result.getMatchedCount(), result.getModifiedCount(), result.wasAcknowledged());

        return Result.OK();
    }

    /**
     * Keeps one record per {@code (executionId, changeId, state)} — the append-oriented audit ledger, where a
     * change accumulates a row per state transition and the collection is itself the history.
     * <p>
     * This is the behaviour used when journal events are disabled, and it is what the Mongock importer needs
     * regardless: a legacy changelog can hold several entries for the same change across executions, and
     * {@link #save} would collapse them onto each other, discarding the very history being imported.
     */
    Result append(AuditEntry auditEntry) {
        Bson filter = Filters.and(
                Filters.eq(KEY_EXECUTION_ID, auditEntry.getExecutionId()),
                Filters.eq(KEY_CHANGE_ID, auditEntry.getChangeId()),
                Filters.eq(KEY_STATE, auditEntry.getState().name())
        );

        Document entryDocument = mapper.toDocument(auditEntry).getDocument();

        UpdateResult result = collection.replaceOne(filter, entryDocument, new ReplaceOptions().upsert(true));
        logger.debug("SaveOrUpdate[{}] with result" +
                "\n[upsertId:{}, matches: {}, modifies: {}, acknowledged: {}]", auditEntry, result.getUpsertedId(), result.getMatchedCount(), result.getModifiedCount(), result.wasAcknowledged());

        return Result.OK();
    }

    public List<AuditEntry> getAuditHistory() {
        return collection.find()
                .into(new LinkedList<>())
                .stream()
                .map(MongoDBDocumentHelper::new)
                .map(mapper::fromDocument)
                .collect(Collectors.toList());
    }
}
