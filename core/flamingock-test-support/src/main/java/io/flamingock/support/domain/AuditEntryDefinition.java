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

import io.flamingock.api.RecoveryStrategy;
import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.Recovery;
import io.flamingock.api.annotations.Rollback;
import io.flamingock.api.annotations.TargetSystem;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;

import java.lang.annotation.Annotation;
import java.util.UUID;
import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static io.flamingock.internal.common.core.audit.AuditEntry.Status.*;

/**
 * Defines an audit entry for use in BDD-style tests.
 *
 * <p>This class provides a fluent API for specifying the values of an audit entry.
 * It can be used both as a precondition in the "Given" phase (to define existing audit state)
 * and as an expectation in the "Then" phase (to define expected audit entries).</p>
 *
 * <h2>Basic Usage</h2>
 * <p>Create definitions using the static factory methods and optionally chain field specifications:</p>
 * <pre>{@code
 * // Simple definition - specifies change ID and status
 * AuditEntryDefinition.APPLIED("my-change-id")
 *
 * // Class-based definition - auto-extracts metadata from annotations
 * AuditEntryDefinition.APPLIED(MyChange.class)
 *
 * // Detailed definition with additional fields
 * AuditEntryDefinition.APPLIED(MyChange.class)
 *     .withAuthor("custom-author")
 *     .withTargetSystemId("mongodb-main")
 * }</pre>
 *
 * <h2>Factory Methods</h2>
 * <p>Two variants are available for each status:</p>
 *
 * <h3>String-based (manual configuration)</h3>
 * <ul>
 *   <li>{@link #APPLIED(String)} - Define an applied change</li>
 *   <li>{@link #FAILED(String)} - Define a failed change</li>
 *   <li>{@link #ROLLED_BACK(String)} - Define a rolled-back change</li>
 *   <li>{@link #ROLLBACK_FAILED(String)} - Define a change whose rollback failed</li>
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
 * <h2>Usage in Tests</h2>
 * <pre>{@code
 * FlamingockTestSupport
 *     .given(builder)
 *     .andExistingAudit(
 *         APPLIED(SetupChange.class),
 *         APPLIED(MigrationV1.class)
 *     )
 *     .whenRun()
 *     .thenExpectAuditFinalStateSequence(
 *         APPLIED(NewChange.class)
 *     )
 *     .verify();
 * }</pre>
 */
public class AuditEntryDefinition {

    private static final String ORDER_PATTERN_PREFIX = "_";
    private static final String ORDER_PATTERN_SEPARATOR = "__";

    private final String changeId;
    private final AuditEntry.Status state;

    private String executionId;
    private String stageId;
    private String author;
    private LocalDateTime createdAt;
    private String className;
    private String methodName;
    private Object metadata;
    private Long executionMillis;
    private String executionHostname;
    private String errorTrace;
    private String targetSystemId;
    private RecoveryStrategy recoveryStrategy;
    private String order;
    private Boolean transactional;

    private AuditEntryDefinition(String changeId, AuditEntry.Status state) {
        this.changeId = changeId;
        this.state = state;
    }

    // ========== Static Factory Methods (String-based) ==========

    /**
     * Creates a definition for an applied change.
     *
     * @param changeId the unique identifier of the change
     * @return a new definition builder for further configuration
     */
    public static AuditEntryDefinition APPLIED(String changeId) {
        return new AuditEntryDefinition(changeId, APPLIED);
    }

    /**
     * Creates a definition for a failed change.
     *
     * @param changeId the unique identifier of the change
     * @return a new definition builder for further configuration
     */
    public static AuditEntryDefinition FAILED(String changeId) {
        return new AuditEntryDefinition(changeId, FAILED);
    }

    /**
     * Creates a definition for a rolled-back change.
     *
     * @param changeId the unique identifier of the change
     * @return a new definition builder for further configuration
     */
    public static AuditEntryDefinition ROLLED_BACK(String changeId) {
        return new AuditEntryDefinition(changeId, ROLLED_BACK);
    }

    /**
     * Creates a definition for a change whose rollback failed.
     *
     * @param changeId the unique identifier of the change
     * @return a new definition builder for further configuration
     */
    public static AuditEntryDefinition ROLLBACK_FAILED(String changeId) {
        return new AuditEntryDefinition(changeId, ROLLBACK_FAILED);
    }

    // ========== Static Factory Methods (Class-based) ==========

    /**
     * Creates a definition for an applied change by extracting metadata from annotations.
     *
     * <p>Extracts from the change class:</p>
     * <ul>
     *   <li>Change ID and author from {@code @Change} annotation</li>
     *   <li>Class name from the class itself</li>
     *   <li>Method name from the method annotated with {@code @Apply}</li>
     *   <li>Target system ID from {@code @TargetSystem} annotation (if present)</li>
     *   <li>Recovery strategy from {@code @Recovery} annotation (if present)</li>
     *   <li>Order from class name pattern {@code _ORDER__CHANGE-NAME} (if applicable)</li>
     *   <li>Transactional flag from {@code @Change.transactional()}</li>
     * </ul>
     *
     * @param changeClass the change class annotated with {@code @Change}
     * @return a new definition builder pre-populated with annotation values
     * @throws IllegalArgumentException if the class is not annotated with {@code @Change}
     *         or does not contain a method annotated with {@code @Apply}
     */
    public static AuditEntryDefinition APPLIED(Class<?> changeClass) {
        return fromChangeClass(changeClass, APPLIED);
    }

