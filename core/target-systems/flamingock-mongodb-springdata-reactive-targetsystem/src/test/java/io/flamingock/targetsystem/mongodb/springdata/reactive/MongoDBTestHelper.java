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

import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import io.flamingock.internal.common.core.targets.TargetSystemAuditMarkType;
import io.flamingock.internal.core.external.targets.mark.TargetSystemAuditMark;
import io.flamingock.internal.core.external.store.audit.domain.AuditContextBundle;
import io.flamingock.internal.common.mongodb.CollectionInitializator;
import io.flamingock.internal.common.mongodb.MongoDBAuditMapper;
import io.flamingock.internal.common.mongodb.MongoDBDocumentHelper;
import io.flamingock.internal.common.mongodb.MongoDBReactiveCollectionHelper;
import io.flamingock.internal.util.constants.CommunityPersistenceConstants;
import org.bson.Document;

import java.util.HashSet;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MongoDBTestHelper {

    public final MongoDatabase mongoDatabase;

    private final MongoDBAuditMapper<MongoDBDocumentHelper> mapper = new MongoDBAuditMapper<>(() -> new MongoDBDocumentHelper(new Document()));

    public MongoDBTestHelper(MongoDatabase mongoDatabase) {
        this.mongoDatabase = mongoDatabase;
    }

    public void insertOngoingExecution(String changeId) {

        MongoCollection<Document> onGoingChangesCollection = mongoDatabase.getCollection(CommunityPersistenceConstants.DEFAULT_MARKER_STORE_NAME);

        CollectionInitializator<MongoDBDocumentHelper> initializer = new CollectionInitializator<>(
                new MongoDBReactiveCollectionHelper(onGoingChangesCollection),
                () -> new MongoDBDocumentHelper(new Document()),
                new String[]{"changeId"}
        );
        initializer.initialize();


        Document filter = new Document("changeId", changeId);

        Document newDocument = new Document("changeId", changeId)
                .append("operation", AuditContextBundle.Operation.EXECUTION.toString());

        ReactiveMongoTestHelper.complete(onGoingChangesCollection.updateOne(
                filter,
                new Document("$set", newDocument),
                new com.mongodb.client.model.UpdateOptions().upsert(true)));

        checkEmptyTargetSystemAudiMarker();
    }

    public <T> void checkCount(MongoCollection<Document> collection, int count) {
        long result = ReactiveMongoTestHelper.collect(collection.find()).stream().count();
        assertEquals(count, (int) result);
    }

    public void checkEmptyTargetSystemAudiMarker() {
        checkOngoingChange(result -> result == 0);
    }

    public void checkOngoingChange(Predicate<Long> predicate) {
        MongoCollection<Document> onGoingChangesCollection = mongoDatabase.getCollection("flamingockOnGoingChanges");

        long result = ReactiveMongoTestHelper.collect(onGoingChangesCollection.find())
                .stream()
                .map(MongoDBTestHelper::mapToOnGoingStatus)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new))
                .size();

        assertTrue(predicate.test(result));
    }

    public static TargetSystemAuditMark mapToOnGoingStatus(Document document) {
        TargetSystemAuditMarkType operation = TargetSystemAuditMarkType.valueOf(document.getString("operation"));
        return new TargetSystemAuditMark(document.getString("changeId"), operation);
    }



}
