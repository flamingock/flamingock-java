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
package io.flamingock.internal.core.external.targets;

import io.flamingock.api.external.TargetSystem;
import io.flamingock.internal.common.core.context.*;
import io.flamingock.internal.common.core.external.ExecutionWrapper;
import io.flamingock.internal.core.context.SimpleContext;
import io.flamingock.internal.core.runtime.ExecutionRuntime;
import io.flamingock.internal.util.Property;

import java.io.File;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.ZonedDateTime;
import java.util.Currency;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;


/**
 * Base implementation for all target systems in Flamingock.
 * <p>
 * Provides common functionality for managing target-system-specific context,
 * including dependencies and properties that will be available to changes
 * during execution.
 * <p>
 * Subclasses have two distinct extension points, and the split matters:
 * <ul>
 *   <li>{@link #enhanceExecutionRuntime(RuntimeContext, boolean)} — injects session-scoped dependencies
 *       (database connections, client sessions) obtained fresh for each change execution. Called by the
 *       framework on both the transactional and the non-transactional path, before any wrapper runs.</li>
 *   <li>{@link #getNonTxWrapper()} — owns the resource lifecycle of non-transactional execution, when
 *       something must be acquired for the call and released afterwards. Its transactional counterpart
 *       is {@code TransactionalExternalSystem#getTxWrapper()}.</li>
 * </ul>
 *
 * @param <HOLDER> the concrete target system type for fluent API support
 */
