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
package io.flamingock.template.mongodb.validation;

import io.flamingock.template.mongodb.model.MongoOperation;
import io.flamingock.template.mongodb.model.MongoOperationType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Validates MongoDB template operations before execution.
 *
 * <p>This validator checks for required fields, valid parameter types,
 * and operation-specific constraints. All errors are collected and returned
 * together rather than failing on the first error.</p>
 *
 * <h2>Common Validations (All Operations)</h2>
 * <ul>
 *   <li><b>type</b> - Required, must be a known operation type from {@link MongoOperationType}</li>
 *   <li><b>collection</b> - Required, cannot be empty, cannot contain '$' or null character</li>
 *   <li><b>rollback</b> - If present, validated recursively with the same rules</li>
 * </ul>
 *
 * <h2>Operation-Specific Validations</h2>
 *
 * <h3>createCollection</h3>
 * <p>Only requires common validations (collection name).</p>
 * <pre>{@code
 * - type: createCollection
 *   collection: users
 * }</pre>
 *
 * <h3>dropCollection</h3>
 * <p>Only requires common validations (collection name).</p>
 * <pre>{@code
 * - type: dropCollection
 *   collection: users
 * }</pre>
 *
 * <h3>insert</h3>
 * <p>Requires {@code parameters.documents} as a non-empty list with no null elements.</p>
 * <pre>{@code
 * - type: insert
 *   collection: users
 *   parameters:
 *     documents:
 *       - name: "John"
 *         email: "john@example.com"
 * }</pre>
 *
 * <h3>delete</h3>
 * <p>Requires {@code parameters.filter}. Filter can be empty ({@code {}}) to delete all documents.</p>
 * <pre>{@code
 * - type: delete
 *   collection: users
 *   parameters:
 *     filter:
 *       status: "inactive"
 * }</pre>
 *
 * <h3>createIndex</h3>
 * <p>Requires {@code parameters.keys} as a non-empty map defining the index fields.</p>
 * <pre>{@code
 * - type: createIndex
 *   collection: users
 *   parameters:
 *     keys:
 *       email: 1
 *     options:
 *       unique: true
 * }</pre>
 *
 * <h3>dropIndex</h3>
 * <p>Requires either {@code parameters.indexName} OR {@code parameters.keys} (at least one).</p>
 * <pre>{@code
 * - type: dropIndex
 *   collection: users
 *   parameters:
 *     indexName: "email_1"
 * }</pre>
 *
 * <h3>renameCollection</h3>
 * <p>Requires {@code parameters.target} as a non-empty string for the new collection name.</p>
 * <pre>{@code
 * - type: renameCollection
 *   collection: old_users
 *   parameters:
 *     target: users_archive
 * }</pre>
 *
 * <h3>createView</h3>
 * <p>Requires {@code parameters.viewOn} (source collection) and {@code parameters.pipeline} (aggregation pipeline as list).</p>
 * <pre>{@code
 * - type: createView
 *   collection: active_users_view
 *   parameters:
 *     viewOn: users
 *     pipeline:
 *       - $match:
 *           status: "active"
 * }</pre>
 *
 * <h3>dropView</h3>
 * <p>Only requires common validations (collection/view name).</p>
 * <pre>{@code
 * - type: dropView
 *   collection: active_users_view
 * }</pre>
 *
 * <h3>modifyCollection</h3>
 * <p>Only requires common validations. Used for modifying collection options like validation rules.</p>
 * <pre>{@code
 * - type: modifyCollection
 *   collection: users
 *   parameters:
 *     validator:
 *       $jsonSchema:
 *         required: ["email"]
 * }</pre>
 *
 * @see MongoOperation
 * @see MongoOperationType
 * @see ValidationError
 */
public final class MongoOperationValidator {

    private static final String MONGO_OPERATION = "MongoOperation";

    private MongoOperationValidator() {
    }

