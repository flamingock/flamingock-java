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
package io.flamingock.common.test.cloud.execution;

import io.flamingock.common.test.cloud.mock.MockRequestResponseChange;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class ExecutionBaseRequestResponseMock {


    private final Long executionId;
    private final long acquiredForMillis;
    private final String acquisitionId;
    private final List<MockRequestResponseChange> changes;

    public ExecutionBaseRequestResponseMock(Long executionId,
                                            long acquiredForMillis,
                                            String acquisitionId,
                                            MockRequestResponseChange...changes) {
        this.executionId = executionId;
        this.acquiredForMillis = acquiredForMillis;
        this.acquisitionId = acquisitionId;
        this.changes = Arrays.asList(changes);
    }

    public Long getExecutionId() {
        return executionId;
    }

    public long getAcquiredForMillis() {
        return acquiredForMillis;
    }

    public String getAcquisitionId() {
        return acquisitionId;
    }

    public List<MockRequestResponseChange> getChanges() {
        return changes;
    }

    public Optional<MockRequestResponseChange> getChangeById(String changeId) {
        return changes.stream()
                .filter(change -> changeId.equals(change.getChangeId()))
                .findFirst();
    }
}
