/*
 * Copyright 2026 Flamingock (https://www.flamingock.io)
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
package io.flamingock.api.template;

/**
 * Represents a validation error found in a {@link TemplatePayload}.
 *
 * <p>Errors can be field-level (with a specific field name) or general (without a field).
 */
public class TemplatePayloadValidationError {

    private final String field;
    private final String message;

    public TemplatePayloadValidationError(String message) {
        this(null, message);
    }

    public TemplatePayloadValidationError(String field, String message) {
        this.field = field;
        this.message = message;
    }

    public String getField() {
        return field;
    }

    public String getMessage() {
        return message;
    }

    public String getFormattedMessage() {
        return field != null ? String.format("[field: %s] %s", field, message) : message;
    }

    @Override
    public String toString() {
        return getFormattedMessage();
    }
}
