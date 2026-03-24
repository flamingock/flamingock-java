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
package io.flamingock.targetsystem.mongodb.springdata;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import io.flamingock.internal.common.cloud.vo.TargetSystemAuditMarkType;
import io.flamingock.internal.core.external.targets.mark.TargetSystemAuditMark;
import io.flamingock.internal.core.external.store.audit.domain.AuditContextBundle;
import io.flamingock.internal.common.mongodb.CollectionInitializator;
import io.flamingock.internal.common.mongodb.MongoDBAuditMapper;
import io.flamingock.internal.util.constants.CommunityPersistenceConstants;
import io.flamingock.targetsystem.mongodb.springdata.util.MongoDBSyncCollectionHelper;
import io.flamingock.targetsystem.mongodb.springdata.util.MongoDBSyncDocumentHelper;
import org.bson.Document;

import java.util.HashSet;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MongoDBTestHelper {

    public final MongoDatabase mongoDatabase;

    private final MongoDBAuditMapper<MongoDBSyncDocumentHelper> mapper = new MongoDBAuditMapper<>(() -> new MongoDBSyncDocumentHelper(new Document()));

    public MongoDBTestHelper(MongoDatabase mongoDatabase) {
        this.mongoDatabase = mongoDatabase;
    }

    public void insertOngoingExecution(String taskId) {

        MongoCollection<Document> onGoingTasksCollection = mongoDatabase.getCollection(CommunityPersistenceConstants.DEFAULT_MARKER_STORE_NAME);

        CollectionInitializator<MongoDBSyncDocumentHelper> initializer = new CollectionInitializator<>(
                new MongoDBSyncCollectionHelper(onGoingTasksCollection),
                () -> new MongoDBSyncDocumentHelper(new Document()),
                new String[]{"taskId"}
        );
        initializer.initialize();


        Document filter = new Document("taskId", taskId);

        Document newDocument = new Document("taskId", taskId)
                .append("operation", AuditContextBundle.Operation.EXECUTION.toString());

        onGoingTasksCollection.updateOne(
                filter,
                new Document("$set", newDocument),
                new com.mongodb.client.model.UpdateOptions().upsert(true));

        checkEmptyTargetSystemAudiMarker();
    }

    public <T> void checkCount(MongoCollection<Document> collection, int count) {
        long result = collection
                .find()
                .into(new HashSet<>())
                .size();
        assertEquals(count, (int) result);
    }

    public void checkEmptyTargetSystemAudiMarker() {
        checkOngoingTask(result -> result == 0);
    }

    public void checkOngoingTask(Predicate<Long> predicate) {
        MongoCollection<Document> onGoingTasksCollection = mongoDatabase.getCollection("flamingockOnGoingTasks");

        long result = onGoingTasksCollection.find()
                .map(MongoDBTestHelper::mapToOnGoingStatus)
                .into(new HashSet<>())
                .size();

        assertTrue(predicate.test(result));
    }

    public static TargetSystemAuditMark mapToOnGoingStatus(Document document) {
        TargetSystemAuditMarkType operation = TargetSystemAuditMarkType.valueOf(document.getString("operation"));
        return new TargetSystemAuditMark(document.getString("taskId"), operation);
    }



}
