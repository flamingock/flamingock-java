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
package io.flamingock.internal.common.core.transaction;

import io.flamingock.internal.common.core.context.RuntimeContext;
import io.flamingock.internal.common.core.error.DatabaseTransactionException;

import java.util.function.Function;

/**
 * Runs an operation inside a target system's transaction boundary.
 * <p>
 * A wrapper owns the whole transaction lifecycle: it opens the transaction, publishes the
 * transaction-scoped handle (a JDBC {@code Connection}, a MongoDB {@code ClientSession}, a DynamoDB
 * write-request builder, …) into the supplied {@link RuntimeContext}, runs the operation, and then
 * commits or rolls back according to how that operation ended — releasing the underlying resources
 * either way. Callers contribute only the work to be run; they reach the handle, if they need it, by
 * resolving it from the context the wrapper handed back to them.
 * <p>
 * Implementations are used in two distinct places:
 * <ul>
 *   <li><strong>Transactional target systems</strong> — to apply a change within the target system's
 *       own transaction.</li>
 *   <li><strong>Audit stores</strong> — to make a group of the store's own writes atomic (for example,
 *       a change-state transition and its journal event).</li>
 * </ul>
 *
 * @see RuntimeContext
 * @see DatabaseTransactionException
 */
public interface TransactionWrapper {

    /**
     * Executes {@code operation} within a transaction and returns its result.
     *
     * <h3>Lifecycle</h3>
     * <ol>
     *   <li>Open a transaction, keyed by {@link RuntimeContext#getSessionId()}.</li>
     *   <li>Publish the transaction-scoped handle into {@code runtimeContext}, making it resolvable by
     *       the operation.</li>
     *   <li>Apply the operation.</li>
     *   <li>Commit or roll back, according to the outcome below.</li>
     *   <li>Release the transaction resources — always, on every outcome.</li>
     * </ol>
     *
     * <h3>Outcomes</h3>
     * An operation can end in three ways, and the two failure paths are deliberately different:
     * <ul>
     *   <li><strong>Success.</strong> The operation returns normally and the returned value is not a
     *       failed step. The transaction is committed and the value is returned to the caller
     *       unchanged — this method never rewrites the operation's result.</li>
     *
     *   <li><strong>Failure reported as a value.</strong> The operation returns a
     *       {@code io.flamingock.internal.core.change.navigation.step.FailedStep}, meaning the work
     *       failed but the failure has already been captured as part of the result. The transaction is
     *       rolled back (or, where writes are only staged until commit, simply never committed) and
     *       that same failed step is returned to the caller. <strong>No exception is thrown.</strong>
     *       This is what allows the execution engine to audit the failure and drive recovery from a
     *       returned value rather than by unwinding a stack.</li>
     *
     *   <li><strong>Failure reported as an exception.</strong> The operation throws. The transaction is
     *       rolled back and the original exception is rethrown wrapped in a
     *       {@link DatabaseTransactionException}, which carries the diagnostic context of the attempt —
     *       transaction state, duration, connection details and, importantly, whether the rollback
     *       itself succeeded
     *       ({@link DatabaseTransactionException.RollbackStatus RollbackStatus}). A failed rollback is
     *       reported through that status, not by swallowing the original failure.</li>
     * </ul>
     * Note that a rolled-back transaction is therefore <em>not</em> in itself an error signal to the
     * caller: whether it learns about the failure by value or by exception depends on how the operation
     * chose to report it.
     *
     * <h3>Implementation contract</h3>
     * <ul>
     *   <li>Use {@link RuntimeContext#getSessionId()} as the key for the underlying session. For change
     *       execution this is the change id; other callers scope it as they see fit, but it must
     *       identify one transaction scope and no other.</li>
     *   <li>Inject transaction-scoped dependencies only <em>after</em> the transaction has started, so
     *       the operation cannot observe a handle that is not yet usable.</li>
     *   <li>Return the operation's result untouched on success and on a failed step.</li>
     *   <li>Surface every transactional failure as a {@link DatabaseTransactionException}, so callers
     *       have one exception type to reason about regardless of the target system.</li>
     *   <li>Release resources in a {@code finally} block, so neither a commit failure nor a rollback
     *       failure can leak a session.</li>
     * </ul>
     *
     * @param <CONTEXT>      the concrete runtime context type, returned to the operation as-is so it
     *                       keeps its static type
     * @param <RESULT>       the type produced by the operation
     * @param runtimeContext the context identifying the transaction scope and receiving the
     *                       transaction-scoped dependencies
     * @param operation      the work to execute within the transaction
     * @return whatever the operation returned — including a failed step, when the operation reported
     *         its failure that way
     * @throws DatabaseTransactionException if the operation throws, or if the transaction itself cannot
     *                                      be started, committed or rolled back
     */
    <CONTEXT extends RuntimeContext, RESULT> RESULT wrapInTransaction(CONTEXT runtimeContext, Function<CONTEXT, RESULT> operation);

}
