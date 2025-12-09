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
 * Enumeration of validation error types for categorizing different kinds
 * of validation failures in the Flamingock test support framework.
 */
public enum ValidationErrorType {

    /**
     * A field value in an audit entry does not match the expected value.
     */
    FIELD_MISMATCH,

    /**
     * An expected audit entry is missing from the actual results.
     */
    MISSING_ENTRY,

    /**
     * An unexpected audit entry was found in the actual results.
     */
    UNEXPECTED_ENTRY,

    /**
     * The count of audit entries does not match the expected count.
     */
    COUNT_MISMATCH,

    /**
     * The thrown exception type does not match the expected type,
     * or no exception was thrown when one was expected.
     */
    EXCEPTION_TYPE_MISMATCH,

    /**
     * An exception was thrown when none was expected.
     */
    EXCEPTION_NOT_EXPECTED,

    /**
     * A custom validation (user-provided validator) failed.
     */
    CUSTOM_VALIDATION
}
