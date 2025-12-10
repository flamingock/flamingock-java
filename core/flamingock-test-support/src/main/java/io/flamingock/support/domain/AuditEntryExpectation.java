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
import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.Rollback;
import io.flamingock.api.annotations.TargetSystem;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.support.stages.ThenStage;
import io.flamingock.support.stages.WhenStage;
import io.flamingock.support.validation.error.FieldMismatchError;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static io.flamingock.internal.common.core.audit.AuditEntry.Status.APPLIED;
import static io.flamingock.internal.common.core.audit.AuditEntry.Status.FAILED;
import static io.flamingock.internal.common.core.audit.AuditEntry.Status.ROLLBACK_FAILED;
import static io.flamingock.internal.common.core.audit.AuditEntry.Status.ROLLED_BACK;

/**
 * Builder for defining expected audit entry values in tests.
 *
 * <p>This class provides a fluent API for specifying the expected values of an audit entry
 * that should be created during change execution. It supports selective field verification,
 * meaning only fields with non-null expected values will be validated.</p>
 *
 * <h2>Basic Usage</h2>
 * <p>Create expectations using the static factory methods and optionally chain field specifications:</p>
 * <pre>{@code
 * // Simple expectation - only verifies change ID and status
 * AuditEntryExpectation.APPLIED("my-change-id")
 *
 * // Class-based expectation - auto-extracts metadata from annotations
 * AuditEntryExpectation.APPLIED(MyChange.class)
 *
 * // Detailed expectation with overrides
 * AuditEntryExpectation.APPLIED(MyChange.class)
 *     .withAuthor("custom-author")
 *     .withTargetSystemId("mongodb-main")
 * }</pre>
 *
 * <h2>Factory Methods</h2>
 * <p>Two variants are available for each status:</p>
 *
 * <h3>String-based (manual configuration)</h3>
 * <ul>
 *   <li>{@link #APPLIED(String)} - Expect a successfully applied change</li>
 *   <li>{@link #FAILED(String)} - Expect a failed change</li>
 *   <li>{@link #ROLLED_BACK(String)} - Expect a rolled-back change</li>
 *   <li>{@link #ROLLBACK_FAILED(String)} - Expect a change whose rollback failed</li>
 * </ul>
 *
 * <h3>Class-based (auto-extraction from annotations)</h3>
 * <ul>
 *   <li>{@link #APPLIED(Class)} - Extracts changeId, author, className, methodName from annotations</li>
 *   <li>{@link #FAILED(Class)} - Same extraction for failed changes</li>
 *   <li>{@link #ROLLED_BACK(Class)} - Extracts rollback method name from {@code @Rollback}</li>
 *   <li>{@link #ROLLBACK_FAILED(Class)} - Same extraction for failed rollbacks</li>
 * </ul>
 *
 * <h2>Selective Field Verification</h2>
 * <p>A field is verified only if its expected value is non-null:</p>
 * <ul>
 *   <li><b>Always verified:</b> change ID, status (required by factory methods)</li>
 *   <li><b>Verified when non-null:</b> author, class name, method name, metadata,
 *       error trace, target system ID, execution ID, stage ID, timestamp, etc.</li>
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
    private String expectedClassName;
    private String expectedMethodName;
    private Object expectedMetadata;
    private Long expectedExecutionMillis;
    private String expectedExecutionHostname;
    private String expectedErrorTrace;
    private String expectedTargetSystemId;

    // Time range for flexible timestamp verification
    private LocalDateTime timestampAfter;
    private LocalDateTime timestampBefore;

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
     * @param expectedChangeId the unique identifier of the expected change
     * @return a new expectation builder for further configuration
     */
    public static AuditEntryExpectation FAILED(String expectedChangeId) {
        return new AuditEntryExpectation(expectedChangeId, FAILED);
    }

    /**
     * Creates an expectation for a rolled-back change.
     *
     * <p>Use this when the change is expected to have been rolled back successfully
     * and be recorded with {@code ROLLED_BACK} status in the audit store.</p>
     *
     * @param expectedChangeId the unique identifier of the expected change
     * @return a new expectation builder for further configuration
     */
    public static AuditEntryExpectation ROLLED_BACK(String expectedChangeId) {
        return new AuditEntryExpectation(expectedChangeId, ROLLED_BACK);
    }

    /**
     * Creates an expectation for a change whose rollback failed.
     *
     * <p>Use this when the change's rollback operation is expected to fail
     * and be recorded with {@code ROLLBACK_FAILED} status in the audit store.</p>
     *
     * @param expectedChangeId the unique identifier of the expected change
     * @return a new expectation builder for further configuration
     */
    public static AuditEntryExpectation ROLLBACK_FAILED(String expectedChangeId) {
        return new AuditEntryExpectation(expectedChangeId, ROLLBACK_FAILED);
    }

    /**
     * Creates an expectation for a successfully applied change by extracting
     * metadata from the change class annotations.
     *
     * <p>This factory method uses reflection to extract:</p>
     * <ul>
     *   <li>Change ID and author from {@code @Change} annotation</li>
     *   <li>Class name from the class itself</li>
     *   <li>Method name from the method annotated with {@code @Apply}</li>
     *   <li>Target system ID from {@code @TargetSystem} annotation (if present)</li>
     * </ul>
     *
     * <p>Values can be overridden after creation using {@code withXxx()} methods.</p>
     *
     * @param changeClass the change class annotated with {@code @Change}
     * @return a new expectation builder pre-populated with annotation values
     * @throws IllegalArgumentException if the class is not annotated with {@code @Change}
     *         or does not contain a method annotated with {@code @Apply}
     */
    public static AuditEntryExpectation APPLIED(Class<?> changeClass) {
        return fromChangeClass(changeClass, APPLIED);
    }

    /**
     * Creates an expectation for a failed change by extracting
     * metadata from the change class annotations.
     *
     * <p>This factory method uses reflection to extract:</p>
     * <ul>
     *   <li>Change ID and author from {@code @Change} annotation</li>
     *   <li>Class name from the class itself</li>
     *   <li>Method name from the method annotated with {@code @Apply}</li>
     *   <li>Target system ID from {@code @TargetSystem} annotation (if present)</li>
     * </ul>
     *
     * <p>Values can be overridden after creation using {@code withXxx()} methods.</p>
     *
     * @param changeClass the change class annotated with {@code @Change}
     * @return a new expectation builder pre-populated with annotation values
     * @throws IllegalArgumentException if the class is not annotated with {@code @Change}
     *         or does not contain a method annotated with {@code @Apply}
     */
    public static AuditEntryExpectation FAILED(Class<?> changeClass) {
        return fromChangeClass(changeClass, FAILED);
    }

    /**
     * Creates an expectation for a rolled-back change by extracting
     * metadata from the change class annotations.
     *
     * <p>This factory method uses reflection to extract:</p>
     * <ul>
     *   <li>Change ID and author from {@code @Change} annotation</li>
     *   <li>Class name from the class itself</li>
     *   <li>Method name from the method annotated with {@code @Rollback}</li>
     *   <li>Target system ID from {@code @TargetSystem} annotation (if present)</li>
     * </ul>
     *
     * <p>Values can be overridden after creation using {@code withXxx()} methods.</p>
     *
     * @param changeClass the change class annotated with {@code @Change}
     * @return a new expectation builder pre-populated with annotation values
     * @throws IllegalArgumentException if the class is not annotated with {@code @Change}
     *         or does not contain a method annotated with {@code @Rollback}
     */
    public static AuditEntryExpectation ROLLED_BACK(Class<?> changeClass) {
        return fromChangeClass(changeClass, ROLLED_BACK);
    }

    /**
     * Creates an expectation for a change whose rollback failed by extracting
     * metadata from the change class annotations.
     *
     * <p>This factory method uses reflection to extract:</p>
     * <ul>
     *   <li>Change ID and author from {@code @Change} annotation</li>
     *   <li>Class name from the class itself</li>
     *   <li>Method name from the method annotated with {@code @Rollback}</li>
     *   <li>Target system ID from {@code @TargetSystem} annotation (if present)</li>
     * </ul>
     *
     * <p>Values can be overridden after creation using {@code withXxx()} methods.</p>
     *
     * @param changeClass the change class annotated with {@code @Change}
     * @return a new expectation builder pre-populated with annotation values
     * @throws IllegalArgumentException if the class is not annotated with {@code @Change}
     *         or does not contain a method annotated with {@code @Rollback}
     */
    public static AuditEntryExpectation ROLLBACK_FAILED(Class<?> changeClass) {
        return fromChangeClass(changeClass, ROLLBACK_FAILED);
    }

    private static AuditEntryExpectation fromChangeClass(Class<?> changeClass, AuditEntry.Status status) {
        Change changeAnnotation = changeClass.getAnnotation(Change.class);
        if (changeAnnotation == null) {
            throw new IllegalArgumentException(
                    String.format("Class [%s] must be annotated with @Change", changeClass.getName()));
        }

        AuditEntryExpectation expectation = new AuditEntryExpectation(
                changeAnnotation.id(),
                status
        );

        expectation.expectedAuthor = changeAnnotation.author();
        expectation.expectedClassName = changeClass.getName();
        expectation.expectedMethodName = findMethodName(changeClass, status);

        TargetSystem targetSystem = changeClass.getAnnotation(TargetSystem.class);
        if (targetSystem != null) {
            expectation.expectedTargetSystemId = targetSystem.id();
        }

        return expectation;
    }

    private static String findMethodName(Class<?> changeClass, AuditEntry.Status status) {
        Class<? extends Annotation> annotationClass =
                (status == APPLIED || status == FAILED) ? Apply.class : Rollback.class;

        for (Method method : changeClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(annotationClass)) {
                return method.getName();
            }
        }

        throw new IllegalArgumentException(String.format(
                "Class [%s] must contain a method annotated with @%s",
                changeClass.getName(),
                annotationClass.getSimpleName()));
    }

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
        return this;
    }

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
        return this;
    }

    /**
     * Sets the expected fully-qualified class name.
     *
     * <p>This is useful for overriding the class name after using a class-based
     * factory method, or when using string-based factory methods.</p>
     *
     * @param className the expected class name
     * @return this builder for method chaining
     */
    public AuditEntryExpectation withClassName(String className) {
        this.expectedClassName = className;
        return this;
    }

    /**
     * Sets the expected method name.
     *
     * <p>This is useful for overriding the method name after using a class-based
     * factory method, or when using string-based factory methods.</p>
     *
     * @param methodName the expected method name
     * @return this builder for method chaining
     */
    public AuditEntryExpectation withMethodName(String methodName) {
        this.expectedMethodName = methodName;
        return this;
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
        return this;
    }

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

    /**
     * Compares this expectation against an actual audit entry.
     *
     * <p>Returns a list of field mismatches (empty if all expected fields match).
     * Only fields with non-null expected values are verified, except for
     * {@code changeId} and {@code status} which are always verified.</p>
     *
     * <p>Timestamp verification supports two modes:</p>
     * <ul>
     *   <li>Exact match: when {@code expectedCreatedAt} is set</li>
     *   <li>Range match: when {@code timestampAfter} and/or {@code timestampBefore} are set</li>
     * </ul>
     *
     * @param actual the actual audit entry to compare against
     * @return list of field mismatch errors (empty if all match)
     */
    public List<FieldMismatchError> compareWith(AuditEntry actual) {
        List<FieldMismatchError> errors = new ArrayList<>();

        // Required fields - always verified
        if (!expectedChangeId.equals(actual.getTaskId())) {
            errors.add(new FieldMismatchError("changeId", expectedChangeId, actual.getTaskId()));
        }

        if (expectedState != actual.getState()) {
            errors.add(new FieldMismatchError("status",
                    expectedState.name(),
                    actual.getState() != null ? actual.getState().name() : null));
        }

        // Optional fields - verified when non-null
        if (expectedExecutionId != null && !expectedExecutionId.equals(actual.getExecutionId())) {
            errors.add(new FieldMismatchError("executionId", expectedExecutionId, actual.getExecutionId()));
        }

        if (expectedStageId != null && !expectedStageId.equals(actual.getStageId())) {
            errors.add(new FieldMismatchError("stageId", expectedStageId, actual.getStageId()));
        }

        if (expectedAuthor != null && !expectedAuthor.equals(actual.getAuthor())) {
            errors.add(new FieldMismatchError("author", expectedAuthor, actual.getAuthor()));
        }

        if (expectedClassName != null && !expectedClassName.equals(actual.getClassName())) {
            errors.add(new FieldMismatchError("className", expectedClassName, actual.getClassName()));
        }

        if (expectedMethodName != null && !expectedMethodName.equals(actual.getMethodName())) {
            errors.add(new FieldMismatchError("methodName", expectedMethodName, actual.getMethodName()));
        }

        if (expectedMetadata != null && !Objects.equals(expectedMetadata, actual.getMetadata())) {
            errors.add(new FieldMismatchError("metadata",
                    String.valueOf(expectedMetadata),
                    String.valueOf(actual.getMetadata())));
        }

        if (expectedExecutionMillis != null && expectedExecutionMillis != actual.getExecutionMillis()) {
            errors.add(new FieldMismatchError("executionMillis",
                    String.valueOf(expectedExecutionMillis),
                    String.valueOf(actual.getExecutionMillis())));
        }

        if (expectedExecutionHostname != null && !expectedExecutionHostname.equals(actual.getExecutionHostname())) {
            errors.add(new FieldMismatchError("executionHostname", expectedExecutionHostname, actual.getExecutionHostname()));
        }

        if (expectedErrorTrace != null && !expectedErrorTrace.equals(actual.getErrorTrace())) {
            errors.add(new FieldMismatchError("errorTrace", expectedErrorTrace, actual.getErrorTrace()));
        }

        if (expectedTargetSystemId != null && !expectedTargetSystemId.equals(actual.getTargetSystemId())) {
            errors.add(new FieldMismatchError("targetSystemId", expectedTargetSystemId, actual.getTargetSystemId()));
        }

        errors.addAll(compareTimestamp(actual));

        return errors;
    }

    private List<FieldMismatchError> compareTimestamp(AuditEntry actual) {
        List<FieldMismatchError> errors = new ArrayList<>();
        if (expectedCreatedAt != null) {
            // Exact match mode
            if (!expectedCreatedAt.equals(actual.getCreatedAt())) {
                errors.add(new FieldMismatchError("createdAt",
                        expectedCreatedAt.toString(),
                        actual.getCreatedAt() != null ? actual.getCreatedAt().toString() : null));
            }
        } else if (timestampAfter != null || timestampBefore != null) {
            // Range match mode
            LocalDateTime actualTimestamp = actual.getCreatedAt();
            if (actualTimestamp == null) {
                errors.add(new FieldMismatchError("createdAt",
                        formatTimestampRange(),
                        null));
            } else {
                boolean afterOk = timestampAfter == null ||
                        actualTimestamp.isAfter(timestampAfter) ||
                        actualTimestamp.isEqual(timestampAfter);
                boolean beforeOk = timestampBefore == null ||
                        actualTimestamp.isBefore(timestampBefore) ||
                        actualTimestamp.isEqual(timestampBefore);

                if (!afterOk || !beforeOk) {
                    errors.add(new FieldMismatchError("createdAt",
                            formatTimestampRange(),
                            actualTimestamp.toString()));
                }
            }
        }
        return errors;
    }

    private String formatTimestampRange() {
        if (timestampAfter != null && timestampBefore != null) {
            return String.format("between %s and %s", timestampAfter, timestampBefore);
        } else if (timestampAfter != null) {
            return String.format("after %s", timestampAfter);
        } else {
            return String.format("before %s", timestampBefore);
        }
    }

}
