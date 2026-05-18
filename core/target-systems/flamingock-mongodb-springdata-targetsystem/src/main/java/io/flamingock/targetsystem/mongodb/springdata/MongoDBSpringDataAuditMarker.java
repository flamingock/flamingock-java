/*
 * Copyright 2025 Flamingock (https://www.flamingock.io)
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
package io.flamingock.targetsystem.mongodb.springdata;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import io.flamingock.internal.common.core.targets.TargetSystemAuditMarkType;
import io.flamingock.internal.common.mongodb.CollectionInitializator;
import io.flamingock.internal.common.mongodb.MongoDBSyncCollectionHelper;
import io.flamingock.internal.common.mongodb.MongoDBSyncDocumentHelper;
import io.flamingock.internal.core.external.targets.mark.TargetSystemAuditMark;
import io.flamingock.internal.core.external.targets.mark.TargetSystemAuditMarker;
import io.flamingock.internal.util.constants.CommunityPersistenceConstants;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.HashSet;
import java.util.Set;

/**
 * Audit marker for the MongoDB Spring Data target system.
 * <p>
 * Writes participate in the active Spring-managed MongoDB transaction by going through
 * {@link MongoTemplate#getDb()}, which returns a session-aware {@link MongoDatabase} when
 * called inside an active {@code MongoTransactionManager} transaction. This avoids exposing
 * the underlying {@code ClientSession} through the Flamingock {@code TransactionManager},
 * keeping the Spring abstraction intact.
 */
public class MongoDBSpringDataAuditMarker implements TargetSystemAuditMarker {

    public static final String OPERATION = "operation";
    private static final String CHANGE_ID = "changeId";

    private final MongoTemplate mongoTemplate;
    private final String collectionName;

    public MongoDBSpringDataAuditMarker(MongoTemplate mongoTemplate, String collectionName) {
        this.mongoTemplate = mongoTemplate;
        this.collectionName = collectionName;
    }

    public static Builder builder(MongoTemplate mongoTemplate) {
        return new Builder(mongoTemplate);
    }

    public static TargetSystemAuditMark mapToOnGoingStatus(Document document) {
        TargetSystemAuditMarkType operation = TargetSystemAuditMarkType.valueOf(document.getString(OPERATION));
        return new TargetSystemAuditMark(document.getString(CHANGE_ID), operation);
    }

    @Override
    public Set<TargetSystemAuditMark> listAll() {
        return collection().find()
                .map(MongoDBSpringDataAuditMarker::mapToOnGoingStatus)
                .into(new HashSet<>());
    }

    @Override
    public void clearMark(String changeId) {
        collection().deleteMany(Filters.eq(CHANGE_ID, changeId));
    }

    @Override
    public void mark(TargetSystemAuditMark auditMark) {
        Document filter = new Document(CHANGE_ID, auditMark.getChangeId());
        Document newDocument = new Document(CHANGE_ID, auditMark.getChangeId())
                .append(OPERATION, auditMark.getOperation().name());

        collection().updateOne(
                filter,
                new Document("$set", newDocument),
                new UpdateOptions().upsert(true));
    }

    private MongoCollection<Document> collection() {
        return mongoTemplate.getDb().getCollection(collectionName);
    }

    public static class Builder {
        private final MongoTemplate mongoTemplate;
        private boolean autoCreate = true;
        private String collectionName = CommunityPersistenceConstants.DEFAULT_MARKER_STORE_NAME;

        public Builder(MongoTemplate mongoTemplate) {
            this.mongoTemplate = mongoTemplate;
        }

        public Builder setCollectionName(String collectionName) {
            this.collectionName = collectionName;
            return this;
        }

        public Builder withAutoCreate(boolean autoCreate) {
            this.autoCreate = autoCreate;
            return this;
        }

        public MongoDBSpringDataAuditMarker build() {
            MongoDatabase db = mongoTemplate.getDb();
            MongoCollection<Document> collection = db.getCollection(collectionName);
            CollectionInitializator<MongoDBSyncDocumentHelper> initializer = new CollectionInitializator<>(
                    new MongoDBSyncCollectionHelper(collection),
                    () -> new MongoDBSyncDocumentHelper(new Document()),
                    new String[]{CHANGE_ID}
            );
            if (autoCreate) {
                initializer.initialize();
            } else {
                initializer.justValidateCollection();
            }
            return new MongoDBSpringDataAuditMarker(mongoTemplate, collectionName);
        }
    }
}
