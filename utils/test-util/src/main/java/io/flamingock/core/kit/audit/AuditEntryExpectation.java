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

/**
 * Builder pattern for creating expected audit entry values in tests.
 * Provides a fluent API for specifying expected audit field values and makes
 * tests more readable and maintainable.
 * 
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * AuditEntryExpectation expected = auditEntry()
 *     .withTaskId("test-change-id")
 *     .withState(EXECUTED)
 *     .withTxType(TX_SHARED)
 *     .withTargetSystemId("custom-target")
 *     .withClassName("TestChangeClass")
 *     .withSystemChange(false);
 * 
 * AuditEntryAssertions.assertAuditEntry(actualEntry, expected);
 * }</pre>
 */
public class AuditEntryExpectation {
    
    private String expectedExecutionId;
    private String expectedStageId;
    private String expectedTaskId;
    private String expectedAuthor;
    private LocalDateTime expectedCreatedAt;
    private AuditEntry.Status expectedState;
    private AuditEntry.ExecutionType expectedType;
    private String expectedClassName;
    private String expectedMethodName;
    private Object expectedMetadata;
    private Long expectedExecutionMillis;
    private String expectedExecutionHostname;
    private String expectedErrorTrace;
    private Boolean expectedSystemChange;
    private AuditTxType expectedTxType;
    private String expectedTargetSystemId;
    
    // Time range for flexible timestamp verification
    private LocalDateTime timestampAfter;
    private LocalDateTime timestampBefore;
    
    // Flags for optional field verification - only verify if explicitly set
    private boolean shouldVerifyExecutionId = false;
    private boolean shouldVerifyStageId = false;
    private boolean shouldVerifyTimestamp = false;
    private boolean shouldVerifyExecutionMillis = false;
    private boolean shouldVerifyExecutionHostname = false;
    
    private AuditEntryExpectation() {}
    
    /**
     * Creates a new audit entry expectation builder.
     * @return new AuditEntryExpectation instance
     */
    public static AuditEntryExpectation auditEntry() {
        return new AuditEntryExpectation();
    }
    
    /**
     * Creates an expectation for a STARTED audit entry.
     * 
     * @param taskId the task ID
     * @return audit expectation for STARTED state
     */
    public static AuditEntryExpectation STARTED(String taskId) {
        return new AuditEntryExpectation().withTaskId(taskId).withState(AuditEntry.Status.STARTED);
    }
    
    /**
     * Creates an expectation for an EXECUTED audit entry.
     * 
     * @param taskId the task ID
     * @return audit expectation for EXECUTED state
     */
    public static AuditEntryExpectation EXECUTED(String taskId) {
        return new AuditEntryExpectation().withTaskId(taskId).withState(AuditEntry.Status.EXECUTED);
    }
    
    /**
     * Creates an expectation for an EXECUTION_FAILED audit entry.
     * 
     * @param taskId the task ID
     * @return audit expectation for EXECUTION_FAILED state
     */
    public static AuditEntryExpectation EXECUTION_FAILED(String taskId) {
        return new AuditEntryExpectation().withTaskId(taskId).withState(AuditEntry.Status.EXECUTION_FAILED);
    }
    
    /**
     * Creates an expectation for a ROLLED_BACK audit entry.
     * 
     * @param taskId the task ID
     * @return audit expectation for ROLLED_BACK state
     */
    public static AuditEntryExpectation ROLLED_BACK(String taskId) {
        return new AuditEntryExpectation().withTaskId(taskId).withState(AuditEntry.Status.ROLLED_BACK);
    }

    /**
     * Creates an expectation for a ROLLBACK_FAILED audit entry.
     *
     * @param taskId the task ID
     * @return audit expectation for ROLLBACK_FAILED state
     */
    public static AuditEntryExpectation ROLLBACK_FAILED(String taskId) {
        return new AuditEntryExpectation().withTaskId(taskId).withState(AuditEntry.Status.ROLLBACK_FAILED);
    }
    
    // Backward compatibility methods matching old AuditExpectation class
    
    /**
     * @deprecated Use STARTED(String) instead. This method uses changeId for backward compatibility.
     * @param changeId the change ID for backward compatibility
     * @return audit expectation for STARTED state
     */
    @Deprecated
    public static AuditEntryExpectation started(String changeId) {
        return STARTED(changeId);
    }
    
    /**
     * @deprecated Use EXECUTED(String) instead. This method uses changeId for backward compatibility.
     * @param changeId the change ID for backward compatibility
     * @return audit expectation for EXECUTED state
     */
    @Deprecated 
    public static AuditEntryExpectation executed(String changeId) {
        return EXECUTED(changeId);
    }
    
    /**
     * @deprecated Use EXECUTION_FAILED(String) instead. This method uses changeId for backward compatibility.
     * @param changeId the change ID for backward compatibility
     * @return audit expectation for EXECUTION_FAILED state
     */
    @Deprecated
    public static AuditEntryExpectation failed(String changeId) {
        return EXECUTION_FAILED(changeId);
    }
    
    /**
     * @deprecated Use ROLLED_BACK(String) instead. This method uses changeId for backward compatibility.
     * @param changeId the change ID for backward compatibility
     * @return audit expectation for ROLLED_BACK state
     */
    @Deprecated
    public static AuditEntryExpectation rolledBack(String changeId) {
        return ROLLED_BACK(changeId);
    }
    