    /**
     * Validates a MongoDB operation and returns all validation errors.
     *
     * @param operation the operation to validate
     * @param entityId  the identifier for error reporting (e.g., "changeId.operations[0]")
     * @return list of validation errors (empty if valid)
     */
    public static List<ValidationError> validate(MongoOperation operation, String entityId) {
        List<ValidationError> errors = new ArrayList<>();

        if (operation == null) {
            errors.add(new ValidationError(entityId, MONGO_OPERATION, "Operation cannot be null"));
            return errors;
        }

        // Operation type
        String typeValue = operation.getType();
        if (typeValue == null || typeValue.trim().isEmpty()) {
            errors.add(new ValidationError(entityId, MONGO_OPERATION, "Operation type is required"));
            return errors;
        }

        MongoOperationType type;
        try {
            type = MongoOperationType.getFromValue(typeValue);
        } catch (IllegalArgumentException e) {
            errors.add(new ValidationError(entityId, MONGO_OPERATION,
                    "Unknown operation type: " + typeValue));
            return errors; // Can't continue with unknown type
        }

        // 2. Collection name
        errors.addAll(validateCollectionName(operation.getCollection(), entityId));

        // 3. Type-specific
        errors.addAll(validateByType(type, operation, entityId));

        // 4. Rollback
        if (operation.getRollback() != null) {
            errors.addAll(validate(operation.getRollback(), entityId + ".rollback"));
        }

        return errors;
    }

    private static List<ValidationError> validateCollectionName(String collection, String entityId) {
        List<ValidationError> errors = new ArrayList<>();

        if (collection == null) {
            errors.add(new ValidationError(entityId, MONGO_OPERATION,
                    "Collection name is required"));
        } else if (collection.trim().isEmpty()) {
            errors.add(new ValidationError(entityId, MONGO_OPERATION,
                    "Collection name cannot be empty"));
        } else if (collection.contains("$")) {
            errors.add(new ValidationError(entityId, MONGO_OPERATION,
                    "Collection name cannot contain '$': " + collection));
        } else if (collection.contains("\0")) {
            errors.add(new ValidationError(entityId, MONGO_OPERATION,
                    "Collection name cannot contain null character"));
        }

        return errors;
    }

    private static List<ValidationError> validateByType(MongoOperationType type,
                                                         MongoOperation op,
                                                         String entityId) {
        switch (type) {
            case INSERT:
                return validateInsert(op, entityId);
            case DELETE:
                return validateDelete(op, entityId);
            case CREATE_INDEX:
                return validateCreateIndex(op, entityId);
            case DROP_INDEX:
                return validateDropIndex(op, entityId);
            case RENAME_COLLECTION:
                return validateRenameCollection(op, entityId);
            case CREATE_VIEW:
                return validateCreateView(op, entityId);
            default:
                return new ArrayList<>();
        }
    }

    private static List<ValidationError> validateInsert(MongoOperation op, String entityId) {
        List<ValidationError> errors = new ArrayList<>();
        Map<String, Object> params = op.getParameters();

        if (params == null) {
            errors.add(new ValidationError(entityId, "InsertOperation",
                    "Insert operation requires 'parameters' with 'documents'"));
            return errors;
        }

        Object docs = params.get("documents");
        if (docs == null) {
            errors.add(new ValidationError(entityId, "InsertOperation",
                    "Insert operation requires 'documents' parameter"));
            return errors;
        }

        if (!(docs instanceof List)) {
            errors.add(new ValidationError(entityId, "InsertOperation",
                    "'documents' must be a list"));
            return errors;
        }

        List<?> docList = (List<?>) docs;
        if (docList.isEmpty()) {
            errors.add(new ValidationError(entityId, "InsertOperation",
                    "'documents' cannot be empty"));
        }

        for (int i = 0; i < docList.size(); i++) {
            if (docList.get(i) == null) {
                errors.add(new ValidationError(entityId, "InsertOperation",
                        "Document at index " + i + " is null"));
            }
        }

        return errors;
    }

