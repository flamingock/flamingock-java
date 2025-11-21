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
package io.flamingock.internal.common.core.audit;

import io.flamingock.internal.common.core.audit.issue.AuditEntryIssue;

import java.util.List;
import java.util.Optional;

public interface AuditIssueReader {
    /**
     * Get only entries with issues
     * @return List of audit entries with problems/issues
     */
    List<AuditEntryIssue> getAuditIssues();

    /**
     * Get detailed information about a specific change that has issues.
     * This includes full audit history, error messages, and execution attempts.
     *
     * @param changeId the change ID to inspect
     * @return detailed issue information including all audit entries, error details, etc.
     */
    Optional<AuditEntryIssue> getAuditIssueByChangeId(String changeId);

}
