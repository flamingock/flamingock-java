package io.flamingock.cloud.transaction.mongodb.sync.v4;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import io.flamingock.cloud.transaction.mongodb.sync.v4.cofig.MongoDBSync4Configuration;
import io.flamingock.cloud.transaction.mongodb.sync.v4.wrapper.MongoSync4CollectionWrapper;
import io.flamingock.cloud.transaction.mongodb.sync.v4.wrapper.MongoSync4DocumentWrapper;
import io.flamingock.cloud.transaction.mongodb.sync.v4.wrapper.MongoSync4TransactionWrapper;
import io.flamingock.core.cloud.api.transaction.OngoingStatus;
import io.flamingock.core.cloud.transaction.CloudTransactioner;
import io.flamingock.core.driver.TransactionManager;
import io.flamingock.core.runtime.dependency.DependencyInjectable;
import io.flamingock.core.task.descriptor.TaskDescriptor;
import io.flamingock.core.transaction.TransactionWrapper;
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
        this(mongoClient, databaseName, MongoDBSync4Configuration.getDefault());
    }

    @Override
    public void initialize() {
        TransactionManager<ClientSession> sessionManager = new TransactionManager<>(mongoClient::startSession);
        transactionWrapper = new MongoSync4TransactionWrapper(sessionManager);
        onGoingTasksCollection = database.getCollection("flamingockOnGoingTasks")
                .withReadConcern(mongoDBSync4Configuration.getReadWriteConfiguration().getReadConcern())
                .withReadPreference(mongoDBSync4Configuration.getReadWriteConfiguration().getReadPreference())
                .withWriteConcern(mongoDBSync4Configuration.getReadWriteConfiguration().getWriteConcern());

        CollectionInitializator<MongoSync4DocumentWrapper> initializer = new CollectionInitializator<>(
                new MongoSync4CollectionWrapper(onGoingTasksCollection),
                () -> new MongoSync4DocumentWrapper(new Document()),
                new String[]{TASK_ID}
        );
        if (mongoDBSync4Configuration.isIndexCreation()) {
            initializer.initialize();
        } else {
            initializer.justValidateCollection();
        }
    }

    @Override
    public void close() {

    }

    @Override
    public Set<OngoingStatus> getOngoingStatuses() {
        return onGoingTasksCollection.find()
                .map(this::mapToOnGoingStatus)
                .into(new HashSet<>());
    }

    @Override
    public void cleanOngoingStatus(String taskId) {
        onGoingTasksCollection.deleteMany(Filters.eq(TASK_ID, taskId));
    }

    @Override
    public void saveOngoingStatus(OngoingStatus status) {
        Document filter = new Document("taskId", status.getTaskId());

        // Define the new document to replace or insert
        Document newDocument = new Document("taskId", status.getTaskId())
                .append("operation", status.getOperation().name());

        onGoingTasksCollection.updateOne(
                filter,
                new Document("$set", newDocument),
                new com.mongodb.client.model.UpdateOptions().upsert(true));
    }

    @Override
    public <T> T wrapInTransaction(TaskDescriptor taskDescriptor, DependencyInjectable dependencyInjectable, Supplier<T> operation) {
        return transactionWrapper.wrapInTransaction(taskDescriptor, dependencyInjectable, operation);
    }

    private OngoingStatus mapToOnGoingStatus(Document document) {
        OngoingStatus.Operation operation = OngoingStatus.Operation.valueOf(document.getString(OPERATION));
        return new OngoingStatus(document.getString(TASK_ID), operation);
    }
}
