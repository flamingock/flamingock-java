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
package io.flamingock.internal.core.targets.operations;

import io.flamingock.api.targets.TargetSystem;
import io.flamingock.internal.common.core.targets.OperationType;
import io.flamingock.internal.core.runtime.ExecutionRuntime;

import java.util.function.Function;

/**
 * Operational interface for target systems used by the execution engine.
 * <p>
 * This interface provides the minimal API required by Flamingock's execution
 * engine to interact with target systems. It abstracts the implementation
 * details and exposes only the operations needed for change execution.
 * <p>
 * Implementations typically wrap an {@link io.flamingock.internal.core.targets.AbstractTargetSystem}
 * instance and delegate operations to it.
 */
public interface TargetSystemOps extends TargetSystem {

    /**
     * Returns the type of operations supported by this target system.
     *
     * @return the operation type (e.g., NON_TX, TX_AUDIT_STORE_SYNC, TX_NON_SYNC)
     */
    OperationType getOperationType();

    /**
     * Applies a change operation to this target system.
     * <p>
     * This method coordinates the execution of a change by:
     * <ul>
     *   <li>Injecting session-scoped dependencies via the target system</li>
     *   <li>Executing the change function with the enhanced runtime</li>
     * </ul>
     *
     * @param <T>              the return type of the change operation
     * @param changeApplier    the function that executes the actual change
     * @param executionRuntime the runtime context for dependency resolution
     * @return the result of the change operation
     */
    <T> T applyChange(Function<ExecutionRuntime, T> changeApplier, ExecutionRuntime executionRuntime);

    /**
     * Rolls back (reverts) a previously applied change in this target system.
     * <p>
     * This method coordinates the execution of a rollback by:
     * <ul>
     *   <li>Injecting session-scoped dependencies via the target system</li>
     *   <li>Executing the rollback function with the enhanced runtime</li>
     * </ul>
     * <p>
     * Implementations should strive for idempotency: invoking the rollback more than
     * once should not produce side effects beyond the first successful revert.
     *
     * @param <T>               the return type of the rollback operation
     * @param changeRollbacker  the function that executes the actual rollback
     * @param executionRuntime  the runtime context for dependency resolution
     * @return the result of the rollback operation
     */
    <T> T rollbackChange(Function<ExecutionRuntime, T> changeRollbacker, ExecutionRuntime executionRuntime);

}
