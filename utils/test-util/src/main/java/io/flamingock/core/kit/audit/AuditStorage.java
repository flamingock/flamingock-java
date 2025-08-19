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
package io.flamingock.core.kit.audit;

import io.flamingock.internal.common.core.audit.AuditEntry;

import java.util.List;

/**
 * Storage abstraction for audit entries used in testing.
 * 
 * <p>This interface enables audit-store agnostic testing by providing a consistent API
 * for storing and retrieving audit entries regardless of the underlying storage technology.
 * Implementations can use in-memory collections, MongoDB, DynamoDB, or any other storage.</p>
 * 
 * <p><strong>Implementing for new storage types:</strong></p>
 * <pre>{@code
 * public class MyStorageAuditStorage implements AuditStorage {
 *     public void addAuditEntry(AuditEntry entry) {
 *         // Store to your specific storage
 *     }
 *     // ... implement other methods
 * }
 * }</pre>
 * 
 * <p>Used by AuditTestHelper to provide storage-agnostic audit testing capabilities.</p>
 */
public interface AuditStorage {

    /**
     * Stores an audit entry.
     * @param auditEntry the audit entry to store
     */
    void addAuditEntry(AuditEntry auditEntry);

    /**
     * Retrieves all stored audit entries.
     * @return list of all audit entries
     */
    List<AuditEntry> getAuditEntries();

    /**
     * Retrieves audit entries for a specific change.
     * @param changeId the change ID to filter by
     * @return list of audit entries for the specified change
     */
    List<AuditEntry> getAuditEntriesForChange(String changeId);

    /**
     * Counts audit entries with a specific status.
     * @param status the status to count
     * @return number of entries with the specified status
     */
    long countAuditEntriesWithStatus(AuditEntry.Status status);

    /**
     * Checks if any audit entries exist.
     * @return true if any entries exist, false otherwise
     */
    boolean hasAuditEntries();

    /**
     * Clears all stored audit entries (useful for test cleanup).
     */
    void clear();
}