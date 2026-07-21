package io.flamingock.internal.core.external;

import io.flamingock.api.external.ExternalSystem;
import io.flamingock.internal.core.transaction.TransactionWrapper;

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
