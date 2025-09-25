/*
 * Copyright 2023 Flamingock (https://www.flamingock.io)
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
package io.flamingock.internal.core.transaction;

import io.flamingock.internal.core.runtime.ExecutionRuntime;

import java.util.function.Function;

/**
 * Manages transaction lifecycle for change operations.
 * <p>
 * Implementations are responsible for:
 * <ul>
 *   <li>Injecting transaction-scoped dependencies into the execution runtime</li>
 *   <li>Starting a transaction before the operation</li>
 *   <li>Committing the transaction on success</li>
 *   <li>Rolling back the transaction on failure</li>
 *   <li>Ensuring proper resource cleanup</li>
 * </ul>
 * <p>
 * Transaction-scoped dependencies (e.g., database sessions, transaction handles)
 * should be injected into the runtime after starting the transaction, making them
 * available to the change during execution.
 */
public interface TransactionWrapper {

    /**
     * Wraps the execution of an operation within a transaction.
     * <p>
     * The implementation should follow this pattern:
     * <ol>
     *   <li>Start a transaction</li>
     *   <li>Inject transaction-scoped dependencies into the runtime</li>
     *   <li>Execute the operation</li>
     *   <li>Commit on success or rollback on failure</li>
     *   <li>Clean up resources</li>
     * </ol>
     *
     * @param <T>                        the return type of the operation
     * @param injectableContextProvider  the execution runtime for dependency injection
     * @param operation                  the function to execute within the transaction
     * @return the result of the operation
     * @throws RuntimeException if the transaction fails
     */
    <T> T wrapInTransaction(ExecutionRuntime injectableContextProvider, Function<ExecutionRuntime, T> operation);

}
