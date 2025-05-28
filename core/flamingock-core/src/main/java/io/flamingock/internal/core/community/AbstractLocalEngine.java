/*
 * Copyright 2023 Flamingock (https://oss.flamingock.io)
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

package io.flamingock.internal.core.community;

import io.flamingock.commons.utils.id.RunnerId;
import io.flamingock.core.api.error.FlamingockException;
import io.flamingock.internal.core.builder.local.CommunityConfigurable;
import io.flamingock.internal.core.system.SystemModule;
import io.flamingock.internal.core.system.SystemModuleManager;

import java.util.Optional;

public abstract class AbstractLocalEngine implements LocalEngine {

    protected final CommunityConfigurable localConfiguration;

    protected AbstractLocalEngine(CommunityConfigurable localConfiguration) {
        this.localConfiguration = localConfiguration;
    }

    abstract protected void doInitialize(RunnerId runnerId);

    abstract protected Optional<? extends SystemModule> getMongockLegacyImporterModule();

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
