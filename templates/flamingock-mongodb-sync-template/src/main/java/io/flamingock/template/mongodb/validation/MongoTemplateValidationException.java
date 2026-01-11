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

import java.util.Collections;
import java.util.List;

/**
 * Exception thrown when MongoDB template validation fails.
 *
 * <p>This exception collects all validation errors found during validation
 * and provides a formatted message listing all issues.</p>
 */
public class MongoTemplateValidationException extends RuntimeException {

    private final List<ValidationError> errors;

    /**
     * Creates a new validation exception with the given errors.
     *
     * @param errors the list of validation errors
     */
    public MongoTemplateValidationException(List<ValidationError> errors) {
        super(formatMessage(errors));
        this.errors = Collections.unmodifiableList(errors);
    }

    /**
     * Returns the list of validation errors.
     *
     * @return unmodifiable list of validation errors
     */
    public List<ValidationError> getErrors() {
        return errors;
    }

    private static String formatMessage(List<ValidationError> errors) {
        StringBuilder sb = new StringBuilder("MongoDB template validation failed with ")
                .append(errors.size())
                .append(" error(s):\n");
        for (ValidationError error : errors) {
            sb.append("  - [").append(error.getEntityId())
              .append("] ").append(error.getMessage()).append("\n");
        }
        return sb.toString();
    }
}