public abstract class AbstractTargetSystem<HOLDER extends AbstractTargetSystem<HOLDER>>
        implements
        TargetSystem,
        ContextProvider,
        ContextConfigurable<HOLDER> {

    /**
     * Pass-through wrapper: applies the operation and nothing else. Stateless, so one instance serves
     * every target system that has no resource to acquire for non-transactional execution.
     */
    private static final ExecutionWrapper PASS_THROUGH_WRAPPER = new ExecutionWrapper() {
        @Override
        public <CONTEXT extends RuntimeContext, RESULT> RESULT wrapExecution(CONTEXT runtimeContext, Function<CONTEXT, RESULT> operation) {
            return operation.apply(runtimeContext);
        }
    };

    private final String id;

    protected final Context targetSystemContext = new SimpleContext();

    public AbstractTargetSystem(String id) {
        this.id = id;
        targetSystemContext.setProperty("change.targetSystem.id", id);

    }

    @Override
    public String getId() {
        return id;
    }

    abstract protected HOLDER getSelf();

    /**
     * Applies a change operation with session-scoped dependency injection.
     * <p>
     * This method is the entry point for non-transactional change execution. It enhances the runtime
     * with the target system's session-scoped dependencies, then delegates execution to
     * {@link #getNonTxWrapper()}, which owns whatever resources that execution needs.
     *
     * @param <T>             the return type of the change operation
     * @param changeApplier   the function that executes the actual change
     * @param executionRuntime the runtime context for dependency resolution
     * @return the result of the change operation
     */
    public final <T> T applyChange(Function<ExecutionRuntime, T> changeApplier, ExecutionRuntime executionRuntime) {
        enhanceExecutionRuntime(executionRuntime, false);
        return getNonTxWrapper().wrapExecution(executionRuntime, changeApplier);
    }

    /**
     * Rolls back (reverts) a previously applied change with session-scoped dependency injection.
     * <p>
     * This method is the entry point for non-transactional rollback execution. It enhances the runtime
     * with the target system's session-scoped dependencies, then delegates execution to
     * {@link #getNonTxWrapper()}, which owns whatever resources that execution needs.
     *
     * @param <T>               the return type of the rollback operation
     * @param changeRollbacker  the function that executes the actual rollback
     * @param executionRuntime  the runtime context for dependency resolution
     * @return the result of the rollback operation
     */
    public final <T> T rollbackChange(Function<ExecutionRuntime, T> changeRollbacker, ExecutionRuntime executionRuntime) {
        enhanceExecutionRuntime(executionRuntime, false);
        return getNonTxWrapper().wrapExecution(executionRuntime, changeRollbacker);
    }

    /**
     * Returns the wrapper used for non-transactional execution — the counterpart to
     * {@code TransactionalExternalSystem#getTxWrapper()}, which is used when the change runs in a
     * transaction. Both are {@link ExecutionWrapper}s; the two method names are what distinguish the
     * intent, since the type alone no longer says whether a transaction is involved.
     * <p>
     * The default implementation is a pass-through: it simply applies the operation, because a target
     * system with nothing to acquire has no boundary to own.
     * <p>
     * Override this when non-transactional execution needs a resource for the duration of the call — a
     * pooled connection, a client session — and that resource must be released afterwards. Acquire it,
     * publish it into the supplied context, apply the operation, and release it on every outcome
     * (try-with-resources, or a {@code finally} block).
     * <p>
     * <strong>Do not enhance the runtime in an override.</strong> {@link #applyChange} and
     * {@link #rollbackChange} already call {@link #enhanceExecutionRuntime(RuntimeContext, boolean)}
     * with {@code isTransactional=false} before invoking the wrapper; enhancing again would inject the
     * session-scoped dependencies twice. An override contributes only the handles its own boundary owns.
     *
     * @return the wrapper owning resource lifecycle for non-transactional apply and rollback
     */
    protected ExecutionWrapper getNonTxWrapper() {
        return PASS_THROUGH_WRAPPER;
    }


    /**
     * Hook for injecting session-scoped dependencies into the execution runtime.
     * <p>
     * Subclasses should override this method to inject resources that need to be
     * obtained fresh for each change execution, such as:
     * <ul>
     *   <li>Database connections from a connection pool</li>
     *   <li>Client sessions for NoSQL databases</li>
     *   <li>API clients or other session-specific resources</li>
     * </ul>
     * <p>
     * The {@code isTransactional} parameter indicates whether the change will be
     * applied within a transaction, allowing different dependencies to be injected
     * based on the execution context.
     *
     * @param executionRuntime the runtime to enhance with dependencies
     * @param isTransactional  true if the change will run in a transaction, false otherwise
     */
    protected void enhanceExecutionRuntime(RuntimeContext executionRuntime, boolean isTransactional) {
    }

    @Override
    public ContextResolver getContext() {
        return targetSystemContext;
    }

    @Override
    public HOLDER addDependency(String name, Class<?> type, Object instance) {
        targetSystemContext.addDependency(new Dependency(name, type, instance));
        return getSelf();
    }

    @Override
    public HOLDER addDependency(Object instance) {
        if (instance instanceof Dependency) {
            targetSystemContext.addDependency(instance);
            return getSelf();
        } else {
            return addDependency(Dependency.DEFAULT_NAME, instance.getClass(), instance);
        }

    }

    @Override
    public HOLDER addDependency(String name, Object instance) {
        return addDependency(name, instance.getClass(), instance);
    }

    @Override
    public HOLDER addDependency(Class<?> type, Object instance) {
        return addDependency(Dependency.DEFAULT_NAME, type, instance);
    }

    @Override
    public HOLDER setProperty(Property property) {
        targetSystemContext.setProperty(property);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, String value) {
        targetSystemContext.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, Boolean value) {
        targetSystemContext.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, Integer value) {
        targetSystemContext.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, Float value) {
        targetSystemContext.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, Long value) {
        targetSystemContext.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, Double value) {
        targetSystemContext.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, UUID value) {
        targetSystemContext.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, Currency value) {
        targetSystemContext.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, Locale value) {
        targetSystemContext.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, Charset value) {
        targetSystemContext.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, File value) {
        targetSystemContext.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, Path value) {
        targetSystemContext.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, InetAddress value) {
        targetSystemContext.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, URL value) {
        targetSystemContext.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, URI value) {
        targetSystemContext.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, Duration value) {
        targetSystemContext.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, Period value) {
        targetSystemContext.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, Instant value) {
        targetSystemContext.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, LocalDate value) {
        targetSystemContext.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, LocalTime value) {
        targetSystemContext.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, LocalDateTime value) {
        targetSystemContext.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, ZonedDateTime value) {
        targetSystemContext.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, OffsetDateTime value) {
        targetSystemContext.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, OffsetTime value) {
        targetSystemContext.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, java.util.Date value) {
        targetSystemContext.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, java.sql.Date value) {
        targetSystemContext.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, Time value) {
        targetSystemContext.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, Timestamp value) {
        targetSystemContext.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, String[] value) {
        targetSystemContext.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, Integer[] value) {
        targetSystemContext.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, Long[] value) {
        targetSystemContext.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, Double[] value) {
        targetSystemContext.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, Float[] value) {
        targetSystemContext.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, Boolean[] value) {
        targetSystemContext.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, Byte[] value) {
        targetSystemContext.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, Short[] value) {
        targetSystemContext.setProperty(key, value);
        return getSelf();
    }

    @Override
    public HOLDER setProperty(String key, Character[] value) {
        targetSystemContext.setProperty(key, value);
        return getSelf();
    }

    @Override
    public <T extends Enum<T>> HOLDER setProperty(String key, T value) {
        targetSystemContext.setProperty(key, value);
        return getSelf();
    }


}
