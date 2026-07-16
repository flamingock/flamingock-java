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
package io.flamingock.targetsystem.mongodb.springdata.reactive;

import io.flamingock.internal.common.core.targets.TargetSystemAuditMarkType;
import io.flamingock.internal.core.external.targets.mark.TargetSystemAuditMark;
import io.flamingock.internal.core.external.targets.mark.TargetSystemAuditMarker;
import io.flamingock.internal.util.constants.CommunityPersistenceConstants;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * Audit marker for the MongoDB Spring Data reactive target system.
 * <p>
 * Writes participate in the active Spring-managed MongoDB transaction by going through
 * {@link ReactiveMongoTemplate}. This keeps the Spring reactive abstraction intact and
 * avoids exposing the underlying reactive {@code ClientSession} through the Flamingock
 * {@code TransactionManager}.
 */
public class MongoDBSpringDataReactiveAuditMarker implements TargetSystemAuditMarker {

    public static final String OPERATION = "operation";
    private static final String CHANGE_ID = "changeId";

    private final ReactiveMongoTemplate mongoTemplate;
    private final String collectionName;

    public MongoDBSpringDataReactiveAuditMarker(ReactiveMongoTemplate mongoTemplate, String collectionName) {
        this.mongoTemplate = mongoTemplate;
        this.collectionName = collectionName;
    }

    public static Builder builder(ReactiveMongoTemplate mongoTemplate) {
        return new Builder(mongoTemplate);
    }

    public static TargetSystemAuditMark mapToOnGoingStatus(Document document) {
        TargetSystemAuditMarkType operation = TargetSystemAuditMarkType.valueOf(document.getString(OPERATION));
        return new TargetSystemAuditMark(document.getString(CHANGE_ID), operation);
    }

    @Override
    public Set<TargetSystemAuditMark> listAll() {
        return mongoTemplate.findAll(Document.class, collectionName)
                .map(MongoDBSpringDataReactiveAuditMarker::mapToOnGoingStatus)
                .collectList()
                .block()
                .stream()
                .collect(Collectors.toSet());
    }

    @Override
    public void clearMark(String changeId) {
        mongoTemplate.remove(Query.query(where(CHANGE_ID).is(changeId)), collectionName).block();
    }

    @Override
    public void mark(TargetSystemAuditMark auditMark) {
        Query query = Query.query(where(CHANGE_ID).is(auditMark.getChangeId()));
        Update update = new Update()
                .set(CHANGE_ID, auditMark.getChangeId())
                .set(OPERATION, auditMark.getOperation().name());
        mongoTemplate.upsert(query, update, collectionName).block();
    }

    public static class Builder {

        private final ReactiveMongoTemplate mongoTemplate;
        private boolean autoCreate = true;
        private String collectionName = CommunityPersistenceConstants.DEFAULT_MARKER_STORE_NAME;

        public Builder(ReactiveMongoTemplate mongoTemplate) {
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

        public MongoDBSpringDataReactiveAuditMarker build() {
            List<IndexInfo> indexes = mongoTemplate.indexOps(collectionName).getIndexInfo().collectList().block();
            boolean validIndex = indexes.stream()
                    .anyMatch(index -> index.isUnique() && index.isIndexForFields(java.util.Collections.singleton(CHANGE_ID)));
            if (!validIndex && !autoCreate) {
                throw new IllegalStateException(
                        "Index creation not allowed, but not created or wrongly created for collection " + collectionName);
            }
            if (!validIndex) {
                mongoTemplate.indexOps(collectionName)
                        .ensureIndex(new Index().on(CHANGE_ID, Sort.Direction.ASC).unique())
                        .block();
            }
            return new MongoDBSpringDataReactiveAuditMarker(mongoTemplate, collectionName);
        }
    }
}
