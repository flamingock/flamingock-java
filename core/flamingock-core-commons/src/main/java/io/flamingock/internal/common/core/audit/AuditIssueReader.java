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
