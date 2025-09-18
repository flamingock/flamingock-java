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

import io.flamingock.internal.core.runtime.ExecutionRuntime;
import io.flamingock.internal.core.targets.mark.TargetSystemAuditMarker;

import java.util.function.Function;

/**
 * Operational interface for target systems that support transactional operations.
 * <p>
 * Extends {@link TargetSystemOps} to add transactional change execution
 * and audit marking capabilities. This interface is used by the execution
 * engine when processing change units marked as transactional.
 * <p>
 * Implementations typically wrap a {@link io.flamingock.internal.core.targets.TransactionalTargetSystem}
 * instance and delegate operations to it.
 */
public interface TransactionalTargetSystemOps extends TargetSystemOps, TargetSystemAuditMarker {

    /**
     * Applies a change operation within a transaction.
     * <p>
     * This method coordinates transactional execution by:
     * <ul>
     *   <li>Injecting session-scoped dependencies via the target system</li>
     *   <li>Starting a transaction and injecting transaction-scoped dependencies</li>
     *   <li>Executing the change function within the transaction</li>
     *   <li>Committing or rolling back based on the operation result</li>
     * </ul>
     *
     * @param <T>              the return type of the change operation
     * @param changeApplier    the function that executes the actual change
     * @param executionRuntime the runtime context for dependency resolution
     * @return the result of the change operation
     * @throws RuntimeException if the transaction fails
     */
    <T> T applyChangeTransactional(Function<ExecutionRuntime, T> changeApplier, ExecutionRuntime executionRuntime);

}
