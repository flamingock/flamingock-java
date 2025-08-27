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
package io.flamingock.community.mongodb.sync.internal;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.result.UpdateResult;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.mongodb.CollectionInitializator;
import io.flamingock.internal.common.mongodb.MongoDBAuditMapper;
import io.flamingock.internal.core.community.TransactionManager;
import io.flamingock.internal.common.core.audit.AuditReader;
import io.flamingock.internal.core.engine.audit.ExecutionAuditWriter;
import io.flamingock.internal.core.engine.audit.domain.AuditSnapshotMapBuilder;

import io.flamingock.internal.util.FlamingockLoggerFactory;
import io.flamingock.internal.util.Result;
import io.flamingock.targetystem.mongodb.sync.MongoSyncTargetSystem;
import io.flamingock.targetystem.mongodb.sync.util.MongoSyncCollectionHelper;
import io.flamingock.targetystem.mongodb.sync.util.MongoSyncDocumentHelper;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;

import java.util.LinkedList;
import java.util.Map;
import java.util.stream.Collectors;

import static io.flamingock.internal.common.core.audit.AuditEntryField.KEY_CHANGE_ID;
import static io.flamingock.internal.common.core.audit.AuditEntryField.KEY_EXECUTION_ID;
import static io.flamingock.internal.common.core.audit.AuditEntryField.KEY_STATE;

public class MongoSyncAuditor implements ExecutionAuditWriter, AuditReader {

    private static final Logger logger = FlamingockLoggerFactory.getLogger("MongoSyncAuditor");

    private final MongoCollection<Document> collection;
    private final MongoDBAuditMapper<MongoSyncDocumentHelper> mapper = new MongoDBAuditMapper<>(() -> new MongoSyncDocumentHelper(new Document()));
    private final TransactionManager<ClientSession> txSessionManager;

    MongoSyncAuditor(MongoSyncTargetSystem targetSystem, String collectionName) {
        this.collection = targetSystem.getDatabase().getCollection(collectionName)
                .withReadConcern(targetSystem.getReadConcern())
                .withReadPreference(targetSystem.getReadPreference())
                .withWriteConcern(targetSystem.getWriteConcern());
        this.txSessionManager = targetSystem.getTxManager();
    }

    protected void initialize(boolean indexCreation) {
        CollectionInitializator<MongoSyncDocumentHelper> initializer = new CollectionInitializator<>(
                new MongoSyncCollectionHelper(collection),
                () -> new MongoSyncDocumentHelper(new Document()),
                new String[]{KEY_EXECUTION_ID, KEY_CHANGE_ID, KEY_STATE}
        );
        if (indexCreation) {
            initializer.initialize();
        } else {
            initializer.justValidateCollection();
        }

    }

    @Override
    public Result writeEntry(AuditEntry auditEntry) {
        Bson filter = Filters.and(
                Filters.eq(KEY_EXECUTION_ID, auditEntry.getExecutionId()),
                Filters.eq(KEY_CHANGE_ID, auditEntry.getTaskId()),
                Filters.eq(KEY_STATE, auditEntry.getState().name())
        );

        Document entryDocument = mapper.toDocument(auditEntry).getDocument();

        UpdateResult result = txSessionManager.getSession(auditEntry.getTaskId())
                .map(clientSession -> collection.replaceOne(clientSession, filter, entryDocument, new ReplaceOptions().upsert(true)))
                .orElseGet(() -> collection.replaceOne(filter, entryDocument, new ReplaceOptions().upsert(true)));
        logger.debug("SaveOrUpdate[{}] with result" +
                "\n[upsertId:{}, matches: {}, modifies: {}, acknowledged: {}]", auditEntry, result.getUpsertedId(), result.getMatchedCount(), result.getModifiedCount(), result.wasAcknowledged());

        return Result.OK();
    }


    @Override
    public Map<String, AuditEntry> getAuditSnapshotByChangeId() {
        AuditSnapshotMapBuilder builder = new AuditSnapshotMapBuilder();
        collection.find()
                .into(new LinkedList<>())
                .stream()
                .map(MongoSyncDocumentHelper::new)
                .map(mapper::fromDocument)
                .collect(Collectors.toList())
                .forEach(builder::addEntry);
        return builder.build();
    }
}
