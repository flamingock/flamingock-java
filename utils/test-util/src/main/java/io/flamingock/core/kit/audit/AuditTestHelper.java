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
import io.flamingock.internal.common.core.audit.AuditTxType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Storage-agnostic helper for audit testing operations.
 * 
 * <p>This class provides convenient methods for testing audit functionality regardless of
 * the underlying storage implementation. It works with any AuditStorage implementation,
 * enabling consistent audit testing across in-memory, MongoDB, DynamoDB, etc.</p>
 * 
 * <p><strong>Usage:</strong></p>
 * <pre>{@code
 * AuditTestHelper helper = testKit.getAuditHelper();
 * 
 * // Verify successful execution
 * helper.verifySuccessfulChangeExecution("my-change-id");
 * 
 * // Check audit counts
 * assertEquals(1, helper.getExecutedAuditCount());
 * assertEquals(0, helper.getFailedAuditCount());
 * }</pre>
 */
public class AuditTestHelper {
    
    private final AuditStorage auditStorage;
    
    public AuditTestHelper(AuditStorage auditStorage) {
        this.auditStorage = auditStorage;
    }

    public List<AuditEntry> getAuditEntriesSorted() {
        return auditStorage.getAuditEntries().stream()
                .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .collect(Collectors.toList());
    }

    public long getStartedAuditCount() {
        return auditStorage.countAuditEntriesWithStatus(AuditEntry.Status.STARTED);
    }

    public long getExecutedAuditCount() {
        return auditStorage.countAuditEntriesWithStatus(AuditEntry.Status.EXECUTED);
    }

    public long getFailedAuditCount() {
        return auditStorage.countAuditEntriesWithStatus(AuditEntry.Status.EXECUTION_FAILED);
    }

    public long getRolledBackAuditCount() {
        return auditStorage.countAuditEntriesWithStatus(AuditEntry.Status.ROLLED_BACK);
    }

    public boolean verifySuccessfulChangeExecution(String changeId) {
        List<AuditEntry> changeEntries = auditStorage.getAuditEntriesForChange(changeId);
        
        if (changeEntries.isEmpty()) {
            return false;
        }
        
        boolean hasStarted = changeEntries.stream().anyMatch(e -> e.getState() == AuditEntry.Status.STARTED);
        boolean hasExecuted = changeEntries.stream().anyMatch(e -> e.getState() == AuditEntry.Status.EXECUTED);
        
        return hasStarted && hasExecuted;
    }

    public void addStartedAuditEntry(String changeId, String author) {
        AuditEntry startedEntry = new AuditEntry(
                "test-execution-id",
                "test-stage-id", 
                changeId,
                author,
                LocalDateTime.now(),
                AuditEntry.Status.STARTED,
                AuditEntry.ExecutionType.EXECUTION,
                "TestClass",
                "testMethod",
                0L,
                "test-hostname",
                null,
                false,
                null,
                AuditTxType.NON_TX
        );
        auditStorage.addAuditEntry(startedEntry);
    }

    public void addExecutedAuditEntry(String changeId, String author) {
        AuditEntry executedEntry = new AuditEntry(
                "test-execution-id",
                "test-stage-id", 
                changeId,
                author,
                LocalDateTime.now(),
                AuditEntry.Status.EXECUTED,
                AuditEntry.ExecutionType.EXECUTION,
                "TestClass",
                "testMethod",
                100L,
                "test-hostname",
                null,
                false,
                null,
                AuditTxType.NON_TX
        );
        auditStorage.addAuditEntry(executedEntry);
    }

    public void clear() {
        auditStorage.clear();
    }

    public boolean hasNoAuditEntries() {
        return !auditStorage.hasAuditEntries();
    }

    public boolean hasAuditEntryCount(int expectedCount) {
        return auditStorage.getAuditEntries().size() == expectedCount;
    }

    public void verifyAuditSequenceStrict(AuditExpectation... expectedAudits) {
        List<AuditEntry> actualEntries = getAuditEntriesSorted();
        
        // Check count first
        if (actualEntries.size() != expectedAudits.length) {
            throw new AssertionError(String.format(
                "Expected %d audit entries but found %d. Expected: %s, Actual: %s",
                expectedAudits.length,
                actualEntries.size(),
                formatExpectedSequence(expectedAudits),
                formatActualSequence(actualEntries)
            ));
        }
        
        // Check each entry
        for (int i = 0; i < expectedAudits.length; i++) {
            AuditEntry actual = actualEntries.get(i);
            AuditExpectation expected = expectedAudits[i];
            
            if (!expected.getChangeId().equals(actual.getTaskId()) || 
                expected.getState() != actual.getState()) {
                
                throw new AssertionError(String.format(
                    "Audit entry mismatch at position %d. Expected: %s, Actual: {changeId='%s', state=%s}. " +
                    "Full expected sequence: %s, Full actual sequence: %s",
                    i,
                    expected,
                    actual.getTaskId(),
                    actual.getState(),
                    formatExpectedSequence(expectedAudits),
                    formatActualSequence(actualEntries)
                ));
            }
        }
    }
    
    private String formatExpectedSequence(AuditExpectation[] expectedAudits) {
        if (expectedAudits.length == 0) {
            return "[]";
        }
        
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < expectedAudits.length; i++) {
            if (i > 0) sb.append(", ");
            AuditExpectation exp = expectedAudits[i];
            sb.append(String.format("(%s, %s)", exp.getChangeId(), exp.getState()));
        }
        sb.append("]");
        return sb.toString();
    }
    
    private String formatActualSequence(List<AuditEntry> actualEntries) {
        if (actualEntries.isEmpty()) {
            return "[]";
        }
        
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < actualEntries.size(); i++) {
            if (i > 0) sb.append(", ");
            AuditEntry entry = actualEntries.get(i);
            sb.append(String.format("(%s, %s)", entry.getTaskId(), entry.getState()));
        }
        sb.append("]");
        return sb.toString();
    }
}