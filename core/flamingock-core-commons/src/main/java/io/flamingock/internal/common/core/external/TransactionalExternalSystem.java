/*
 * Copyright 2026 Flamingock (https://www.flamingock.io)
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

import io.flamingock.api.external.ExternalSystem;

public interface TransactionalExternalSystem extends ExternalSystem {

    /**
     * Indicates whether this concrete external-system instance can execute transactions.
     *
     * <p>Transaction-capable implementations default to {@code true} for backward compatibility,
     * but may override this value when the connected deployment does not support transactions.
     * A {@code false} result makes Flamingock use its normal non-transactional execution path.</p>
     *
     * @return {@code true} when transactions are available for this instance
     */
    default boolean supportsTransactions() {
        return true;
    }

    /**
     * Returns the transactional {@link ExecutionWrapper} for this external system.
     * <p>
     * The wrapper is responsible for starting, committing, and rolling back transactions,
     * as well as injecting transaction-scoped dependencies into the execution runtime.
     * <p>
     * It is one of two wrappers a target system exposes — the other,
     * {@code AbstractTargetSystem#getNonTxWrapper()}, serves changes that run outside a transaction.
     * Both are {@code ExecutionWrapper}s, so the method name, not the type, is what states the intent.
     * This one is only used when {@link #supportsTransactions()} is {@code true} <em>and</em> the change
     * itself is declared transactional.
     *
     * @return the transactional wrapper instance
     */
    ExecutionWrapper getTxWrapper();
}
