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

import java.util.Objects;

/**
 * Represents expected audit entry attributes for testing purposes.
 * 
 * <p>This class encapsulates the expected values for audit entries in tests,
 * providing a clean and extensible way to verify audit sequences. It currently
 * supports changeId and state verification, but can be easily extended with
 * additional fields like author, execution time, etc.</p>
 * 
 * <p><strong>Usage:</strong></p>
 * <pre>{@code
 * auditHelper.verifyAuditSequence(
 *     AuditExpectation.started("change-1"),
 *     AuditExpectation.executed("change-1"),
 *     AuditExpectation.failed("change-2"),
 *     AuditExpectation.rolledBack("change-2")
 * );
 * }</pre>
 */
public class AuditExpectation {
    
    private final String changeId;
    private final AuditEntry.Status state;
    
    /**
     * Creates a new audit expectation.
     * 
     * @param changeId the expected change ID
     * @param state the expected audit state
     */
    public AuditExpectation(String changeId, AuditEntry.Status state) {
        this.changeId = Objects.requireNonNull(changeId, "changeId cannot be null");
        this.state = Objects.requireNonNull(state, "state cannot be null");
    }
    
    /**
     * Creates an expectation for a STARTED audit entry.
     * 
     * @param changeId the change ID
     * @return audit expectation for STARTED state
     */
    public static AuditExpectation STARTED(String changeId) {
        return new AuditExpectation(changeId, AuditEntry.Status.STARTED);
    }
    
    /**
     * Creates an expectation for an EXECUTED audit entry.
     * 
     * @param changeId the change ID
     * @return audit expectation for EXECUTED state
     */
    public static AuditExpectation EXECUTED(String changeId) {
        return new AuditExpectation(changeId, AuditEntry.Status.EXECUTED);
    }
    
    /**
     * Creates an expectation for an EXECUTION_FAILED audit entry.
     * 
     * @param changeId the change ID
     * @return audit expectation for EXECUTION_FAILED state
     */
    public static AuditExpectation EXECUTION_FAILED(String changeId) {
        return new AuditExpectation(changeId, AuditEntry.Status.EXECUTION_FAILED);
    }
    
    /**
     * Creates an expectation for a ROLLED_BACK audit entry.
     * 
     * @param changeId the change ID
     * @return audit expectation for ROLLED_BACK state
     */
    public static AuditExpectation ROLLED_BACK(String changeId) {
        return new AuditExpectation(changeId, AuditEntry.Status.ROLLED_BACK);
    }

    /**
     * Creates an expectation for a ROLLBACK_FAILED audit entry.
     *
     * @param changeId the change ID
     * @return audit expectation for ROLLBACK_FAILED state
     */
    public static AuditExpectation ROLLBACK_FAILED(String changeId) {
        return new AuditExpectation(changeId, AuditEntry.Status.ROLLBACK_FAILED);
    }
    
    /**
     * Gets the expected change ID.
     * 
     * @return the change ID
     */
    public String getChangeId() {
        return changeId;
    }
    
    /**
     * Gets the expected audit state.
     * 
     * @return the audit state
     */
    public AuditEntry.Status getState() {
        return state;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuditExpectation)) return false;
        AuditExpectation that = (AuditExpectation) o;
        return Objects.equals(changeId, that.changeId) && state == that.state;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(changeId, state);
    }
    
    @Override
    public String toString() {
        return "AuditExpectation{changeId='" + changeId + "', state=" + state + "}";
    }
}