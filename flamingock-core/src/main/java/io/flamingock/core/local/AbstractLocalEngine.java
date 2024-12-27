package io.flamingock.core.local;

import io.flamingock.commons.utils.RunnerId;
import io.flamingock.core.api.exception.FlamingockException;
import io.flamingock.core.configurator.local.LocalConfigurable;

public abstract class AbstractLocalEngine implements LocalEngine {

    protected final LocalConfigurable localConfiguration;

    protected AbstractLocalEngine(LocalConfigurable localConfiguration) {
        this.localConfiguration = localConfiguration;
    }

    public void initialize(RunnerId runnerId) {
        doInitialize(runnerId);
        validate();
    }

    abstract protected void doInitialize(RunnerId runnerId);

    private void validate() {
        boolean transactionEnabled = !localConfiguration.isTransactionDisabled();
        if (!getTransactionWrapper().isPresent() && transactionEnabled) {
            throw new FlamingockException("[transactionDisabled = false] and driver is not transactional. Either set transactionDisabled = true or provide a transactional driver");
        }
    }
}
