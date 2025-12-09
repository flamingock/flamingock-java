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

import java.util.Collections;
import java.util.List;

/**
 * Validation error indicating that the count of audit entries
 * does not match the expected count.
 *
 * <p>This error includes both the expected and actual lists of change IDs
 * to provide full context for debugging.</p>
 */
public class CountMismatchError extends ValidationError {

    private final List<String> expectedChangeIds;
    private final List<String> actualChangeIds;

    /**
     * Creates a new count mismatch error.
     *
     * @param expectedChangeIds the list of expected change IDs
     * @param actualChangeIds   the list of actual change IDs found
     */
    public CountMismatchError(List<String> expectedChangeIds, List<String> actualChangeIds) {
        super(ValidationErrorType.COUNT_MISMATCH);
        this.expectedChangeIds = expectedChangeIds != null
                ? Collections.unmodifiableList(expectedChangeIds)
                : Collections.emptyList();
        this.actualChangeIds = actualChangeIds != null
                ? Collections.unmodifiableList(actualChangeIds)
                : Collections.emptyList();
    }

    public List<String> getExpectedChangeIds() {
        return expectedChangeIds;
    }

    public List<String> getActualChangeIds() {
        return actualChangeIds;
    }

    @Override
    public String formatMessage() {
        return String.format(
                "Audit entry count mismatch: expected <%d> entries but found <%d>\n" +
                "    Expected: %s\n" +
                "    Actual:   %s",
                expectedChangeIds.size(),
                actualChangeIds.size(),
                expectedChangeIds,
                actualChangeIds);
    }
}
