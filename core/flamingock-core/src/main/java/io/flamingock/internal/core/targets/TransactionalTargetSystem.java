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
package io.flamingock.internal.core.targets;

import io.flamingock.internal.common.core.context.ContextInitializable;
import io.flamingock.internal.core.runtime.ExecutionRuntime;
import io.flamingock.internal.core.targets.mark.NoOpTargetSystemAuditMarker;
import io.flamingock.internal.core.targets.mark.TargetSystemAuditMarker;
import io.flamingock.internal.core.transaction.TransactionWrapper;
import io.flamingock.internal.util.constants.CommunityPersistenceConstants;

import java.util.function.Function;

/**
 * Base implementation for target systems that support transactional operations.
 * <p>
 * Extends {@link AbstractTargetSystem} to add transaction management capabilities,
 * including transaction wrapping, audit marking, etc.
 * <p>
 * Subclasses must provide:
 * <ul>
 *   <li>A {@link TransactionWrapper} for managing transactions</li>
 *   <li>An audit marker for tracking execution state (optional in Community Edition)</li>
 * </ul>
 *
 * @param <HOLDER> the concrete target system type for fluent API support
 */
public abstract class TransactionalTargetSystem<HOLDER extends TransactionalTargetSystem<HOLDER>>
        extends AbstractTargetSystem<HOLDER>
        implements ContextInitializable {

    protected String onGoingTasksRepositoryName = CommunityPersistenceConstants.DEFAULT_MARKER_STORE_NAME;
    protected boolean autoCreate = true;
    protected TargetSystemAuditMarker markerRepository;

    public TransactionalTargetSystem(String id) {
        super(id);
    }

    public boolean hasMarker() {
        TargetSystemAuditMarker onGoingTaskStatusRepository = getOnGoingTaskStatusRepository();
        return onGoingTaskStatusRepository != null && !(onGoingTaskStatusRepository instanceof NoOpTargetSystemAuditMarker);
    }

    /**
     * Applies a change operation within a transaction.
     * <p>
     * This method orchestrates transactional change execution by:
     * <ol>
     *   <li>Calling {@link #enhanceExecutionRuntime(ExecutionRuntime, boolean)} with
     *       {@code isTransactional=true} for session-scoped dependency injection</li>
     *   <li>Delegating to the {@link TransactionWrapper} for transaction management
     *       and potential injection of transaction-scoped dependencies</li>
     * </ol>
     *
     * @param <T>              the return type of the change operation
     * @param changeApplier    the function that executes the actual change
     * @param executionRuntime the runtime context for dependency resolution
     * @return the result of the change operation
     */
    public final <T> T applyChangeTransactional(Function<ExecutionRuntime, T> changeApplier, ExecutionRuntime executionRuntime) {
        enhanceExecutionRuntime(executionRuntime, true);
        return getTxWrapper().wrapInTransaction(executionRuntime, changeApplier);
    }

    /**
     * Returns the audit marker for tracking execution state.
     * <p>
     * In Community Edition, this typically returns a {@link NoOpTargetSystemAuditMarker}.
     * In Cloud Edition, it returns an implementation that persists execution state.
     *
     * @return the audit marker instance
     */
    public TargetSystemAuditMarker getOnGoingTaskStatusRepository() {
        return markerRepository;
    }

    /**
     * Returns the transaction wrapper for this target system.
     * <p>
     * The wrapper is responsible for starting, committing, and rolling back transactions,
     * as well as injecting transaction-scoped dependencies into the execution runtime.
     *
     * @return the transaction wrapper instance
     */
    abstract public TransactionWrapper getTxWrapper();

}