    /**
     * Gets the expected change ID (alias for task ID for backward compatibility).
     * 
     * @return the task ID
     */
    public String getChangeId() {
        return expectedTaskId;
    }
    
    // Identity fields
    public AuditEntryExpectation withExecutionId(String executionId) {
        this.expectedExecutionId = executionId;
        this.shouldVerifyExecutionId = true;
        return this;
    }
    
    public AuditEntryExpectation withStageId(String stageId) {
        this.expectedStageId = stageId;
        this.shouldVerifyStageId = true;
        return this;
    }
    
    public AuditEntryExpectation withTaskId(String taskId) {
        this.expectedTaskId = taskId;
        return this;
    }
    
    // Metadata fields
    public AuditEntryExpectation withAuthor(String author) {
        this.expectedAuthor = author;
        return this;
    }
    
    public AuditEntryExpectation withCreatedAt(LocalDateTime createdAt) {
        this.expectedCreatedAt = createdAt;
        this.shouldVerifyTimestamp = true;
        return this;
    }
    
    public AuditEntryExpectation withTimestampBetween(LocalDateTime after, LocalDateTime before) {
        this.timestampAfter = after;
        this.timestampBefore = before;
        this.shouldVerifyTimestamp = true;
        return this;
    }
    
    // State fields
    public AuditEntryExpectation withState(AuditEntry.Status state) {
        this.expectedState = state;
        return this;
    }
    
    public AuditEntryExpectation withType(AuditEntry.ExecutionType type) {
        this.expectedType = type;
        return this;
    }
    
    // Execution fields
    public AuditEntryExpectation withClassName(String className) {
        this.expectedClassName = className;
        return this;
    }
    
    public AuditEntryExpectation withMethodName(String methodName) {
        this.expectedMethodName = methodName;
        return this;
    }
    
    /**
     * Sets both className and methodName from a change unit class.
     * Extracts the className from the class itself and finds the method annotated with @Execution.
     * 
     * @param clazz the change unit class
     * @return this expectation for method chaining
     */
    public AuditEntryExpectation withClass(Class<?> clazz) {
        this.expectedClassName = clazz.getName();
        
        // Find the method annotated with @Execution
        java.lang.reflect.Method[] methods = clazz.getDeclaredMethods();
        for (java.lang.reflect.Method method : methods) {
            if (method.isAnnotationPresent(io.flamingock.api.annotations.Execution.class)) {
                this.expectedMethodName = method.getName();
                return this;
            }
        }
        
        throw new RuntimeException(String.format("Class[%s] should contain a method annotated with @Execution", expectedClassName));
    }
    
    public AuditEntryExpectation withMetadata(Object metadata) {
        this.expectedMetadata = metadata;
        return this;
    }
    
    // Performance fields
    public AuditEntryExpectation withExecutionMillis(Long executionMillis) {
        this.expectedExecutionMillis = executionMillis;
        this.shouldVerifyExecutionMillis = true;
        return this;
    }
    
    public AuditEntryExpectation withExecutionHostname(String hostname) {
        this.expectedExecutionHostname = hostname;
        this.shouldVerifyExecutionHostname = true;
        return this;
    }
    
    // Error fields
    public AuditEntryExpectation withErrorTrace(String errorTrace) {
        this.expectedErrorTrace = errorTrace;
        return this;
    }
    
    // System fields
    public AuditEntryExpectation withSystemChange(Boolean systemChange) {
        this.expectedSystemChange = systemChange;
        return this;
    }
    
    // Transaction fields
    public AuditEntryExpectation withTxType(AuditTxType txType) {
        this.expectedTxType = txType;
        return this;
    }
    
    public AuditEntryExpectation withTargetSystemId(String targetSystemId) {
        this.expectedTargetSystemId = targetSystemId;
        return this;
    }
    
    // Getters for AuditEntryAssertions
    public String getExpectedExecutionId() { return expectedExecutionId; }
    public String getExpectedStageId() { return expectedStageId; }
    public String getExpectedTaskId() { return expectedTaskId; }
    public String getExpectedAuthor() { return expectedAuthor; }
    public LocalDateTime getExpectedCreatedAt() { return expectedCreatedAt; }
    public AuditEntry.Status getExpectedState() { return expectedState; }
    public AuditEntry.ExecutionType getExpectedType() { return expectedType; }
    public String getExpectedClassName() { return expectedClassName; }
    public String getExpectedMethodName() { return expectedMethodName; }
    public Object getExpectedMetadata() { return expectedMetadata; }
    public Long getExpectedExecutionMillis() { return expectedExecutionMillis; }
    public String getExpectedExecutionHostname() { return expectedExecutionHostname; }
    public String getExpectedErrorTrace() { return expectedErrorTrace; }
    public Boolean getExpectedSystemChange() { return expectedSystemChange; }
    public AuditTxType getExpectedTxType() { return expectedTxType; }
    public String getExpectedTargetSystemId() { return expectedTargetSystemId; }
    
    public LocalDateTime getTimestampAfter() { return timestampAfter; }
    public LocalDateTime getTimestampBefore() { return timestampBefore; }
    
    public boolean shouldVerifyExecutionId() { return shouldVerifyExecutionId; }
    public boolean shouldVerifyStageId() { return shouldVerifyStageId; }
    public boolean shouldVerifyTimestamp() { return shouldVerifyTimestamp; }
    public boolean shouldVerifyExecutionMillis() { return shouldVerifyExecutionMillis; }
    public boolean shouldVerifyExecutionHostname() { return shouldVerifyExecutionHostname; }
}