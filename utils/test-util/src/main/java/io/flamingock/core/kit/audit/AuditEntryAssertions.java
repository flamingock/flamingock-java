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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive assertion utilities for AuditEntry testing.
 * Provides systematic verification of all audit entry fields to ensure
 * complete correctness of audit persistence across different storage implementations.
 * 
 * <p><strong>Usage Examples:</strong></p>
 * <pre>{@code
 * // Complete audit entry verification
 * AuditEntryExpectation expected = auditEntry()
 *     .withTaskId("test-change")
 *     .withState(EXECUTED)
 *     .withTxType(TX_SHARED);
 * 
 * AuditEntryAssertions.assertAuditEntry(actualEntry, expected);
 * 
 * // Quick individual field verification
 * AuditEntryAssertions.assertTransactionFields(entry, TX_SHARED, "custom-target-system");
 * 
 * // Multiple entries verification
 * AuditEntryAssertions.assertAuditSequence(entries, 
 *     auditEntry().withState(STARTED),
 *     auditEntry().withState(APPLIED));
 * }</pre>
 */
public class AuditEntryAssertions {
    
    /**
     * Comprehensive verification of all audit entry fields against expectations.
     * This is the primary method for complete audit entry validation.
     *
     * @param actual the actual audit entry to verify
     * @param expected the expected values
     */
    public static void assertAuditEntry(AuditEntry actual, AuditEntryExpectation expected) {
        assertNotNull(actual, "Audit entry should not be null");
        
        // Identity fields
        if (expected.shouldVerifyExecutionId() && expected.getExpectedExecutionId() != null) {
            assertEquals(expected.getExpectedExecutionId(), actual.getExecutionId(),
                "ExecutionId mismatch");
        }
        
        if (expected.shouldVerifyStageId() && expected.getExpectedStageId() != null) {
            assertEquals(expected.getExpectedStageId(), actual.getStageId(),
                "StageId mismatch");
        }
        
        if (expected.getExpectedTaskId() != null) {
            assertEquals(expected.getExpectedTaskId(), actual.getTaskId(),
                "TaskId mismatch");
        }
        
        // Metadata fields
        if (expected.getExpectedAuthor() != null) {
            assertEquals(expected.getExpectedAuthor(), actual.getAuthor(),
                "Author mismatch");
        }
        
        // Timestamp verification (flexible)
        if (expected.shouldVerifyTimestamp()) {
            assertNotNull(actual.getCreatedAt(), "CreatedAt should not be null");
            
            if (expected.getExpectedCreatedAt() != null) {
                assertEquals(expected.getExpectedCreatedAt(), actual.getCreatedAt(),
                    "CreatedAt exact match failed");
            } else if (expected.getTimestampAfter() != null && expected.getTimestampBefore() != null) {
                assertTrue(actual.getCreatedAt().isAfter(expected.getTimestampAfter()) || 
                          actual.getCreatedAt().isEqual(expected.getTimestampAfter()),
                    "CreatedAt should be after " + expected.getTimestampAfter());
                assertTrue(actual.getCreatedAt().isBefore(expected.getTimestampBefore()) || 
                          actual.getCreatedAt().isEqual(expected.getTimestampBefore()),
                    "CreatedAt should be before " + expected.getTimestampBefore());
            }
        }
        
        // State fields
        if (expected.getExpectedState() != null) {
            assertEquals(expected.getExpectedState(), actual.getState(),
                "State mismatch");
        }
        
        if (expected.getExpectedType() != null) {
            assertEquals(expected.getExpectedType(), actual.getType(),
                "ExecutionType mismatch");
        }
        
        // Execution fields
        if (expected.getExpectedClassName() != null) {
            assertEquals(expected.getExpectedClassName(), actual.getClassName(),
                "ClassName mismatch");
        }
        
        if (expected.getExpectedMethodName() != null) {
            assertEquals(expected.getExpectedMethodName(), actual.getMethodName(),
                "MethodName mismatch");
        }
        
        if (expected.getExpectedMetadata() != null) {
            assertEquals(expected.getExpectedMetadata(), actual.getMetadata(),
                "Metadata mismatch");
        }
        
        // Performance fields
        if (expected.shouldVerifyExecutionMillis() && expected.getExpectedExecutionMillis() != null) {
            assertEquals(expected.getExpectedExecutionMillis().longValue(), actual.getExecutionMillis(),
                "ExecutionMillis mismatch");
        }
        
        if (expected.shouldVerifyExecutionHostname() && expected.getExpectedExecutionHostname() != null) {
            assertEquals(expected.getExpectedExecutionHostname(), actual.getExecutionHostname(),
                "ExecutionHostname mismatch");
        }
        
        // Error fields
        if (expected.getExpectedErrorTrace() != null) {
            assertEquals(expected.getExpectedErrorTrace(), actual.getErrorTrace(),
                "ErrorTrace mismatch");
        }
        
        // System fields
        if (expected.getExpectedSystemChange() != null) {
            assertEquals(expected.getExpectedSystemChange(), actual.getSystemChange(),
                "SystemChange mismatch");
        }
        
        // Transaction fields
        if (expected.getExpectedTxType() != null) {
            assertEquals(expected.getExpectedTxType(), actual.getTxType(),
                "TxType mismatch");
        }
        
        if (expected.getExpectedTargetSystemId() != null) {
            assertEquals(expected.getExpectedTargetSystemId(), actual.getTargetSystemId(),
                "TargetSystemId mismatch");
        }
    }
    
