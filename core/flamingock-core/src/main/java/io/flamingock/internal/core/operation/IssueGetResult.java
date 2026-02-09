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
package io.flamingock.internal.core.operation;

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.issue.AuditEntryIssue;
import io.flamingock.internal.common.core.response.data.IssueGetResponseData;

public class IssueGetResult extends AbstractOperationResult {

    private final AuditEntryIssue issue;
    private final boolean withGuidance;

    public IssueGetResult(AuditEntryIssue issue, boolean withGuidance) {
        this.issue = issue;
        this.withGuidance = withGuidance;
    }

    public AuditEntryIssue getIssue() {
        return issue;
    }

    public boolean isWithGuidance() {
        return withGuidance;
    }

    @Override
    public Object toResponseData() {
        if (issue == null) {
            return IssueGetResponseData.notFound();
        }

        AuditEntry entry = issue.getAuditEntry();
        return IssueGetResponseData.builder()
                .changeId(issue.getChangeId())
                .state(entry.getState() != null ? entry.getState().name() : null)
                .author(entry.getAuthor())
                .createdAt(entry.getCreatedAt())
                .errorTrace(entry.getErrorTrace())
                .targetSystemId(entry.getTargetSystemId())
                .executionId(entry.getExecutionId())
                .executionMillis(entry.getExecutionMillis())
                .className(entry.getClassName())
                .methodName(entry.getMethodName())
                .executionHostname(entry.getExecutionHostname())
                .recoveryStrategy(entry.getRecoveryStrategy() != null ? entry.getRecoveryStrategy().name() : null)
                .found(true)
                .build();
    }
}
