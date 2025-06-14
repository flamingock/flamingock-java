/*
 * Copyright 2023 Flamingock ("https://oss.flamingock.io")
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.flamingock.cloud.transaction.mongodb.sync;

import com.mongodb.ReadConcern;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import io.flamingock.cloud.transaction.mongodb.sync.config.MongoDBSync4Configuration;
import io.flamingock.cloud.transaction.mongodb.sync.wrapper.MongoSync4CollectionWrapper;
import io.flamingock.cloud.transaction.mongodb.sync.wrapper.MongoSync4DocumentWrapper;
import io.flamingock.cloud.transaction.mongodb.sync.wrapper.MongoSync4TransactionWrapper;
import io.flamingock.core.cloud.api.vo.OngoingStatus;
import io.flamingock.internal.core.cloud.transaction.TaskWithOngoingStatus;
import io.flamingock.internal.core.cloud.transaction.CloudTransactioner;
import io.flamingock.internal.core.community.TransactionManager;
import io.flamingock.core.context.DependencyInjectable;
import io.flamingock.core.task.TaskDescriptor;
import io.flamingock.internal.core.transaction.TransactionWrapper;
import io.flamingock.oss.driver.common.mongodb.CollectionInitializator;
import org.bson.Document;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;


public class MongoSync4CloudTransactioner implements CloudTransactioner {
    public static final String OPERATION = "operation";
    private static final String TASK_ID = "taskId";
    private final MongoClient mongoClient;
    private final MongoDatabase database;
    private final MongoDBSync4Configuration mongoDBSync4Configuration;


    private TransactionWrapper transactionWrapper;
    private MongoCollection<Document> onGoingTasksCollection;

    public MongoSync4CloudTransactioner(MongoClient mongoClient,
                                        String databaseName,
                                        MongoDBSync4Configuration mongoDBSync4Configuration) {
        this.mongoClient = mongoClient;
        this.database = mongoClient.getDatabase(databaseName);
        this.mongoDBSync4Configuration = mongoDBSync4Configuration;
    }

    public MongoSync4CloudTransactioner(MongoClient mongoClient,
                                        String databaseName) {
        this(mongoClient, databaseName, new MongoDBSync4Configuration());
    }

    @Override
    public void initialize() {
        TransactionManager<ClientSession> sessionManager = new TransactionManager<>(mongoClient::startSession);
        transactionWrapper = new MongoSync4TransactionWrapper(sessionManager);
        onGoingTasksCollection = database.getCollection("flamingockOnGoingTasks")
                .withReadConcern(new ReadConcern(mongoDBSync4Configuration.getReadConcern()))
                .withReadPreference(mongoDBSync4Configuration.getReadPreference().getValue())
                .withWriteConcern(mongoDBSync4Configuration.getBuiltMongoDBWriteConcern());

        CollectionInitializator<MongoSync4DocumentWrapper> initializer = new CollectionInitializator<>(
                new MongoSync4CollectionWrapper(onGoingTasksCollection),
                () -> new MongoSync4DocumentWrapper(new Document()),
                new String[]{TASK_ID}
        );
        if (mongoDBSync4Configuration.isAutoCreate()) {
            initializer.initialize();
        } else {
            initializer.justValidateCollection();
        }
    }

    @Override
    public void close() {

    }

    @Override
    public Set<TaskWithOngoingStatus> getOngoingStatuses() {
        return onGoingTasksCollection.find()
                .map(MongoSync4CloudTransactioner::mapToOnGoingStatus)
                .into(new HashSet<>());
    }

    @Override
    public void cleanOngoingStatus(String taskId) {
        onGoingTasksCollection.deleteMany(Filters.eq(TASK_ID, taskId));
    }

    @Override
    public void saveOngoingStatus(TaskWithOngoingStatus status) {
        Document filter = new Document(TASK_ID, status.getTaskId());

        // Define the new document to replace or insert
        Document newDocument = new Document(TASK_ID, status.getTaskId())
                .append(OPERATION, status.getOperation().name());

        onGoingTasksCollection.updateOne(
                filter,
                new Document("$set", newDocument),
                new com.mongodb.client.model.UpdateOptions().upsert(true));
    }

    @Override
    public <T> T wrapInTransaction(TaskDescriptor loadedTask, DependencyInjectable dependencyInjectable, Supplier<T> operation) {
        return transactionWrapper.wrapInTransaction(loadedTask, dependencyInjectable, operation);
    }

    public static TaskWithOngoingStatus mapToOnGoingStatus(Document document) {
        OngoingStatus operation = OngoingStatus.valueOf(document.getString(OPERATION));
        return new TaskWithOngoingStatus(document.getString(TASK_ID), operation);
    }
}
