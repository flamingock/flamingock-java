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

import io.flamingock.internal.common.core.audit.AuditEntry;

/**
 * Validation error indicating that an expected audit entry
 * is missing from the actual results.
 */
public class MissingEntryError extends ValidationError {

    private final int expectedIndex;
    private final String expectedChangeId;
    private final AuditEntry.Status expectedStatus;

    /**
     * Creates a new missing entry error.
     *
     * @param expectedIndex    the index where the entry was expected
     * @param expectedChangeId the change ID of the expected entry
     * @param expectedStatus   the expected status of the entry
     */
    public MissingEntryError(int expectedIndex, String expectedChangeId, AuditEntry.Status expectedStatus) {
        super(ValidationErrorType.MISSING_ENTRY);
        this.expectedIndex = expectedIndex;
        this.expectedChangeId = expectedChangeId;
        this.expectedStatus = expectedStatus;
    }

    public int getExpectedIndex() {
        return expectedIndex;
    }

    public String getExpectedChangeId() {
        return expectedChangeId;
    }

    public AuditEntry.Status getExpectedStatus() {
        return expectedStatus;
    }

    @Override
    public String formatMessage() {
        return String.format("Entry[%d]: expected entry with changeId='%s' (%s) but no entry found",
                expectedIndex,
                expectedChangeId,
                expectedStatus);
    }
}
