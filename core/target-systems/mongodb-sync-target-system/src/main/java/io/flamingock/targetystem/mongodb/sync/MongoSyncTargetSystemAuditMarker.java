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
package io.flamingock.targetystem.mongodb.sync;

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import io.flamingock.internal.core.transaction.TransactionManager;
import io.flamingock.targetystem.mongodb.sync.util.MongoSyncCollectionHelper;
import io.flamingock.targetystem.mongodb.sync.util.MongoSyncDocumentHelper;
import io.flamingock.internal.common.cloud.vo.TargetSystemAuditMarkType;
import io.flamingock.internal.common.mongodb.CollectionInitializator;
import io.flamingock.internal.core.targets.mark.TargetSystemAuditMark;
import io.flamingock.internal.core.targets.mark.TargetSystemAuditMarker;
import org.bson.Document;

import java.util.HashSet;
import java.util.Set;


public class MongoSyncTargetSystemAuditMarker implements TargetSystemAuditMarker {
    public static final String OPERATION = "operation";
    private static final String TASK_ID = "taskId";
    private final MongoCollection<Document> onGoingTaskStatusCollection;
    private final TransactionManager<ClientSession> txManager;

    public static Builder builder(MongoDatabase mongoDatabase, TransactionManager<ClientSession> txManager) {
        return new Builder(mongoDatabase, txManager);
    }

    public MongoSyncTargetSystemAuditMarker(MongoCollection<Document> onGoingTaskStatusCollection,
                                            TransactionManager<ClientSession> txManager) {
        this.onGoingTaskStatusCollection = onGoingTaskStatusCollection;
        this.txManager = txManager;
    }

    @Override
    public Set<TargetSystemAuditMark> listAll() {
        return onGoingTaskStatusCollection.find()
                .map(MongoSyncTargetSystemAuditMarker::mapToOnGoingStatus)
                .into(new HashSet<>());
    }

    @Override
    public void clearMark(String changeId) {
        onGoingTaskStatusCollection.deleteMany(Filters.eq(TASK_ID, changeId));
    }

    @Override
    public void mark(TargetSystemAuditMark auditMark) {

        Document filter = new Document(TASK_ID, auditMark.getTaskId());

        // Define the new document to replace or insert
        Document newDocument = new Document(TASK_ID, auditMark.getTaskId())
                .append(OPERATION, auditMark.getOperation().name());

        ClientSession clientSession = txManager.getSessionOrThrow(auditMark.getTaskId());
        onGoingTaskStatusCollection.updateOne(
                clientSession,
                filter,
                new Document("$set", newDocument),
                new com.mongodb.client.model.UpdateOptions().upsert(true));
    }

    public static TargetSystemAuditMark mapToOnGoingStatus(Document document) {
        TargetSystemAuditMarkType operation = TargetSystemAuditMarkType.valueOf(document.getString(OPERATION));
        return new TargetSystemAuditMark(document.getString(TASK_ID), operation);
    }


    public static class Builder {

        private final MongoDatabase mongoDatabase;
        private final TransactionManager<ClientSession> txManager;
        private boolean autoCreate = true;
        private String collectionName = "flamingockOnGoingTasks";
        private ReadConcern readConcern;
        private ReadPreference readPreference;
        private WriteConcern writeConcern;

        public Builder(MongoDatabase mongoDatabase, TransactionManager<ClientSession> txManager) {
            this.mongoDatabase = mongoDatabase;
            this.txManager = txManager;
        }

        public Builder setCollectionName(String collectionName) {
            this.collectionName = collectionName;
            return this;
        }

        public Builder withAutoCreate(boolean autoCreate) {
            this.autoCreate = autoCreate;
            return this;
        }

        public Builder withReadConcern(ReadConcern readConcern) {
            this.readConcern = readConcern;
            return this;
        }

        public Builder withReadPreference(ReadPreference readPreference) {
            this.readPreference = readPreference;
            return this;
        }

        public Builder withWriteConcern(WriteConcern writeConcern) {
            this.writeConcern = writeConcern;
            return this;
        }

        public MongoSyncTargetSystemAuditMarker build() {
            MongoCollection<Document> collection = mongoDatabase.getCollection(collectionName)
//                    .withReadConcern(readConcern)
//                    .withReadPreference(readPreference)
//                    .withWriteConcern(writeConcern)
                    ;

            CollectionInitializator<MongoSyncDocumentHelper> initializer = new CollectionInitializator<>(
                    new MongoSyncCollectionHelper(collection),
                    () -> new MongoSyncDocumentHelper(new Document()),
                    new String[]{TASK_ID}
            );
            if (autoCreate) {
                initializer.initialize();
            } else {
                initializer.justValidateCollection();
            }
            return new MongoSyncTargetSystemAuditMarker(collection, txManager);
        }
    }
}
