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
package io.flamingock.internal.common.core.external;

import io.flamingock.internal.common.core.context.RuntimeContext;
import io.flamingock.internal.common.core.error.DatabaseTransactionException;

import java.util.function.Function;

/**
 * Runs an operation inside an execution boundary: acquire scoped resources, publish their handles into
 * the {@link RuntimeContext}, apply the operation, release the resources — on every outcome. The caller
 * supplies only the work; it reaches the handles by resolving them from the context it gets back.
 * <p>
 * There are two kinds, and the method name that returns a wrapper is what tells them apart:
 * <ul>
 *   <li><strong>Non-transactional</strong> ({@code AbstractTargetSystem#getNonTxWrapper()}) — scopes
 *       resources, promises no atomicity. Often just applies the operation; SQL borrows a
 *       {@code Connection} and closes it.</li>
 *   <li><strong>Transactional</strong> ({@link TransactionalExternalSystem#getTxWrapper()}) — also opens
 *       a transaction and commits or rolls back. Used to apply a change transactionally, and by audit
 *       stores to make a group of their own writes atomic.</li>
 * </ul>
 * Holding an {@code ExecutionWrapper} therefore tells you nothing about whether your work is
 * transactional — that was decided by which wrapper you were handed.
 *
 * @see RuntimeContext
 * @see DatabaseTransactionException
 */
public interface ExecutionWrapper {

    /**
     * Executes {@code operation} within this wrapper's boundary and returns its result.
     *
     * <h3>The one thing to get right</h3>
     * <strong>A normal return does not mean the work committed.</strong> An operation can report failure
     * two ways, and they are handled differently:
     * <ul>
     *   <li><strong>By value</strong> — it returns a {@code FailedStep}. A transactional wrapper rolls
     *       back and returns that same failed step, <em>without throwing</em>. This is what lets the
     *       engine audit the failure and drive recovery from a value instead of a stack unwind.</li>
     *   <li><strong>By exception</strong> — a transactional wrapper rolls back and rethrows wrapped in
     *       {@link DatabaseTransactionException}, which carries the diagnostics, including whether the
     *       rollback itself succeeded
     *       ({@link DatabaseTransactionException.RollbackStatus RollbackStatus}).</li>
     * </ul>
     * So callers that need to know the work is durable — to advance a counter, publish, acknowledge —
     * must check the result, or use an operation whose return type cannot be a failed step. A
     * non-transactional wrapper has nothing staged to roll back: results and exceptions pass through
     * as-is.
     *
     * <h3>Implementing one</h3>
     * <ul>
     *   <li>Publish handles only once they are usable, and release them in a {@code finally} /
     *       try-with-resources so no path leaks them.</li>
     *   <li>Return the operation's result untouched — never rewrite it.</li>
     *   <li><strong>Do not</strong> call {@code enhanceExecutionRuntime}: the caller already did, and
     *       doing it again double-injects. Contribute only what your own boundary owns.</li>
     *   <li>Transactional only: key the session on {@link RuntimeContext#getSessionId()} (the change id,
     *       for change execution), and surface every transactional failure as
     *       {@link DatabaseTransactionException} so callers have one exception type to handle.</li>
     * </ul>
     *
     * @param <CONTEXT>      the concrete runtime context type, passed to the operation as-is
     * @param <RESULT>       the type produced by the operation
     * @param runtimeContext identifies the execution scope and receives the scoped dependencies
     * @param operation      the work to execute within the boundary
     * @return the operation's result, untouched — including a failed step returned after a rollback
     * @throws DatabaseTransactionException if a transactional operation throws, or the transaction cannot
     *                                      be started, committed or rolled back. Non-transactional
     *                                      wrappers propagate the original exception unwrapped
     */
    <CONTEXT extends RuntimeContext, RESULT> RESULT wrapExecution(CONTEXT runtimeContext, Function<CONTEXT, RESULT> operation);

}
