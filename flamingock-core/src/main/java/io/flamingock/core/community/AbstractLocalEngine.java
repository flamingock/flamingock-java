package io.flamingock.core.community;

import io.flamingock.commons.utils.RunnerId;
import io.flamingock.core.api.exception.FlamingockException;
import io.flamingock.core.builder.local.CommunityConfigurable;

public abstract class AbstractLocalEngine implements LocalEngine {

    protected final CommunityConfigurable localConfiguration;

    protected AbstractLocalEngine(CommunityConfigurable localConfiguration) {
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
