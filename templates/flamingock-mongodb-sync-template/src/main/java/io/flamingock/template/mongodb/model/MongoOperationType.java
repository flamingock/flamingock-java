package io.flamingock.template.mongodb.model;

import com.mongodb.client.MongoDatabase;
import io.flamingock.template.mongodb.model.operator.CreateCollectionOperator;
import io.flamingock.template.mongodb.model.operator.CreateIndexOperator;
import io.flamingock.template.mongodb.model.operator.InsertOperator;
import io.flamingock.template.mongodb.model.operator.MongoOperator;

import java.util.Arrays;
import java.util.function.BiFunction;

public enum MongoOperationType {

    CREATE_COLLECTION("createCollection", CreateCollectionOperator::new),
    CREATE_INDEX("createIndex", CreateIndexOperator::new),
    INSERT("insert", InsertOperator::new);

    private final String value;
    private final BiFunction<MongoDatabase, MongoOperation, MongoOperator> createOperatorFunction;

    MongoOperationType(String value, BiFunction<MongoDatabase, MongoOperation, MongoOperator> createOperatorFunction) {
        this.value = value;
        this.createOperatorFunction = createOperatorFunction;
    }

    public static MongoOperationType getFromValue(String typeValue) {
        return Arrays.stream(MongoOperationType.values())
                .filter(type -> type.matches(typeValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("MongoOperation not supported: " + typeValue));
    }

    public MongoOperator getOperator(MongoDatabase mongoDatabase, MongoOperation operation) {
        return createOperatorFunction.apply(mongoDatabase, operation);
    }

    private boolean matches(String operationType) {
        return this.value.equals(operationType);
    }
}
