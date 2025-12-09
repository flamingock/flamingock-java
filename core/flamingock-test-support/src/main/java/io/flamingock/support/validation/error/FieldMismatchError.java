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
package io.flamingock.support.validation.error;

/**
 * Validation error indicating that a field value in an audit entry
 * does not match the expected value.
 */
public class FieldMismatchError extends ValidationError {

    private final String fieldName;
    private final String expectedValue;
    private final String actualValue;

    /**
     * Creates a new field mismatch error.
     *
     * @param fieldName     the name of the field that mismatched
     * @param expectedValue the expected value (may be null)
     * @param actualValue   the actual value found (may be null)
     */
    public FieldMismatchError(String fieldName, String expectedValue, String actualValue) {
        super(ValidationErrorType.FIELD_MISMATCH);
        this.fieldName = fieldName;
        this.expectedValue = expectedValue;
        this.actualValue = actualValue;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getExpectedValue() {
        return expectedValue;
    }

    public String getActualValue() {
        return actualValue;
    }

    @Override
    public String formatMessage() {
        return String.format("Field '%s': expected <%s> but was <%s>",
                fieldName,
                expectedValue != null ? expectedValue : "null",
                actualValue != null ? actualValue : "null");
    }
}
