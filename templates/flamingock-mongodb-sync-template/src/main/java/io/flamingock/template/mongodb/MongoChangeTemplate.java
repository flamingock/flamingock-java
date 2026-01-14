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
package io.flamingock.template.mongodb;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoDatabase;
import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.Nullable;
import io.flamingock.api.annotations.Rollback;
import io.flamingock.api.template.AbstractChangeTemplate;
import io.flamingock.template.mongodb.model.MongoOperation;
import io.flamingock.template.mongodb.validation.MongoOperationValidator;
import io.flamingock.template.mongodb.validation.MongoTemplateValidationException;
import io.flamingock.template.mongodb.validation.ValidationError;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * MongoDB Change Template for executing declarative MongoDB operations defined in YAML.
 *
 * <h2>YAML Structure</h2>
 * <pre>{@code
 * id: create-orders-collection
 * transactional: true
 * template: MongoChangeTemplate
 * targetSystem:
 *   id: "mongodb"
 * apply:
 *   - type: createCollection
 *     collection: orders
 *   - type: insert
 *     collection: orders
 *     parameters:
 *       documents:
 *         - orderId: "ORD-001"
 *           customer: "John Doe"
 * rollback:
 *   - type: delete
 *     collection: orders
 *     parameters:
 *       filter: {}
 *   - type: dropCollection
 *     collection: orders
 * }</pre>
 *
 * <h2>Execution Behavior</h2>
 * <ul>
 *   <li>Apply operations execute sequentially in order</li>
 *   <li>Rollback operations execute sequentially in the order defined by the user</li>
 *   <li>The framework handles rollback invocation on failure</li>
 *   <li>In transactional mode, MongoDB transaction provides atomicity</li>
 * </ul>
 *
 * @see MongoOperation
 */
/*
 * Backward Compatibility for YAML Formats
 *
 * This template supports two YAML structures:
 * - New list format:   apply: [- type: createCollection, - type: insert]
 * - Old single format: apply: {type: createCollection, collection: users}
 *
 * The framework uses getApplyPayloadClass() to deserialize YAML payloads.
 * Due to Java type erasure, List<MongoOperation> becomes List.class, which
 * cannot deserialize the old Map format correctly.
 *
 * Solution: Override getApplyPayloadClass() to return Object.class, allowing
 * the framework to pass raw YAML data (Map or List). The convertRawPayload()
 * method then handles conversion to List<MongoOperation> for both formats.
 *
 * The converted operations are cached to avoid repeated conversion.
 */
public class MongoChangeTemplate extends AbstractChangeTemplate<Void, List<MongoOperation>, List<MongoOperation>> {

    private List<MongoOperation> convertedApplyOps;
    private List<MongoOperation> convertedRollbackOps;

    public MongoChangeTemplate() {
        super(MongoOperation.class);
    }

