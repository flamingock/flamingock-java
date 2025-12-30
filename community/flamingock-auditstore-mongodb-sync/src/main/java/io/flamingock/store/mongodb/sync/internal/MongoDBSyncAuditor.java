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
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.result.UpdateResult;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditReader;
import io.flamingock.internal.common.mongodb.CollectionInitializator;
import io.flamingock.internal.common.mongodb.MongoDBAuditMapper;
import io.flamingock.internal.core.store.audit.LifecycleAuditWriter;
import io.flamingock.internal.util.Result;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import io.flamingock.targetystem.mongodb.sync.util.MongoDBSyncCollectionHelper;
import io.flamingock.targetystem.mongodb.sync.util.MongoDBSyncDocumentHelper;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import static io.flamingock.internal.util.constants.AuditEntryFieldConstants.KEY_CHANGE_ID;
import static io.flamingock.internal.util.constants.AuditEntryFieldConstants.KEY_EXECUTION_ID;
import static io.flamingock.internal.util.constants.AuditEntryFieldConstants.KEY_STATE;

public class MongoDBSyncAuditor implements LifecycleAuditWriter, AuditReader {

    private static final Logger logger = FlamingockLoggerFactory.getLogger("MongoDBSyncAuditor");

    private final MongoCollection<Document> collection;
    private final MongoDBAuditMapper<MongoDBSyncDocumentHelper> mapper = new MongoDBAuditMapper<>(() -> new MongoDBSyncDocumentHelper(new Document()));

    MongoDBSyncAuditor(MongoDatabase database,
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
        CollectionInitializator<MongoDBSyncDocumentHelper> initializer = new CollectionInitializator<>(
                new MongoDBSyncCollectionHelper(collection),
                () -> new MongoDBSyncDocumentHelper(new Document()),
                new String[]{KEY_EXECUTION_ID, KEY_CHANGE_ID, KEY_STATE}
        );
        if (autoCreate) {
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

        UpdateResult result = collection.replaceOne(filter, entryDocument, new ReplaceOptions().upsert(true));
        logger.debug("SaveOrUpdate[{}] with result" +
                "\n[upsertId:{}, matches: {}, modifies: {}, acknowledged: {}]", auditEntry, result.getUpsertedId(), result.getMatchedCount(), result.getModifiedCount(), result.wasAcknowledged());

        return Result.OK();
    }


    @Override
    public List<AuditEntry> getAuditHistory() {
        return collection.find()
                .into(new LinkedList<>())
                .stream()
                .map(MongoDBSyncDocumentHelper::new)
                .map(mapper::fromDocument)
                .collect(Collectors.toList());
    }
}
