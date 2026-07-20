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
package io.flamingock.targetsystem.mongodb.reactive;

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.reactivestreams.client.ClientSession;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import io.flamingock.internal.common.core.targets.TargetSystemAuditMarkType;
import io.flamingock.internal.common.mongodb.CollectionInitializator;
import io.flamingock.internal.common.mongodb.MongoDBReactiveCollectionHelper;
import io.flamingock.internal.common.mongodb.MongoDBDocumentHelper;
import io.flamingock.internal.core.external.targets.mark.TargetSystemAuditMark;
import io.flamingock.internal.core.external.targets.mark.TargetSystemAuditMarker;
import io.flamingock.internal.core.transaction.TransactionManager;
import io.flamingock.internal.util.constants.CommunityPersistenceConstants;
import io.flamingock.reactive.util.PublisherSync;
import org.bson.Document;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;


public class MongoDBReactiveAuditMarker implements TargetSystemAuditMarker {
    public static final String OPERATION = "operation";
    private static final String CHANGE_ID = "changeId";
    private final MongoCollection<Document> auditMarkerCollection;
    private final TransactionManager<ClientSession> txManager;

    public MongoDBReactiveAuditMarker(MongoCollection<Document> auditMarkerCollection,
                                      TransactionManager<ClientSession> txManager) {
        this.auditMarkerCollection = auditMarkerCollection;
        this.txManager = txManager;
    }

    public static Builder builder(MongoDatabase mongoDatabase, TransactionManager<ClientSession> txManager) {
        return new Builder(mongoDatabase, txManager);
    }

    public static TargetSystemAuditMark mapToOnGoingStatus(Document document) {
        TargetSystemAuditMarkType operation = TargetSystemAuditMarkType.valueOf(document.getString(OPERATION));
        return new TargetSystemAuditMark(document.getString(CHANGE_ID), operation);
    }

    @Override
    public Set<TargetSystemAuditMark> listAll() {
        return PublisherSync.collect(auditMarkerCollection.find())
                .stream()
                .map(MongoDBReactiveAuditMarker::mapToOnGoingStatus)
                .collect(Collectors.toCollection(HashSet::new));
    }

    @Override
    public void clearMark(String changeId) {
        PublisherSync.first(auditMarkerCollection.deleteMany(Filters.eq(CHANGE_ID, changeId)));
    }

    @Override
    public void mark(TargetSystemAuditMark auditMark) {

        Document filter = new Document(CHANGE_ID, auditMark.getChangeId());

        // Define the new document to replace or insert
        Document newDocument = new Document(CHANGE_ID, auditMark.getChangeId())
                .append(OPERATION, auditMark.getOperation().name());

        ClientSession clientSession = txManager.getSessionOrThrow(auditMark.getChangeId());
        PublisherSync.first(auditMarkerCollection.updateOne(
                clientSession,
                filter,
                new Document("$set", newDocument),
                new UpdateOptions().upsert(true)));
    }

    public static class Builder {

        private final MongoDatabase mongoDatabase;
        private final TransactionManager<ClientSession> txManager;
        private boolean autoCreate = true;
        private String collectionName = CommunityPersistenceConstants.DEFAULT_MARKER_STORE_NAME;
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

        public MongoDBReactiveAuditMarker build() {
            MongoCollection<Document> collection = mongoDatabase.getCollection(collectionName)
//                    .withReadConcern(readConcern)
//                    .withReadPreference(readPreference)
//                    .withWriteConcern(writeConcern)
                    ;

            CollectionInitializator<MongoDBDocumentHelper> initializer = new CollectionInitializator<>(
                    new MongoDBReactiveCollectionHelper(collection),
                    () -> new MongoDBDocumentHelper(new Document()),
                    new String[]{CHANGE_ID}
            );
            if (autoCreate) {
                initializer.initialize();
            } else {
                initializer.justValidateCollection();
            }
            return new MongoDBReactiveAuditMarker(collection, txManager);
        }
    }
}
