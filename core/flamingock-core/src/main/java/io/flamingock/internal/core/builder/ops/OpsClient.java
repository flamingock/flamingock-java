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
package io.flamingock.internal.core.builder.ops;

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.core.store.audit.AuditPersistence;
import io.flamingock.internal.util.FlamingockLoggerFactory;
import io.flamingock.internal.util.id.RunnerId;
import org.slf4j.Logger;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

public class OpsClient {
    private final Logger logger = FlamingockLoggerFactory.getLogger("OpsClient");

    private final AuditPersistence auditPersistence;

    OpsClient(RunnerId runnerId, AuditPersistence auditPersistence) {
        this.auditPersistence = auditPersistence;
    }

    public void markAsSuccess(String changeUnit) {
        logger.info("ChangeUnit[{}] marked as success", changeUnit);
    }

    public void markAsRolledBack(String changeUnit) {
        logger.info("ChangeUnit[{}] marked as rolled back", changeUnit);
    }

    public List<AuditEntry> getConflictedAuditEntries() {
        logger.info("Listing audit entires");
        return Collections.emptyList();
    }

    /**
     * Get snapshot view - latest state per changeUnit (DEFAULT behavior)
     * @return List of latest audit entries per changeUnit
     */
    public List<AuditEntry> getAuditEntriesSnapshot() {
        logger.info("Getting audit entries snapshot (latest per changeUnit)");
        // TODO: Implementation - return latest entry for each unique changeUnit
        return Collections.emptyList();
    }

    /**
     * Get only entries with issues
     * @return List of audit entries with problems/issues
     */
    public List<AuditEntry> getAuditEntriesWithIssues() {
        logger.info("Getting audit entries with issues");
        // TODO: Implementation - filter for failed, conflicted, or problematic entries
        return Collections.emptyList();
    }

    /**
     * Get full chronological history
     * @return All audit entries ordered by timestamp
     */
    public List<AuditEntry> getAuditEntriesHistory() {
        logger.info("Getting full audit history");
        // TODO: Implementation - return all entries ordered chronologically
        return Collections.emptyList();
    }

    /**
     * Get entries since a specific date
     * @param since The date to filter from
     * @return List of audit entries after the specified date
     */
    public List<AuditEntry> getAuditEntriesSince(LocalDateTime since) {
        logger.info("Getting audit entries since: {}", since);
        // TODO: Implementation - filter entries by timestamp >= since
        return Collections.emptyList();
    }

    /**
     * Get paginated results
     * @param limit Number of entries per page
     * @param page Page number (1-based)
     * @return List of audit entries for the specified page
     */
    public List<AuditEntry> getAuditEntriesPaginated(int limit, int page) {
        logger.info("Getting paginated audit entries - limit: {}, page: {}", limit, page);
        // TODO: Implementation - return subset based on pagination
        return Collections.emptyList();
    }

    /**
     * Combined method for all filters
     * @param snapshot Whether to get snapshot view (latest per changeUnit)
     * @param issuesOnly Whether to filter for issues only
     * @param fullHistory Whether to get full chronological history
     * @param since Filter entries after this date
     * @param limit Pagination limit
     * @param page Pagination page number
     * @return List of audit entries based on filters
     */
    public List<AuditEntry> getAuditEntries(boolean snapshot, boolean issuesOnly, 
                                            boolean fullHistory, LocalDateTime since, 
                                            Integer limit, Integer page) {
        logger.info("Getting audit entries with filters - snapshot: {}, issues: {}, history: {}, since: {}, limit: {}, page: {}", 
                    snapshot, issuesOnly, fullHistory, since, limit, page);
        // TODO: Implementation - combine all filtering logic
        return Collections.emptyList();
    }
}
