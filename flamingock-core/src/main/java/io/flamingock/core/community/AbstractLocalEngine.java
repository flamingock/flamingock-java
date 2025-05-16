package io.flamingock.core.community;

import io.flamingock.commons.utils.RunnerId;
import io.flamingock.core.api.exception.FlamingockException;
import io.flamingock.core.builder.local.CommunityConfigurable;
import io.flamingock.core.system.SystemModuleManager;

public abstract class AbstractLocalEngine implements LocalEngine {

    protected final CommunityConfigurable localConfiguration;

    protected AbstractLocalEngine(CommunityConfigurable localConfiguration) {
        this.localConfiguration = localConfiguration;
    }

    abstract protected void doInitialize(RunnerId runnerId);

    public void initialize(RunnerId runnerId) {
        doInitialize(runnerId);
        validate();
    }
    private void validate() {
        boolean transactionEnabled = !localConfiguration.isTransactionDisabled();
        if (!getTransactionWrapper().isPresent() && transactionEnabled) {
            throw new FlamingockException("[transactionDisabled = false] and driver is not transactional. Either set transactionDisabled = true or provide a transactional driver");
        }
    }

    @Override
    public void contributeToSystemModules(SystemModuleManager systemModuleManager) {
        getMongockLegacyImporterModule().ifPresent(systemModuleManager::add);
    }
}