    /**
     * Quick verification of basic audit entry identity fields.
     * 
     * @param entry the audit entry to verify
     * @param expectedTaskId expected task ID
     * @param expectedAuthor expected author
     * @param expectedState expected audit entry state
     */
    //TODO add author check, when CodeChangeUnitTestDefinition adds it
    public static void assertBasicFields(AuditEntry entry, String expectedTaskId, String expectedAuthor, 
                                       AuditEntry.Status expectedState) {
        assertNotNull(entry, "Audit entry should not be null");
        assertEquals(expectedTaskId, entry.getTaskId(), "TaskId mismatch");
//        assertEquals(expectedAuthor, entry.getAuthor(), "Author mismatch");
        assertEquals(expectedState, entry.getState(), "State mismatch");
    }
    
    /**
     * Quick verification of execution-related fields.
     * 
     * @param entry the audit entry to verify
     * @param expectedClassName expected class name
     * @param expectedMethodName expected method name
     * @param expectedType expected execution type
     */
    public static void assertExecutionFields(AuditEntry entry, String expectedClassName, 
                                           String expectedMethodName, AuditEntry.ExecutionType expectedType) {
        assertNotNull(entry, "Audit entry should not be null");
        assertEquals(expectedClassName, entry.getClassName(), "ClassName mismatch");
        assertEquals(expectedMethodName, entry.getMethodName(), "MethodName mismatch");
        assertEquals(expectedType, entry.getType(), "ExecutionType mismatch");
    }
    
    /**
     * Quick verification of transaction-related fields.
     * 
     * @param entry the audit entry to verify
     * @param expectedTxType expected transaction type
     * @param expectedTargetSystemId expected target system ID
     */
    public static void assertTransactionFields(AuditEntry entry, AuditTxType expectedTxType, 
                                             String expectedTargetSystemId) {
        assertNotNull(entry, "Audit entry should not be null");
        assertEquals(expectedTxType, entry.getTxType(), "TxType mismatch");
        assertEquals(expectedTargetSystemId, entry.getTargetSystemId(), "TargetSystemId mismatch");
    }
    
    /**
     * Quick verification of timing and performance fields.
     * 
     * @param entry the audit entry to verify
     * @param timestampAfter expected timestamp should be after this time (can be null)
     * @param timestampBefore expected timestamp should be before this time (can be null)
     */
    public static void assertTimingFields(AuditEntry entry, LocalDateTime timestampAfter, 
                                        LocalDateTime timestampBefore) {
        assertNotNull(entry, "Audit entry should not be null");
        assertNotNull(entry.getCreatedAt(), "CreatedAt should not be null");
        
        if (timestampAfter != null) {
            assertTrue(entry.getCreatedAt().isAfter(timestampAfter) || 
                      entry.getCreatedAt().isEqual(timestampAfter),
                "CreatedAt should be after " + timestampAfter);
        }
        
        if (timestampBefore != null) {
            assertTrue(entry.getCreatedAt().isBefore(timestampBefore) || 
                      entry.getCreatedAt().isEqual(timestampBefore),
                "CreatedAt should be before " + timestampBefore);
        }
        
        assertTrue(entry.getExecutionMillis() >= 0, "ExecutionMillis should be non-negative");
    }
    
