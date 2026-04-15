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
package io.flamingock.common.test.cloud;

import io.flamingock.cloud.api.vo.CloudAuditStatus;

public class AuditRequestExpectation {
    private final String changeId;
    private final CloudAuditStatus state;
    private final String executionId;

    public AuditRequestExpectation(String executionId, String changeId, CloudAuditStatus state) {
        this.executionId = executionId;
        this.changeId = changeId;
        this.state = state;
    }

    public String getChangeId() {
        return changeId;
    }

    public CloudAuditStatus getState() {
        return state;
    }

    public String getExecutionId() {
        return executionId;
    }
}
