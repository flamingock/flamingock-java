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
package io.flamingock.support.domain;

import io.flamingock.api.annotations.Apply;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.support.stages.ThenStage;
import io.flamingock.support.stages.WhenStage;

import java.time.LocalDateTime;

import static io.flamingock.internal.common.core.audit.AuditEntry.Status.APPLIED;
import static io.flamingock.internal.common.core.audit.AuditEntry.Status.FAILED;
import static io.flamingock.internal.common.core.audit.AuditEntry.Status.ROLLBACK_FAILED;
import static io.flamingock.internal.common.core.audit.AuditEntry.Status.ROLLED_BACK;

/**
 * Builder for defining expected audit entry values in tests.
 *
 * <p>This class provides a fluent API for specifying the expected values of an audit entry
 * that should be created during change execution. It supports selective field verification,
 * meaning only the fields explicitly set via {@code withXxx()} methods will be validated.</p>
 *
 * <h2>Basic Usage</h2>
 * <p>Create expectations using the static factory methods and optionally chain field specifications:</p>
 * <pre>{@code
 * // Simple expectation - only verifies change ID and status
 * AuditEntryExpectation.APPLIED("my-change-id")
 *
 * // Detailed expectation - verifies additional fields
 * AuditEntryExpectation.APPLIED("my-change-id")
 *     .withClass(MyChange.class)
 *     .withAuthor("dev-team")
 *     .withTargetSystemId("mongodb-main")
 * }</pre>
 *
 * <h2>Selective Field Verification</h2>
 * <p>By default, only the change ID and status (specified via factory methods) are verified.
 * Additional fields are only verified when explicitly set:</p>
 * <ul>
 *   <li><b>Always verified:</b> change ID, status</li>
 *   <li><b>Verified when set:</b> author, class name, method name, metadata, error trace,
 *       system change flag, transaction type, target system ID</li>
 *   <li><b>Verified only with flag:</b> execution ID, stage ID, timestamp, execution millis,
 *       execution hostname (these set a verification flag when their {@code withXxx()} is called)</li>
 * </ul>
 *
 * <h2>Factory Methods</h2>
 * <ul>
 *   <li>{@link #APPLIED(String)} - Expect a successfully applied change</li>
 *   <li>{@link #FAILED(String)} - Expect a failed change</li>
 *   <li>{@link #ROLLED_BACK(String)} - Expect a rolled-back change</li>
 *   <li>{@link #ROLLBACK_FAILED(String)} - Expect a change whose rollback failed</li>
 * </ul>
 *
 * @see WhenStage#thenExpectAuditSequenceStrict(AuditEntryExpectation...)
 * @see ThenStage#andExpectAuditSequenceStrict(AuditEntryExpectation...)
 */
public class AuditEntryExpectation {

    private final String expectedChangeId;
    private final AuditEntry.Status expectedState;

    private String expectedExecutionId;
    private String expectedStageId;
    private String expectedAuthor;
    private LocalDateTime expectedCreatedAt;
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

    private AuditEntryExpectation(String expectedChangeId, AuditEntry.Status expectedState) {
        this.expectedChangeId = expectedChangeId;
        this.expectedState = expectedState;
    }

    /**
     * Creates an expectation for a successfully applied change.
     *
     * <p>Use this when the change is expected to execute successfully and be recorded
     * with {@code APPLIED} status in the audit store.</p>
     *
     * @param expectedChangeId the unique identifier of the expected change
     * @return a new expectation builder for further configuration
     */
    public static AuditEntryExpectation APPLIED(String expectedChangeId) {
        return new AuditEntryExpectation(expectedChangeId, APPLIED);
    }

    /**
     * Creates an expectation for a failed change.
     *
     * <p>Use this when the change is expected to fail during execution and be recorded
     * with {@code FAILED} status in the audit store.</p>
     *
     * @param taskId the unique identifier of the expected change
     * @return a new expectation builder for further configuration
     */
    public static AuditEntryExpectation FAILED(String taskId) {
        return new AuditEntryExpectation(taskId, FAILED);
    }

    /**
     * Creates an expectation for a rolled-back change.
     *
     * <p>Use this when the change is expected to have been rolled back successfully
     * and be recorded with {@code ROLLED_BACK} status in the audit store.</p>
     *
     * @param taskId the unique identifier of the expected change
     * @return a new expectation builder for further configuration
     */
    public static AuditEntryExpectation ROLLED_BACK(String taskId) {
        return new AuditEntryExpectation(taskId, ROLLED_BACK);
    }