    /**
     * Returns Object.class to allow the framework to pass raw YAML data without
     * attempting to deserialize it as List. This enables backward compatibility
     * with the old single-operation YAML format.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Class<List<MongoOperation>> getApplyPayloadClass() {
        return (Class<List<MongoOperation>>) (Class<?>) Object.class;
    }

    /**
     * Returns Object.class to allow the framework to pass raw YAML data without
     * attempting to deserialize it as List. This enables backward compatibility
     * with the old single-operation YAML format.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Class<List<MongoOperation>> getRollbackPayloadClass() {
        return (Class<List<MongoOperation>>) (Class<?>) Object.class;
    }

    @Apply
    public void apply(MongoDatabase db, @Nullable ClientSession clientSession) {
        validateSession(clientSession);
        List<MongoOperation> operations = getConvertedApplyOperations();
        validatePayload(operations, changeId + ".apply");
        executeOperations(db, operations, clientSession);
    }

    @Rollback
    public void rollback(MongoDatabase db, @Nullable ClientSession clientSession) {
        validateSession(clientSession);
        List<MongoOperation> operations = getConvertedRollbackOperations();
        validatePayload(operations, changeId + ".rollback");
        executeOperations(db, operations, clientSession);
    }

    private List<MongoOperation> getConvertedApplyOperations() {
        if (convertedApplyOps == null) {
            convertedApplyOps = convertRawPayload(getRawPayload("applyPayload"));
        }
        return convertedApplyOps;
    }

    private List<MongoOperation> getConvertedRollbackOperations() {
        if (convertedRollbackOps == null) {
            convertedRollbackOps = convertRawPayload(getRawPayload("rollbackPayload"));
        }
        return convertedRollbackOps;
    }

    /**
     * Accesses a payload field via reflection to avoid Java's checkcast instruction.
     * Since we override getApplyPayloadClass() to return Object.class, the actual
     * runtime value may be a Map (old format) or List (new format), not List<MongoOperation>.
     */
    private Object getRawPayload(String fieldName) {
        try {
            java.lang.reflect.Field field = findField(getClass(), fieldName);
            if (field != null) {
                field.setAccessible(true);
                return field.get(this);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private java.lang.reflect.Field findField(Class<?> clazz, String fieldName) {
        while (clazz != null) {
            try {
                return clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }

    private void validateSession(ClientSession clientSession) {
        if (this.isTransactional && clientSession == null) {
            throw new IllegalArgumentException(
                    String.format("Transactional change[%s] requires transactional ecosystem with ClientSession", changeId));
        }
    }

    private void validatePayload(List<MongoOperation> operations, String entityId) {
        if (operations == null || operations.isEmpty()) {
            return;
        }

        List<ValidationError> errors = new ArrayList<>();
        for (int i = 0; i < operations.size(); i++) {
            String opId = entityId + "[" + i + "]";
            errors.addAll(MongoOperationValidator.validate(operations.get(i), opId));
        }

        if (!errors.isEmpty()) {
            throw new MongoTemplateValidationException(errors);
        }
    }

    private void executeOperations(MongoDatabase db, List<MongoOperation> operations, ClientSession clientSession) {
        if (operations == null || operations.isEmpty()) {
            return;
        }

        for (MongoOperation op : operations) {
            op.getOperator(db).apply(clientSession);
        }
    }

    @SuppressWarnings("unchecked")
    private List<MongoOperation> convertRawPayload(Object rawPayload) {
        if (rawPayload == null) {
            return null;
        }

        // Handle single MongoOperation (already converted)
        if (rawPayload instanceof MongoOperation) {
            List<MongoOperation> operations = new ArrayList<>();
            operations.add((MongoOperation) rawPayload);
            return operations;
        }

        // Handle single Map (backward compatibility)
        if (rawPayload instanceof Map && !(rawPayload instanceof Collection)) {
            Map<String, Object> map = (Map<String, Object>) rawPayload;
            // Check if it looks like an operation (has 'type' field)
            if (map.containsKey("type")) {
                List<MongoOperation> operations = new ArrayList<>();
                operations.add(convertMapToOperation(map));
                return operations;
            }
        }

        // Handle Collection types
        if (rawPayload instanceof Collection) {
            Collection<?> rawCollection = (Collection<?>) rawPayload;
            if (rawCollection.isEmpty()) {
                return new ArrayList<>();
            }

            // Check if already properly deserialized
            Object firstElement = rawCollection.iterator().next();
            if (firstElement instanceof MongoOperation) {
                // If it's already a List<MongoOperation>, return as-is
                if (rawPayload instanceof List) {
                    return (List<MongoOperation>) rawPayload;
                }
                // Otherwise convert to List
                return new ArrayList<>((Collection<MongoOperation>) rawCollection);
            }

            // Convert from LinkedHashMap elements
            List<MongoOperation> operations = new ArrayList<>();
            for (Object item : rawCollection) {
                if (item instanceof Map) {
                    operations.add(convertMapToOperation((Map<String, Object>) item));
                }
            }
            return operations;
        }

        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private MongoOperation convertMapToOperation(Map<String, Object> map) {
        MongoOperation op = new MongoOperation();
        op.setType((String) map.get("type"));
        op.setCollection((String) map.get("collection"));

        Object params = map.get("parameters");
        if (params instanceof Map) {
            op.setParameters((Map<String, Object>) params);
        }

        return op;
    }
}
