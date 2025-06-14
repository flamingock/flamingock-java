package io.flamingock.importer.adapter;

import io.flamingock.core.error.FlamingockException;
import io.flamingock.importer.ImporterAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ImporterAdapterFactory {

    private static final Logger logger = LoggerFactory.getLogger("ImporterAdapterFactory");

    private static final String MONGO_ADAPTER_CLASS = "io.flamingock.importer.adapter.mongodb.MongoDbImporterAdapter";
    private static final String DYNAMO_ADAPTER_CLASS = "io.flamingock.importer.adapter.dynamodb.DynamoDbImporterAdapter";
    private static final String COUCHBASE_ADAPTER_CLASS = "io.flamingock.importer.adapter.couchbase.CouchbaseImporterAdapter";

    private ImporterAdapterFactory() {}

    public static ImporterAdapter getImporterAdapter() {
        String className = getClassName();
        try {
            Class<?> adapterClass = Class.forName(className);
            return (ImporterAdapter) adapterClass.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Importer adapter implementation not found: " + className, e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate importer adapter: " + className, e);
        }
    }

    @NotNull
    private static String getClassName() {
        String className;

        if (isMongoDbAdapter()) {
            className = MONGO_ADAPTER_CLASS;
        } else if (isDynamoDbAdapter()) {
            className = DYNAMO_ADAPTER_CLASS;
        } else if (isCouchbaseAdapter()) {
            className = COUCHBASE_ADAPTER_CLASS;
        } else {
            throw new FlamingockException("No compatible database driver detected. Please include a supported database dependency (MongoDB, DynamoDB, or Couchbase) in your project classpath.");
        }
        return className;
    }

    private static boolean isMongoDbAdapter() {
        try{
            Class.forName("com.mongodb.client.MongoCollection");
            return true;
        } catch (ClassNotFoundException e) {
            logger.warn("MongoDB adapter not found, skipping");
            return false;
        }
    }

    private static boolean isDynamoDbAdapter() {
        try{
            Class.forName("software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable");
            return true;
        } catch (ClassNotFoundException e) {
            logger.warn("DynamoDB adapter not found, skipping");
            return false;
        }
    }

    private static boolean isCouchbaseAdapter() {
        //TODO implement
        logger.warn("Couchbase adapter not implemented, skipping");
        return false;
    }
}