    /**
     * Creates a definition for a failed change by extracting metadata from annotations.
     *
     * @param changeClass the change class annotated with {@code @Change}
     * @return a new definition builder pre-populated with annotation values
     * @throws IllegalArgumentException if the class is not annotated with {@code @Change}
     *         or does not contain a method annotated with {@code @Apply}
     * @see #APPLIED(Class) for details on extracted fields
     */
    public static AuditEntryDefinition FAILED(Class<?> changeClass) {
        return fromChangeClass(changeClass, FAILED);
    }

    /**
     * Creates a definition for a rolled-back change by extracting metadata from annotations.
     *
     * <p>Similar to {@link #APPLIED(Class)}, but extracts method name from
     * {@code @Rollback} annotation instead of {@code @Apply}.</p>
     *
     * @param changeClass the change class annotated with {@code @Change}
     * @return a new definition builder pre-populated with annotation values
     * @throws IllegalArgumentException if the class is not annotated with {@code @Change}
     *         or does not contain a method annotated with {@code @Rollback}
     */
    public static AuditEntryDefinition ROLLED_BACK(Class<?> changeClass) {
        return fromChangeClass(changeClass, ROLLED_BACK);
    }

    /**
     * Creates a definition for a change whose rollback failed by extracting metadata from annotations.
     *
     * @param changeClass the change class annotated with {@code @Change}
     * @return a new definition builder pre-populated with annotation values
     * @throws IllegalArgumentException if the class is not annotated with {@code @Change}
     *         or does not contain a method annotated with {@code @Rollback}
     * @see #ROLLED_BACK(Class) for details on extracted fields
     */
    public static AuditEntryDefinition ROLLBACK_FAILED(Class<?> changeClass) {
        return fromChangeClass(changeClass, ROLLBACK_FAILED);
    }

