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
package io.flamingock.internal.core.builder;

import io.flamingock.api.annotations.Apply;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;

import java.time.LocalDateTime;

/**
 * Builder pattern for creating expected audit entry values in tests.
 * Relocated into flamingock-test-support module.
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

    // Flags for optional field verification
    private boolean shouldVerifyExecutionId = false;
    private boolean shouldVerifyStageId = false;
    private boolean shouldVerifyTimestamp = false;
    private boolean shouldVerifyExecutionMillis = false;
    private boolean shouldVerifyExecutionHostname = false;

    AuditEntryExpectation() {}

    public static AuditEntryExpectation auditEntry() {
        return new AuditEntryExpectation();
    }

    public static AuditEntryExpectation STARTED(String taskId) {
        return new AuditEntryExpectation().withTaskId(taskId).withState(AuditEntry.Status.STARTED);
    }

    public static AuditEntryExpectation APPLIED(String taskId) {
        return new AuditEntryExpectation().withTaskId(taskId).withState(AuditEntry.Status.APPLIED);
    }

    public static AuditEntryExpectation FAILED(String taskId) {
        return new AuditEntryExpectation().withTaskId(taskId).withState(AuditEntry.Status.FAILED);
    }

    public static AuditEntryExpectation ROLLED_BACK(String taskId) {
        return new AuditEntryExpectation().withTaskId(taskId).withState(AuditEntry.Status.ROLLED_BACK);
    }

    public static AuditEntryExpectation ROLLBACK_FAILED(String taskId) {
        return new AuditEntryExpectation().withTaskId(taskId).withState(AuditEntry.Status.ROLLBACK_FAILED);
    }

    @Deprecated
    public static AuditEntryExpectation started(String changeId) {
        return STARTED(changeId);
    }

    @Deprecated
    public static AuditEntryExpectation applied(String changeId) {
        return APPLIED(changeId);
    }

    @Deprecated
    public static AuditEntryExpectation failed(String changeId) {
        return FAILED(changeId);
    }

    @Deprecated
    public static AuditEntryExpectation rolledBack(String changeId) {
        return ROLLED_BACK(changeId);
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
     * Sets both className and methodName from a change class.
     * Finds method annotated with @Apply; if missing, throws RuntimeException.
     */
    public AuditEntryExpectation withClass(Class<?> clazz) {
        this.expectedClassName = clazz.getName();

        java.lang.reflect.Method[] methods = clazz.getDeclaredMethods();
        for (java.lang.reflect.Method method : methods) {
            if (method.isAnnotationPresent(Apply.class)) {
                this.expectedMethodName = method.getName();
                return this;
            }
        }

        throw new RuntimeException(String.format("Class[%s] should contain a method annotated with @Apply", expectedClassName));
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
    public AuditEntryExpectation withTxType(AuditTxType txStrategy) {
        this.expectedTxType = txStrategy;
        return this;
    }

    public AuditEntryExpectation withTargetSystemId(String targetSystemId) {
        this.expectedTargetSystemId = targetSystemId;
        return this;
    }

    // Getters for verification code
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