    /**
     * Quick verification of system-related fields.
     * 
     * @param entry the audit entry to verify
     * @param expectedSystemChange expected system change flag
     * @param expectedHostname expected execution hostname (can be null)
     */
    public static void assertSystemFields(AuditEntry entry, boolean expectedSystemChange, 
                                        String expectedHostname) {
        assertNotNull(entry, "Audit entry should not be null");
        assertEquals(expectedSystemChange, entry.getSystemChange(), "SystemChange mismatch");
        
        if (expectedHostname != null) {
            assertEquals(expectedHostname, entry.getExecutionHostname(), "ExecutionHostname mismatch");
        }
    }
    
    /**
     * Verification of multiple audit entries in sequence.
     * Useful for testing complete execution flows (STARTED to APPLIED, etc.)
     * 
     * @param actualEntries list of actual audit entries from the system
     * @param expectedEntries expected audit entry expectations to verify against
     */
    public static void assertAuditSequence(List<AuditEntry> actualEntries, 
                                         AuditEntryExpectation... expectedEntries) {
        assertNotNull(actualEntries, "Audit entries list should not be null");
        assertEquals(expectedEntries.length, actualEntries.size(), 
            "Expected " + expectedEntries.length + " audit entries, but got " + actualEntries.size());
        
        for (int i = 0; i < expectedEntries.length; i++) {
            assertAuditEntry(actualEntries.get(i), expectedEntries[i]);
        }
    }
    
    /**
     * Verification that all entries in a sequence have the same core identity fields.
     * Useful for verifying that STARTED and APPLIED entries belong to the same execution.
     * 
     * @param entries list of audit entries to verify belong to same execution
     */
    public static void assertSameExecution(List<AuditEntry> entries) {
        assertNotNull(entries, "Audit entries list should not be null");
        assertTrue(entries.size() >= 2, "Need at least 2 entries to verify same execution");
        
        String expectedExecutionId = entries.get(0).getExecutionId();
        String expectedStageId = entries.get(0).getStageId();
        String expectedTaskId = entries.get(0).getTaskId();
        
        for (AuditEntry entry : entries) {
            assertEquals(expectedExecutionId, entry.getExecutionId(), 
                "All entries should have same executionId");
            assertEquals(expectedStageId, entry.getStageId(), 
                "All entries should have same stageId");
            assertEquals(expectedTaskId, entry.getTaskId(), 
                "All entries should have same taskId");
        }
    }
    
    /**
     * Quick verification that critical audit fields are not null/empty.
     * Useful for basic audit entry completeness checks.
     * 
     * @param entry the audit entry to verify for completeness
     */
    public static void assertAuditEntryCompleteness(AuditEntry entry) {
        assertNotNull(entry, "Audit entry should not be null");
        
        // Critical fields that should never be null/empty
        assertNotNull(entry.getExecutionId(), "ExecutionId should not be null");
        assertFalse(entry.getExecutionId().trim().isEmpty(), "ExecutionId should not be empty");
        
        assertNotNull(entry.getStageId(), "StageId should not be null");
        assertFalse(entry.getStageId().trim().isEmpty(), "StageId should not be empty");
        
        assertNotNull(entry.getTaskId(), "TaskId should not be null");
        assertFalse(entry.getTaskId().trim().isEmpty(), "TaskId should not be empty");
        
        assertNotNull(entry.getAuthor(), "Author should not be null");
        assertFalse(entry.getAuthor().trim().isEmpty(), "Author should not be empty");
        
        assertNotNull(entry.getCreatedAt(), "CreatedAt should not be null");
        assertNotNull(entry.getState(), "State should not be null");
        assertNotNull(entry.getType(), "ExecutionType should not be null");
        assertNotNull(entry.getTxType(), "TxType should not be null");
        
        // Fields that can be null but if present should not be empty strings
        if (entry.getClassName() != null) {
            assertFalse(entry.getClassName().trim().isEmpty(), "ClassName should not be empty if present");
        }
        
        if (entry.getMethodName() != null) {
            assertFalse(entry.getMethodName().trim().isEmpty(), "MethodName should not be empty if present");
        }
        
        if (entry.getTargetSystemId() != null) {
            assertFalse(entry.getTargetSystemId().trim().isEmpty(), "TargetSystemId should not be empty if present");
        }
    }
}