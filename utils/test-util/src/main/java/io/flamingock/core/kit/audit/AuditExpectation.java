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

/**
 * DSL for creating readable audit entry expectations in test scenarios.
 * 
 * <p>This class provides static factory methods for creating audit expectations
 * with a fluent, readable syntax that makes test assertions clear and maintainable.</p>
 * 
 * <p><strong>Usage Examples:</strong></p>
 * <pre>{@code
 * // Basic state expectations
 * STARTED("change1")
 * APPLIED("change2")
 * EXECUTION_FAILED("change3")
 * ROLLED_BACK("change4")
 * 
 * // Verify audit sequence
 * verify(STARTED("change1"), APPLIED("change1"), STARTED("change2"), APPLIED("change2"));
 * }</pre>
 */
public class AuditExpectation {
    
    /**
     * Creates an expectation for a STARTED audit entry.
     * 
     * @param changeId the change ID that should have a STARTED audit entry
     * @return expectation for STARTED state
     */
    public static AuditEntryExpectation STARTED(String changeId) {
        return AuditEntryExpectation.auditEntry()
            .withTaskId(changeId)
            .withState(AuditEntry.Status.STARTED);
    }
    
    /**
     * Creates an expectation for an APPLIED audit entry.
     * 
     * @param changeId the change ID that should have an APPLIED audit entry
     * @return expectation for APPLIED state
     */
    public static AuditEntryExpectation APPLIED(String changeId) {
        return AuditEntryExpectation.auditEntry()
            .withTaskId(changeId)
            .withState(AuditEntry.Status.APPLIED);
    }
    
    /**
     * Creates an expectation for an EXECUTION_FAILED audit entry.
     * 
     * @param changeId the change ID that should have an EXECUTION_FAILED audit entry
     * @return expectation for EXECUTION_FAILED state
     */
    public static AuditEntryExpectation EXECUTION_FAILED(String changeId) {
        return AuditEntryExpectation.auditEntry()
            .withTaskId(changeId)
            .withState(AuditEntry.Status.FAILED);
    }
    
    /**
     * Creates an expectation for a ROLLED_BACK audit entry.
     * 
     * @param changeId the change ID that should have a ROLLED_BACK audit entry
     * @return expectation for ROLLED_BACK state
     */
    public static AuditEntryExpectation ROLLED_BACK(String changeId) {
        return AuditEntryExpectation.auditEntry()
            .withTaskId(changeId)
            .withState(AuditEntry.Status.ROLLED_BACK);
    }
    
    /**
     * Creates an expectation for a ROLLBACK_FAILED audit entry.
     * 
     * @param changeId the change ID that should have a ROLLBACK_FAILED audit entry
     * @return expectation for ROLLBACK_FAILED state
     */
    public static AuditEntryExpectation ROLLBACK_FAILED(String changeId) {
        return AuditEntryExpectation.auditEntry()
            .withTaskId(changeId)
            .withState(AuditEntry.Status.ROLLBACK_FAILED);
    }
    
    /**
     * Creates a custom expectation with specific state.
     * 
     * <p>This method allows for more flexible expectations when the standard
     * factory methods don't cover the specific test scenario.</p>
     * 
     * @param changeId the change ID for the expected audit entry
     * @param expectedState the expected audit entry state
     * @return expectation for the specified state
     */
    public static AuditEntryExpectation withState(String changeId, AuditEntry.Status expectedState) {
        return AuditEntryExpectation.auditEntry()
            .withTaskId(changeId)
            .withState(expectedState);
    }
}