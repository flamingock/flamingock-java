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
package io.flamingock.template.mongodb.model;

import com.mongodb.client.MongoDatabase;
import io.flamingock.template.mongodb.model.operator.CreateCollectionOperator;
import io.flamingock.template.mongodb.model.operator.CreateIndexOperator;
import io.flamingock.template.mongodb.model.operator.CreateViewOperator;
import io.flamingock.template.mongodb.model.operator.DeleteOperator;
import io.flamingock.template.mongodb.model.operator.DropCollectionOperator;
import io.flamingock.template.mongodb.model.operator.DropIndexOperator;
import io.flamingock.template.mongodb.model.operator.DropViewOperator;
import io.flamingock.template.mongodb.model.operator.InsertOperator;
import io.flamingock.template.mongodb.model.operator.ModifyCollectionOperator;
import io.flamingock.template.mongodb.model.operator.MongoOperator;
import io.flamingock.template.mongodb.model.operator.RenameCollectionOperator;
import io.flamingock.template.mongodb.model.operator.UpdateOperator;

import java.util.Arrays;
import java.util.function.BiFunction;

public enum MongoOperationType {

    CREATE_COLLECTION("createCollection", CreateCollectionOperator::new),
    CREATE_INDEX("createIndex", CreateIndexOperator::new),
    INSERT("insert", InsertOperator::new),
    UPDATE("update", UpdateOperator::new),
    DELETE("delete", DeleteOperator::new),
    DROP_COLLECTION("dropCollection", DropCollectionOperator::new),
    DROP_INDEX("dropIndex", DropIndexOperator::new),
    RENAME_COLLECTION("renameCollection", RenameCollectionOperator::new),
    MODIFY_COLLECTION("modifyCollection", ModifyCollectionOperator::new),
    CREATE_VIEW("createView", CreateViewOperator::new),
    DROP_VIEW("dropView", DropViewOperator::new);

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