    private static List<ValidationError> validateDelete(MongoOperation op, String entityId) {
        List<ValidationError> errors = new ArrayList<>();
        Map<String, Object> params = op.getParameters();

        if (params == null || !params.containsKey("filter")) {
            errors.add(new ValidationError(entityId, "DeleteOperation",
                    "Delete operation requires 'filter' parameter"));
        }

        return errors;
    }

    private static List<ValidationError> validateCreateIndex(MongoOperation op, String entityId) {
        List<ValidationError> errors = new ArrayList<>();
        Map<String, Object> params = op.getParameters();

        if (params == null) {
            errors.add(new ValidationError(entityId, "CreateIndexOperation",
                    "CreateIndex operation requires 'parameters' with 'keys'"));
            return errors;
        }

        Object keys = params.get("keys");
        if (keys == null) {
            errors.add(new ValidationError(entityId, "CreateIndexOperation",
                    "CreateIndex operation requires 'keys' parameter"));
            return errors;
        }

        if (!(keys instanceof Map)) {
            errors.add(new ValidationError(entityId, "CreateIndexOperation",
                    "'keys' must be a map"));
            return errors;
        }

        if (((Map<?, ?>) keys).isEmpty()) {
            errors.add(new ValidationError(entityId, "CreateIndexOperation",
                    "'keys' cannot be empty"));
        }

        return errors;
    }

    private static List<ValidationError> validateDropIndex(MongoOperation op, String entityId) {
        List<ValidationError> errors = new ArrayList<>();
        Map<String, Object> params = op.getParameters();

        if (params == null) {
            errors.add(new ValidationError(entityId, "DropIndexOperation",
                    "DropIndex operation requires 'parameters' with 'indexName' or 'keys'"));
            return errors;
        }

        Object indexName = params.get("indexName");
        Object keys = params.get("keys");

        if (indexName == null && keys == null) {
            errors.add(new ValidationError(entityId, "DropIndexOperation",
                    "DropIndex operation requires either 'indexName' or 'keys' parameter"));
        }

        return errors;
    }

    private static List<ValidationError> validateRenameCollection(MongoOperation op, String entityId) {
        List<ValidationError> errors = new ArrayList<>();
        Map<String, Object> params = op.getParameters();

        if (params == null || !params.containsKey("target")) {
            errors.add(new ValidationError(entityId, "RenameCollectionOperation",
                    "RenameCollection operation requires 'target' parameter"));
            return errors;
        }

        Object target = params.get("target");
        if (target == null || (target instanceof String && ((String) target).trim().isEmpty())) {
            errors.add(new ValidationError(entityId, "RenameCollectionOperation",
                    "'target' cannot be null or empty"));
        }

        return errors;
    }

    private static List<ValidationError> validateCreateView(MongoOperation op, String entityId) {
        List<ValidationError> errors = new ArrayList<>();
        Map<String, Object> params = op.getParameters();

        if (params == null) {
            errors.add(new ValidationError(entityId, "CreateViewOperation",
                    "CreateView operation requires 'parameters' with 'viewOn' and 'pipeline'"));
            return errors;
        }

        Object viewOn = params.get("viewOn");
        if (viewOn == null || (viewOn instanceof String && ((String) viewOn).trim().isEmpty())) {
            errors.add(new ValidationError(entityId, "CreateViewOperation",
                    "CreateView operation requires 'viewOn' parameter"));
        }

        Object pipeline = params.get("pipeline");
        if (pipeline == null) {
            errors.add(new ValidationError(entityId, "CreateViewOperation",
                    "CreateView operation requires 'pipeline' parameter"));
        } else if (!(pipeline instanceof List)) {
            errors.add(new ValidationError(entityId, "CreateViewOperation",
                    "'pipeline' must be a list"));
        }

        return errors;
    }
}
