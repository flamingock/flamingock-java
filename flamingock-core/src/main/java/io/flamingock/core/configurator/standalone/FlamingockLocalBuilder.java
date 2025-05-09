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

package io.flamingock.core.configurator.standalone;

import io.flamingock.commons.utils.RunnerId;
import io.flamingock.core.configurator.core.CoreConfiguration;
import io.flamingock.core.configurator.core.CoreConfiguratorDelegate;
import io.flamingock.core.configurator.local.LocalConfigurable;
import io.flamingock.core.configurator.local.LocalConfiguration;
import io.flamingock.core.configurator.local.LocalConfigurator;
import io.flamingock.core.configurator.local.LocalConfiguratorDelegate;
import io.flamingock.core.configurator.local.LocalSystemModuleManager;
import io.flamingock.core.engine.ConnectionEngine;
import io.flamingock.core.local.LocalEngine;
import io.flamingock.core.local.driver.LocalDriver;
import io.flamingock.core.runtime.dependency.DependencyInjectableContext;

public class FlamingockLocalBuilder
        extends AbstractFlamingockBuilder<FlamingockLocalBuilder>
        implements LocalConfigurator<FlamingockLocalBuilder> {

    private final LocalSystemModuleManager systemModuleManager;

    private final CoreConfiguratorDelegate<FlamingockLocalBuilder> coreConfiguratorDelegate;

    private final StandaloneConfiguratorDelegate<FlamingockLocalBuilder> standaloneConfiguratorDelegate;

    private final LocalConfiguratorDelegate<FlamingockLocalBuilder> localConfiguratorDelegate;

    private LocalEngine engine;

    protected FlamingockLocalBuilder(CoreConfiguration coreConfiguration,
                                     LocalConfiguration communityConfiguration,
                                     DependencyInjectableContext dependencyInjectableContext,
                                     LocalSystemModuleManager systemModuleManager) {
        super(systemModuleManager);
        this.coreConfiguratorDelegate = new CoreConfiguratorDelegate<>(coreConfiguration, () -> this);
        this.standaloneConfiguratorDelegate = new StandaloneConfiguratorDelegate<>(dependencyInjectableContext, () -> this);
        this.localConfiguratorDelegate = new LocalConfiguratorDelegate<>(communityConfiguration, () -> this);
        this.systemModuleManager = systemModuleManager;
    }

    @Override
    protected CoreConfiguratorDelegate<FlamingockLocalBuilder> coreConfiguratorDelegate() {
        return coreConfiguratorDelegate;
    }

    @Override
    protected StandaloneConfiguratorDelegate<FlamingockLocalBuilder> standaloneConfiguratorDelegate() {
        return standaloneConfiguratorDelegate;
    }

    @Override
    protected ConnectionEngine getConnectionEngine(RunnerId runnerId) {
        //TODO get the driver from serviceLoader
        engine = localConfiguratorDelegate.getDriver().initializeAndGetEngine(
                runnerId,
                coreConfiguratorDelegate.getCoreConfiguration(),
                localConfiguratorDelegate.getLocalConfiguration()
        );
        return engine;
    }

    @Override
    protected void configureSystemModules() {
        //TODO change this
        engine.getMongockLegacyImporterModule().ifPresent(systemModuleManager::add);
        systemModuleManager.initialize();
    }

    @Override
    @Deprecated
    public FlamingockLocalBuilder setDriver(LocalDriver<?> connectionDriver) {
        return localConfiguratorDelegate.setDriver(connectionDriver);
    }

    @Override
    @Deprecated
    public LocalDriver<?> getDriver() {
        return localConfiguratorDelegate.getDriver();
    }

    @Override
    public LocalConfigurable getLocalConfiguration() {
        return localConfiguratorDelegate.getLocalConfiguration();
    }

    @Override
    public FlamingockLocalBuilder disableTransaction() {
        return localConfiguratorDelegate.disableTransaction();
    }

    @Override
    public boolean isTransactionDisabled() {
        return localConfiguratorDelegate.isTransactionDisabled();
    }

}
