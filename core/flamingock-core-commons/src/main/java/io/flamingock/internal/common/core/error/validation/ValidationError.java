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

package io.flamingock.internal.common.core.error.validation;

public class ValidationError {
    private final String message;
    private final String entityId;
    private final String entityType;

    /**
     * Creates a new validation error.
     *
     * @param message    error message
     * @param entityId   ID of the entity that failed (task ID, stage name, order...)
     * @param entityType The type of entity that failed ("task", "stage", "pipeline")
     */
    public ValidationError(String message, String entityId, String entityType) {
        this.message = message;
        this.entityId = entityId;
        this.entityType = entityType;
    }

    public String getMessage() {
        return message;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getFormattedMessage() {
        return String.format("[%s: %s] %s", entityType, entityId, message);
    }

    @Override
    public String toString() {
        return getFormattedMessage();
    }
}