    /**
     * Creates an expectation for a change whose rollback failed.
     *
     * <p>Use this when the change's rollback operation is expected to fail
     * and be recorded with {@code ROLLBACK_FAILED} status in the audit store.</p>
     *
     * @param taskId the unique identifier of the expected change
     * @return a new expectation builder for further configuration
     */
    public static AuditEntryExpectation ROLLBACK_FAILED(String taskId) {
        return new AuditEntryExpectation(taskId, ROLLBACK_FAILED);
    }

    // ==================== Identity Fields ====================

    /**
     * Sets the expected execution ID for verification.
     *
     * <p>Enables verification of the execution ID field.</p>
     *
     * @param executionId the expected execution identifier
     * @return this builder for method chaining
     */
    public AuditEntryExpectation withExecutionId(String executionId) {
        this.expectedExecutionId = executionId;
        this.shouldVerifyExecutionId = true;
        return this;
    }

    /**
     * Sets the expected stage ID for verification.
     *
     * <p>Enables verification of the stage ID field.</p>
     *
     * @param stageId the expected stage identifier
     * @return this builder for method chaining
     */
    public AuditEntryExpectation withStageId(String stageId) {
        this.expectedStageId = stageId;
        this.shouldVerifyStageId = true;
        return this;
    }

    // ==================== Metadata Fields ====================

    /**
     * Sets the expected author of the change.
     *
     * @param author the expected author value
     * @return this builder for method chaining
     */
    public AuditEntryExpectation withAuthor(String author) {
        this.expectedAuthor = author;
        return this;
    }

    /**
     * Sets the expected exact timestamp for the audit entry.
     *
     * <p>Enables timestamp verification with exact matching.</p>
     *
     * @param createdAt the expected creation timestamp
     * @return this builder for method chaining
     * @see #withTimestampBetween(LocalDateTime, LocalDateTime)
     */
    public AuditEntryExpectation withCreatedAt(LocalDateTime createdAt) {
        this.expectedCreatedAt = createdAt;
        this.shouldVerifyTimestamp = true;
        return this;
    }

    /**
     * Sets an expected timestamp range for the audit entry.
     *
     * <p>Use this for flexible timestamp verification when the exact time
     * is not predictable but a range can be established.</p>
     *
     * @param after  the timestamp must be after this value (exclusive)
     * @param before the timestamp must be before this value (exclusive)
     * @return this builder for method chaining
     * @see #withCreatedAt(LocalDateTime)
     */
    public AuditEntryExpectation withTimestampBetween(LocalDateTime after, LocalDateTime before) {
        this.timestampAfter = after;
        this.timestampBefore = before;
        this.shouldVerifyTimestamp = true;
        return this;
    }

    /**
     * Sets the expected execution type.
     *
     * @param type the expected execution type
     * @return this builder for method chaining
     */
    public AuditEntryExpectation withType(AuditEntry.ExecutionType type) {
        this.expectedType = type;
        return this;
    }

    // ==================== Execution Fields ====================

    /**
     * Sets the expected fully-qualified class name.
     *
     * @param className the expected class name
     * @return this builder for method chaining
     * @see #withClass(Class)
     */
    public AuditEntryExpectation withClassName(String className) {
        this.expectedClassName = className;
        return this;
    }

    /**
     * Sets the expected method name.
     *
     * @param methodName the expected method name
     * @return this builder for method chaining
     * @see #withClass(Class)
     */
    public AuditEntryExpectation withMethodName(String methodName) {
        this.expectedMethodName = methodName;
        return this;
    }

    /**
     * Sets both class name and method name by inspecting the provided change class.
     *
     * <p>This method uses reflection to find the method annotated with {@code @Apply}
     * and automatically sets both {@code className} and {@code methodName} fields.</p>
     *
     * <p>This is the recommended way to set execution details as it avoids
     * hardcoding string values that may become stale.</p>
     *
     * @param clazz the change class to inspect
     * @return this builder for method chaining
     * @throws RuntimeException if no method annotated with {@code @Apply} is found
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

    /**
     * Sets the expected metadata object.
     *
     * @param metadata the expected metadata
     * @return this builder for method chaining
     */
    public AuditEntryExpectation withMetadata(Object metadata) {
        this.expectedMetadata = metadata;
        return this;
    }

    // ==================== Performance Fields ====================

    /**
     * Sets the expected execution duration in milliseconds.
     *
     * <p>Enables verification of the execution time field.</p>
     *
     * @param executionMillis the expected execution duration
     * @return this builder for method chaining
     */
    public AuditEntryExpectation withExecutionMillis(Long executionMillis) {
        this.expectedExecutionMillis = executionMillis;
        this.shouldVerifyExecutionMillis = true;
        return this;
    }

