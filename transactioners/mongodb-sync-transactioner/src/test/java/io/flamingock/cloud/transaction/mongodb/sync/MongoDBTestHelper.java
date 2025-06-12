/*
 * Copyright 2023 Flamingock (https://oss.flamingock.io)
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

package io.flamingock.cloud.transaction.mongodb.sync;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import io.flamingock.cloud.transaction.mongodb.sync.wrapper.MongoSync4CollectionWrapper;
import io.flamingock.cloud.transaction.mongodb.sync.wrapper.MongoSync4DocumentWrapper;
import io.flamingock.internal.core.engine.audit.domain.AuditItem;
import io.flamingock.oss.driver.common.mongodb.CollectionInitializator;
import io.flamingock.oss.driver.common.mongodb.MongoDBAuditMapper;
import org.bson.Document;

import java.util.HashSet;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MongoDBTestHelper {

    public final MongoDatabase mongoDatabase;

    private final MongoDBAuditMapper<MongoSync4DocumentWrapper> mapper = new MongoDBAuditMapper<>(() -> new MongoSync4DocumentWrapper(new Document()));

    public MongoDBTestHelper(MongoDatabase mongoDatabase) {
        this.mongoDatabase = mongoDatabase;
    }

    public void insertOngoingExecution(String taskId) {

        MongoCollection<Document> onGoingTasksCollection = mongoDatabase.getCollection("flamingockOnGoingTasks");

        CollectionInitializator<MongoSync4DocumentWrapper> initializer = new CollectionInitializator<>(
                new MongoSync4CollectionWrapper(onGoingTasksCollection),
                () -> new MongoSync4DocumentWrapper(new Document()),
                new String[]{"taskId"}
        );
        initializer.initialize();


        Document filter = new Document("taskId", taskId);

        Document newDocument = new Document("taskId", taskId)
                .append("operation", AuditItem.Operation.EXECUTION.toString());

        onGoingTasksCollection.updateOne(
                filter,
                new Document("$set", newDocument),
                new com.mongodb.client.model.UpdateOptions().upsert(true));

        checkAtLeastOneOngoingTask();
    }

    public <T> void checkCount(MongoCollection<Document> collection, int count) {
        long result = collection
                .find()
                .into(new HashSet<>())
                .size();
        assertEquals(count, (int) result);
    }

    public void checkAtLeastOneOngoingTask() {
        checkOngoingTask(result -> result >= 1);
    }

    public void checkOngoingTask(Predicate<Long> predicate) {
        MongoCollection<Document> onGoingTasksCollection = mongoDatabase.getCollection("flamingockOnGoingTasks");

        long result = onGoingTasksCollection.find()
                .map(MongoSync4CloudTransactioner::mapToOnGoingStatus)
                .into(new HashSet<>())
                .size();

        assertTrue(predicate.test(result));
    }



}
