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
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.reactivestreams.client.ClientSession;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.mongodb.CollectionInitializator;
import io.flamingock.internal.common.mongodb.MongoDBAuditMapper;
import io.flamingock.internal.common.mongodb.MongoDBDocumentHelper;
import io.flamingock.internal.common.mongodb.MongoDBReactiveCollectionHelper;
import io.flamingock.internal.util.Result;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import io.flamingock.reactive.util.PublisherSync;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;

import java.util.List;
import java.util.stream.Collectors;

import static io.flamingock.internal.util.constants.AuditEntryFieldConstants.KEY_CHANGE_ID;
import static io.flamingock.internal.util.constants.AuditEntryFieldConstants.KEY_EXECUTION_ID;
import static io.flamingock.internal.util.constants.AuditEntryFieldConstants.KEY_STATE;

/**
 * Native MongoDB Reactive Streams implementation of the audit repository.
 *
 * <p>The repository exposes two write shapes because the journal feature has two deliberately different
 * persistence models. The legacy append path keeps one document per {@code (executionId, changeId, state)};
 * the journal path keeps one current-state document per {@code changeId} and joins the caller's transaction.
 */
public class MongoDBReactiveAuditRepository {

    private static final Logger logger = FlamingockLoggerFactory.getLogger("MongoDBReactiveAuditRepository");

    private final MongoCollection<Document> collection;
    private final CollectionInitializator<MongoDBDocumentHelper> initializer;
    private final MongoDBAuditMapper<MongoDBDocumentHelper> mapper =
            new MongoDBAuditMapper<>(() -> new MongoDBDocumentHelper(new Document()));
    private boolean initialized;

    public MongoDBReactiveAuditRepository(MongoDatabase database,
                                          String collectionName,
                                          ReadConcern readConcern,
                                          ReadPreference readPreference,
                                          WriteConcern writeConcern) {
        this.collection = database.getCollection(collectionName)
                .withReadConcern(readConcern)
                .withReadPreference(readPreference)
                .withWriteConcern(writeConcern);
        this.initializer = new CollectionInitializator<>(
                new MongoDBReactiveCollectionHelper(collection),
                () -> new MongoDBDocumentHelper(new Document()),
                new String[]{KEY_EXECUTION_ID, KEY_CHANGE_ID, KEY_STATE});
    }

    public synchronized void initialize(boolean autoCreate) {
        if (initialized) {
            return;
        }
        if (autoCreate) {
            initializer.initialize();
        } else {
            initializer.justValidateCollection();
        }
        initialized = true;
    }

    /**
     * Saves the current state of a change in a caller-owned MongoDB transaction.
     *
     * @param clientSession session owning the transaction
     * @param auditEntry    current change state
     * @return successful write result; driver failures are propagated
     */
    Result save(ClientSession clientSession, AuditEntry auditEntry) {
        Bson filter = Filters.eq(KEY_CHANGE_ID, auditEntry.getChangeId());
        Document entryDocument = mapper.toDocument(auditEntry).getDocument();

        UpdateResult result = PublisherSync.first(
                collection.replaceOne(clientSession, filter, entryDocument, new ReplaceOptions().upsert(true)));
        logger.debug("Save changeState[{}] with result"
                        + "\n[upsertId:{}, matches: {}, modifies: {}, acknowledged: {}]",
                auditEntry, result.getUpsertedId(), result.getMatchedCount(), result.getModifiedCount(),
                result.wasAcknowledged());
        return Result.OK();
    }

    /**
     * Keeps the historical one-document-per-state behavior used while journal events are disabled.
     *
     * @param auditEntry entry to append or replace
     * @return successful write result; driver failures are propagated
     */
    Result append(AuditEntry auditEntry) {
        Bson filter = Filters.and(
                Filters.eq(KEY_EXECUTION_ID, auditEntry.getExecutionId()),
                Filters.eq(KEY_CHANGE_ID, auditEntry.getChangeId()),
                Filters.eq(KEY_STATE, auditEntry.getState().name())
        );
        Document entryDocument = mapper.toDocument(auditEntry).getDocument();

        UpdateResult result = PublisherSync.first(
                collection.replaceOne(filter, entryDocument, new ReplaceOptions().upsert(true)));
        logger.debug("SaveOrUpdate[{}] with result"
                        + "\n[upsertId:{}, matches: {}, modifies: {}, acknowledged: {}]",
                auditEntry, result.getUpsertedId(), result.getMatchedCount(), result.getModifiedCount(),
                result.wasAcknowledged());
        return Result.OK();
    }

    public List<AuditEntry> getAuditHistory() {
        return PublisherSync.collect(collection.find())
                .stream()
                .map(MongoDBDocumentHelper::new)
                .map(mapper::fromDocument)
                .collect(Collectors.toList());
    }
}