    private static AuditEntryDefinition fromChangeClass(Class<?> changeClass, AuditEntry.Status status) {
        Change changeAnnotation = changeClass.getAnnotation(Change.class);
        if (changeAnnotation == null) {
            throw new IllegalArgumentException(
                    String.format("Class [%s] must be annotated with @Change", changeClass.getName()));
        }

        AuditEntryDefinition definition = new AuditEntryDefinition(
                changeAnnotation.id(),
                status
        );

        definition.author = changeAnnotation.author();
        definition.className = changeClass.getName();
        definition.methodName = findMethodName(changeClass, status);

        TargetSystem targetSystem = changeClass.getAnnotation(TargetSystem.class);
        if (targetSystem != null) {
            definition.targetSystemId = targetSystem.id();
        }

        Recovery recovery = changeClass.getAnnotation(Recovery.class);
        definition.recoveryStrategy = (recovery != null)
                ? recovery.strategy()
                : RecoveryStrategy.MANUAL_INTERVENTION;

        definition.order = extractOrderFromClassName(changeClass.getSimpleName());

        definition.transactional = changeAnnotation.transactional();

        return definition;
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

    private static String extractOrderFromClassName(String className) {
        if (className == null || !className.startsWith(ORDER_PATTERN_PREFIX)) {
            return null;
        }
        int separatorIndex = className.indexOf(ORDER_PATTERN_SEPARATOR);
        if (separatorIndex <= 1) {
            return null;
        }
        return className.substring(1, separatorIndex);
    }

    // ========== Fluent Builder Methods ==========

    /**
     * Sets the execution ID.
     *
     * @param executionId the execution identifier
     * @return this builder for method chaining
     */
    public AuditEntryDefinition withExecutionId(String executionId) {
        this.executionId = executionId;
        return this;
    }

    /**
     * Sets the stage ID.
     *
     * @param stageId the stage identifier
     * @return this builder for method chaining
     */
    public AuditEntryDefinition withStageId(String stageId) {
        this.stageId = stageId;
        return this;
    }

    /**
     * Sets the author of the change.
     *
     * @param author the author value
     * @return this builder for method chaining
     */
    public AuditEntryDefinition withAuthor(String author) {
        this.author = author;
        return this;
    }

    /**
     * Sets the creation timestamp.
     *
     * @param createdAt the creation timestamp
     * @return this builder for method chaining
     */
    public AuditEntryDefinition withCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    /**
     * Sets the fully-qualified class name.
     *
     * @param className the class name
     * @return this builder for method chaining
     */
    public AuditEntryDefinition withClassName(String className) {
        this.className = className;
        return this;
    }

    /**
     * Sets the method name.
     *
     * @param methodName the method name
     * @return this builder for method chaining
     */
    public AuditEntryDefinition withMethodName(String methodName) {
        this.methodName = methodName;
        return this;
    }

    /**
     * Sets the metadata object.
     *
     * @param metadata the metadata
     * @return this builder for method chaining
     */
    public AuditEntryDefinition withMetadata(Object metadata) {
        this.metadata = metadata;
        return this;
    }

    /**
     * Sets the execution duration in milliseconds.
     *
     * @param executionMillis the execution duration
     * @return this builder for method chaining
     */
    public AuditEntryDefinition withExecutionMillis(Long executionMillis) {
        this.executionMillis = executionMillis;
        return this;
    }

    /**
     * Sets the execution hostname.
     *
     * @param hostname the hostname
     * @return this builder for method chaining
     */
    public AuditEntryDefinition withExecutionHostname(String hostname) {
        this.executionHostname = hostname;
        return this;
    }

    /**
     * Sets the error trace for failed changes.
     *
     * @param errorTrace the error trace or message
     * @return this builder for method chaining
     */
    public AuditEntryDefinition withErrorTrace(String errorTrace) {
        this.errorTrace = errorTrace;
        return this;
    }

    /**
     * Sets the target system identifier.
     *
     * @param targetSystemId the target system ID
     * @return this builder for method chaining
     */
    public AuditEntryDefinition withTargetSystemId(String targetSystemId) {
        this.targetSystemId = targetSystemId;
        return this;
    }

    /**
     * Sets the recovery strategy.
     *
     * @param recoveryStrategy the recovery strategy
     * @return this builder for method chaining
     */
    public AuditEntryDefinition withRecoveryStrategy(RecoveryStrategy recoveryStrategy) {
        this.recoveryStrategy = recoveryStrategy;
        return this;
    }

    /**
     * Sets the order value.
     *
     * @param order the order value
     * @return this builder for method chaining
     */
    public AuditEntryDefinition withOrder(String order) {
        this.order = order;
        return this;
    }

    /**
     * Sets the transactional flag.
     *
     * @param transactional the transactional flag
     * @return this builder for method chaining
     */
    public AuditEntryDefinition withTransactional(boolean transactional) {
        this.transactional = transactional;
        return this;
    }

    // ========== Getters ==========

    public String getChangeId() {
        return changeId;
    }

    public AuditEntry.Status getState() {
        return state;
    }

    public String getExecutionId() {
        return executionId;
    }

    public String getStageId() {
        return stageId;
    }

    public String getAuthor() {
        return author;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public Object getMetadata() {
        return metadata;
    }

    public Long getExecutionMillis() {
        return executionMillis;
    }

    public String getExecutionHostname() {
        return executionHostname;
    }

    public String getErrorTrace() {
        return errorTrace;
    }

    public String getTargetSystemId() {
        return targetSystemId;
    }

    public RecoveryStrategy getRecoveryStrategy() {
        return recoveryStrategy;
    }

    public String getOrder() {
        return order;
    }

    public Boolean getTransactional() {
        return transactional;
    }

    // ========== Conversion Methods ==========

    /**
     * Converts this definition to an {@link AuditEntry} for insertion into the audit store.
     *
     * <p>Fields that are not set will use sensible defaults:</p>
     * <ul>
     *   <li>{@code executionId} - UUID-based if not specified</li>
     *   <li>{@code stageId} - UUID-based if not specified</li>
     *   <li>{@code createdAt} - current time if not specified</li>
     *   <li>{@code executionMillis} - 0 if not specified</li>
     *   <li>{@code executionHostname} - "test-host" if not specified</li>
     *   <li>{@code type} - {@code ExecutionType.EXECUTION}</li>
     *   <li>{@code txStrategy} - {@code AuditTxType.NON_TX}</li>
     *   <li>{@code systemChange} - false</li>
     *   <li>{@code recoveryStrategy} - {@code RecoveryStrategy.MANUAL_INTERVENTION} if not specified</li>
     * </ul>
     *
     * @return an {@link AuditEntry} instance representing this definition
     */
    public AuditEntry toAuditEntry() {
        return new AuditEntry(
                executionId != null ? executionId : "precondition-" + UUID.randomUUID().toString(),
                stageId != null ? stageId : "precondition-stage-" + UUID.randomUUID().toString(),
                changeId,
                author,
                createdAt != null ? createdAt : LocalDateTime.now(),
                state,
                AuditEntry.ExecutionType.EXECUTION,
                className,
                methodName,
                executionMillis != null ? executionMillis : 0L,
                executionHostname != null ? executionHostname : "test-host",
                metadata,
                false, // systemChange
                errorTrace,
                AuditTxType.NON_TX,
                targetSystemId,
                order,
                recoveryStrategy != null ? recoveryStrategy : RecoveryStrategy.MANUAL_INTERVENTION,
                transactional
        );
    }
}
