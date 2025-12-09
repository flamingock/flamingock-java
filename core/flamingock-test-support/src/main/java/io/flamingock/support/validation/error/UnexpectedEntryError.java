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
 * Validation error indicating that an unexpected audit entry
 * was found in the actual results.
 */
public class UnexpectedEntryError extends ValidationError {

    private final int index;
    private final String actualChangeId;
    private final AuditEntry.Status actualStatus;

    /**
     * Creates a new unexpected entry error.
     *
     * @param index          the index where the unexpected entry was found
     * @param actualChangeId the change ID of the unexpected entry
     * @param actualStatus   the status of the unexpected entry
     */
    public UnexpectedEntryError(int index, String actualChangeId, AuditEntry.Status actualStatus) {
        super(ValidationErrorType.UNEXPECTED_ENTRY);
        this.index = index;
        this.actualChangeId = actualChangeId;
        this.actualStatus = actualStatus;
    }

    public int getIndex() {
        return index;
    }

    public String getActualChangeId() {
        return actualChangeId;
    }

    public AuditEntry.Status getActualStatus() {
        return actualStatus;
    }

    @Override
    public String formatMessage() {
        return String.format("Entry[%d]: unexpected entry with changeId='%s' (%s)",
                index,
                actualChangeId,
                actualStatus);
    }
}