    /**
     * Sets the expected execution hostname.
     *
     * <p>Enables verification of the hostname field.</p>
     *
     * @param hostname the expected hostname
     * @return this builder for method chaining
     */
    public AuditEntryExpectation withExecutionHostname(String hostname) {
        this.expectedExecutionHostname = hostname;
        this.shouldVerifyExecutionHostname = true;
        return this;
    }

    // ==================== Error Fields ====================

    /**
     * Sets the expected error trace for failed changes.
     *
     * <p>Typically used with {@link #FAILED(String)} or {@link #ROLLBACK_FAILED(String)}
     * expectations to verify error details.</p>
     *
     * @param errorTrace the expected error trace or message
     * @return this builder for method chaining
     */
    public AuditEntryExpectation withErrorTrace(String errorTrace) {
        this.expectedErrorTrace = errorTrace;
        return this;
    }

    // ==================== System Fields ====================

    /**
     * Sets whether this is expected to be a system change.
     *
     * @param systemChange {@code true} if this should be a system change
     * @return this builder for method chaining
     */
    public AuditEntryExpectation withSystemChange(Boolean systemChange) {
        this.expectedSystemChange = systemChange;
        return this;
    }

    // ==================== Transaction Fields ====================

    /**
     * Sets the expected transaction type.
     *
     * @param txStrategy the expected transaction type
     * @return this builder for method chaining
     */
    public AuditEntryExpectation withTxType(AuditTxType txStrategy) {
        this.expectedTxType = txStrategy;
        return this;
    }

    /**
     * Sets the expected target system identifier.
     *
     * @param targetSystemId the expected target system ID
     * @return this builder for method chaining
     */
    public AuditEntryExpectation withTargetSystemId(String targetSystemId) {
        this.expectedTargetSystemId = targetSystemId;
        return this;
    }

    // ==================== Getters (for verification logic) ====================

    /** Returns the expected execution ID. */
    public String getExpectedExecutionId() { return expectedExecutionId; }

    /** Returns the expected stage ID. */
    public String getExpectedStageId() { return expectedStageId; }

    /** Returns the expected change ID. */
    public String getExpectedChangeId() { return expectedChangeId; }

    /** Returns the expected author. */
    public String getExpectedAuthor() { return expectedAuthor; }

    /** Returns the expected creation timestamp. */
    public LocalDateTime getExpectedCreatedAt() { return expectedCreatedAt; }

    /** Returns the expected audit entry status. */
    public AuditEntry.Status getExpectedState() { return expectedState; }

    /** Returns the expected execution type. */
    public AuditEntry.ExecutionType getExpectedType() { return expectedType; }

    /** Returns the expected class name. */
    public String getExpectedClassName() { return expectedClassName; }

    /** Returns the expected method name. */
    public String getExpectedMethodName() { return expectedMethodName; }

    /** Returns the expected metadata. */
    public Object getExpectedMetadata() { return expectedMetadata; }

    /** Returns the expected execution duration in milliseconds. */
    public Long getExpectedExecutionMillis() { return expectedExecutionMillis; }

    /** Returns the expected execution hostname. */
    public String getExpectedExecutionHostname() { return expectedExecutionHostname; }

    /** Returns the expected error trace. */
    public String getExpectedErrorTrace() { return expectedErrorTrace; }

    /** Returns whether this is expected to be a system change. */
    public Boolean getExpectedSystemChange() { return expectedSystemChange; }

    /** Returns the expected transaction type. */
    public AuditTxType getExpectedTxType() { return expectedTxType; }

    /** Returns the expected target system ID. */
    public String getExpectedTargetSystemId() { return expectedTargetSystemId; }

    /** Returns the lower bound for timestamp range verification. */
    public LocalDateTime getTimestampAfter() { return timestampAfter; }

    /** Returns the upper bound for timestamp range verification. */
    public LocalDateTime getTimestampBefore() { return timestampBefore; }

    // ==================== Verification Flags ====================

    /** Returns {@code true} if execution ID should be verified. */
    public boolean shouldVerifyExecutionId() { return shouldVerifyExecutionId; }

    /** Returns {@code true} if stage ID should be verified. */
    public boolean shouldVerifyStageId() { return shouldVerifyStageId; }

    /** Returns {@code true} if timestamp should be verified. */
    public boolean shouldVerifyTimestamp() { return shouldVerifyTimestamp; }

    /** Returns {@code true} if execution millis should be verified. */
    public boolean shouldVerifyExecutionMillis() { return shouldVerifyExecutionMillis; }

    /** Returns {@code true} if execution hostname should be verified. */
    public boolean shouldVerifyExecutionHostname() { return shouldVerifyExecutionHostname; }
}
