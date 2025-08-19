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
package io.flamingock.core.kit.inmemory;

import io.flamingock.core.kit.audit.AuditStorage;
import io.flamingock.internal.common.core.audit.AuditEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * In-memory implementation of AuditStorage for core e2e testing.
 * 
 * <p>This implementation stores audit entries in a thread-safe ArrayList, making it
 * ideal for testing core Flamingock audit functionality without external dependencies.
 * It provides fast execution and easy setup for unit and integration tests.</p>
 * 
 * <p><strong>Usage:</strong> Typically used through InMemoryTestKit, but can be used
 * directly when needed:</p>
 * <pre>{@code
 * InMemoryAuditStorage storage = new InMemoryAuditStorage();
 * AuditTestHelper helper = new AuditTestHelper(storage);
 * }</pre>
 * 
 * <p><strong>For other storage implementations:</strong> Use this as a reference
 * when implementing AuditStorage for MongoDB, DynamoDB, etc.</p>
 */
public class InMemoryAuditStorage implements AuditStorage {
    
    private final List<AuditEntry> auditEntries = new ArrayList<>();

    public synchronized void addAuditEntry(AuditEntry auditEntry) {
        auditEntries.add(auditEntry);
    }

    public synchronized List<AuditEntry> getAuditEntries() {
        return new ArrayList<>(auditEntries);
    }

    public synchronized void clear() {
        auditEntries.clear();
    }

    public synchronized long countAuditEntriesWithStatus(AuditEntry.Status status) {
        return auditEntries.stream()
                .filter(entry -> entry.getState() == status)
                .count();
    }

    public synchronized List<AuditEntry> getAuditEntriesForChange(String changeId) {
        return auditEntries.stream()
                .filter(entry -> changeId.equals(entry.getTaskId()))
                .collect(Collectors.toList());
    }

    public synchronized boolean hasAuditEntries() {
        return !auditEntries.isEmpty();
    }
}