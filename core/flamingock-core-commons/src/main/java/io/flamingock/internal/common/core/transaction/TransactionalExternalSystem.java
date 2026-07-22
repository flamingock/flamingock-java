package io.flamingock.internal.common.core.transaction;

import io.flamingock.api.external.ExternalSystem;

public interface TransactionalExternalSystem extends ExternalSystem {

    /**
     * Returns the transaction wrapper for this target system.
     * <p>
     * The wrapper is responsible for starting, committing, and rolling back transactions,
     * as well as injecting transaction-scoped dependencies into the execution runtime.
     *
     * @return the transaction wrapper instance
     */
    TransactionWrapper getTxWrapper();
}
