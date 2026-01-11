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

/**
 * Represents a validation error for MongoDB template operations.
 *
 * <p>Each error contains context about where the validation failed
 * (entityId, entityType) and a human-readable error message.</p>
 */
public class ValidationError {

    private final String entityId;
    private final String entityType;
    private final String message;

    /**
     * Creates a new validation error.
     *
     * @param entityId   the identifier of the entity that failed validation (e.g., "changeId.operations[0]")
     * @param entityType the type of entity (e.g., "MongoOperation", "InsertOperation")
     * @param message    the human-readable error message
     */
    public ValidationError(String entityId, String entityType, String message) {
        this.entityId = entityId;
        this.entityType = entityType;
        this.message = message;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s: %s", entityId, entityType, message);
    }
}
