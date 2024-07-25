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

import io.flamingock.core.configurator.core.CoreConfiguration;
import io.flamingock.core.configurator.core.CoreConfiguratorDelegate;
import io.flamingock.core.configurator.local.LocalConfigurable;
import io.flamingock.core.configurator.local.LocalConfiguration;
import io.flamingock.core.configurator.local.LocalConfigurator;
import io.flamingock.core.configurator.local.LocalConfiguratorDelegate;
import io.flamingock.core.engine.local.LocalConnectionEngine;
import io.flamingock.core.engine.local.driver.ConnectionDriver;
import io.flamingock.core.runner.PipelineRunnerCreator;
import io.flamingock.core.runner.Runner;
import io.flamingock.core.runner.RunnerId;
import io.flamingock.core.runtime.dependency.DependencyInjectableContext;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StandaloneLocalBuilder
        extends AbstractStandaloneBuilder<StandaloneLocalBuilder>
        implements LocalConfigurator<StandaloneLocalBuilder> {

    private static final Logger logger = LoggerFactory.getLogger(StandaloneLocalBuilder.class);
    private final CoreConfiguratorDelegate<StandaloneLocalBuilder> coreConfiguratorDelegate;

    private final StandaloneConfiguratorDelegate<StandaloneLocalBuilder> standaloneConfiguratorDelegate;

    private final LocalConfiguratorDelegate<StandaloneLocalBuilder> localConfiguratorDelegate;


    StandaloneLocalBuilder(CoreConfiguration coreConfiguration,
                           LocalConfiguration communityConfiguration,
                           DependencyInjectableContext dependencyInjectableContext) {
        this.coreConfiguratorDelegate = new CoreConfiguratorDelegate<>(coreConfiguration, () -> this);
        this.standaloneConfiguratorDelegate = new StandaloneConfiguratorDelegate<>(dependencyInjectableContext, () -> this);
        this.localConfiguratorDelegate = new LocalConfiguratorDelegate<>(communityConfiguration, () -> this);

    }


    ///////////////////////////////////////////////////////////////////////////////////
    //  BUILD
    ///////////////////////////////////////////////////////////////////////////////////

    @Override
    protected CoreConfiguratorDelegate<StandaloneLocalBuilder> coreConfiguratorDelegate() {
        return coreConfiguratorDelegate;
    }

    @Override
    protected StandaloneConfiguratorDelegate<StandaloneLocalBuilder> standaloneConfiguratorDelegate() {
        return standaloneConfiguratorDelegate;
    }

    @Override
    public Runner build() {
        RunnerId runnerId = RunnerId.generate();
        logger.info("Generated runner id:  {}", runnerId);
        LocalConnectionEngine connectionEngine = getAndInitializeConnectionEngine(runnerId);
        registerTemplates();
        return PipelineRunnerCreator.create(
                runnerId,
                buildPipeline(),
                connectionEngine.getAuditor(),
                connectionEngine.getTransactionWrapper().orElse(null),
                connectionEngine.getExecutionPlanner(),
                coreConfiguratorDelegate.getCoreConfiguration(),
                buildEventPublisher(),
                getDependencyContext(),
                getCoreConfiguration().isThrowExceptionIfCannotObtainLock(),
                connectionEngine::close
        );
    }

    @NotNull
    private LocalConnectionEngine getAndInitializeConnectionEngine(RunnerId runnerId) {
        LocalConnectionEngine connectionEngine = localConfiguratorDelegate
                .getDriver()
                .getConnectionEngine(coreConfiguratorDelegate.getCoreConfiguration(), localConfiguratorDelegate.getLocalConfiguration());
        connectionEngine.initialize(runnerId);
        return connectionEngine;
    }

    ///////////////////////////////////////////////////////////////////////////////////
    //  LOCAL
    ///////////////////////////////////////////////////////////////////////////////////

    @Override
    public StandaloneLocalBuilder setDriver(ConnectionDriver<?> connectionDriver) {
        return localConfiguratorDelegate.setDriver(connectionDriver);
    }

    @Override
    public ConnectionDriver<?> getDriver() {
        return localConfiguratorDelegate.getDriver();
    }

    @Override
    public LocalConfigurable getLocalConfiguration() {
        return localConfiguratorDelegate.getLocalConfiguration();
    }


}
