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

import io.flamingock.internal.common.core.audit.issue.AuditEntryIssue;
import io.flamingock.internal.common.core.response.data.IssueListResponseData;
import io.flamingock.internal.common.core.response.data.IssueListResponseData.IssueSummaryDto;

import java.util.List;
import java.util.stream.Collectors;

public class IssueListResult extends AbstractOperationResult {

    private final List<AuditEntryIssue> issues;

    public IssueListResult(List<AuditEntryIssue> issues) {
        this.issues = issues;
    }

    public List<AuditEntryIssue> getIssues() {
        return issues;
    }

    @Override
    public Object toResponseData() {
        List<IssueSummaryDto> dtos = issues.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return new IssueListResponseData(dtos);
    }

    private IssueSummaryDto toDto(AuditEntryIssue issue) {
        String errorSummary = issue.getErrorMessage();
        if (errorSummary != null && errorSummary.length() > 100) {
            errorSummary = errorSummary.substring(0, 97) + "...";
        }
        return new IssueSummaryDto(
                issue.getChangeId(),
                issue.getAuditEntry().getState() != null ? issue.getAuditEntry().getState().name() : null,
                issue.getAuditEntry().getCreatedAt(),
                errorSummary
        );
    }
}
