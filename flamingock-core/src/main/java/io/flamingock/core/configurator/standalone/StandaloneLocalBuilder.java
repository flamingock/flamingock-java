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

import io.flamingock.core.api.LocalSystemModule;
import io.flamingock.commons.utils.RunnerId;
import io.flamingock.core.api.exception.FlamingockException;
import io.flamingock.core.configurator.core.CoreConfigurable;
import io.flamingock.core.configurator.core.CoreConfiguration;
import io.flamingock.core.configurator.core.CoreConfiguratorDelegate;
import io.flamingock.core.configurator.local.LocalConfigurable;
import io.flamingock.core.configurator.local.LocalConfiguration;
import io.flamingock.core.configurator.local.LocalConfigurator;
import io.flamingock.core.configurator.local.LocalConfiguratorDelegate;
import io.flamingock.core.configurator.local.LocalSystemModuleManager;
import io.flamingock.core.engine.local.LocalEngine;
import io.flamingock.core.engine.local.driver.LocalDriver;
import io.flamingock.core.pipeline.Pipeline;
import io.flamingock.core.runner.PipelineRunnerCreator;
import io.flamingock.core.runner.Runner;
import io.flamingock.core.runtime.dependency.DependencyInjectableContext;
import io.flamingock.core.transaction.TransactionWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StandaloneLocalBuilder
        extends AbstractStandaloneBuilder<StandaloneLocalBuilder, LocalSystemModule, LocalSystemModuleManager>
        implements LocalConfigurator<StandaloneLocalBuilder> {

    private static final Logger logger = LoggerFactory.getLogger(StandaloneLocalBuilder.class);
    private final CoreConfiguratorDelegate<StandaloneLocalBuilder, LocalSystemModule, LocalSystemModuleManager> coreConfiguratorDelegate;

    private final StandaloneConfiguratorDelegate<StandaloneLocalBuilder> standaloneConfiguratorDelegate;

    private final LocalConfiguratorDelegate<StandaloneLocalBuilder> localConfiguratorDelegate;


    StandaloneLocalBuilder(CoreConfiguration coreConfiguration,
                           LocalConfiguration communityConfiguration,
                           DependencyInjectableContext dependencyInjectableContext,
                           LocalSystemModuleManager systemModuleManager) {
        this.coreConfiguratorDelegate = new CoreConfiguratorDelegate<>(coreConfiguration, () -> this, systemModuleManager);
        this.standaloneConfiguratorDelegate = new StandaloneConfiguratorDelegate<>(dependencyInjectableContext, () -> this);
        this.localConfiguratorDelegate = new LocalConfiguratorDelegate<>(communityConfiguration, () -> this);

    }


    ///////////////////////////////////////////////////////////////////////////////////
    //  BUILD
    ///////////////////////////////////////////////////////////////////////////////////

    @Override
    protected CoreConfiguratorDelegate<StandaloneLocalBuilder, LocalSystemModule, LocalSystemModuleManager> coreConfiguratorDelegate() {
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

        LocalEngine engine = localConfiguratorDelegate.getDriver().initializeAndGetEngine(
                runnerId,
                coreConfiguratorDelegate.getCoreConfiguration(),
                localConfiguratorDelegate.getLocalConfiguration()
        );


        //adds Mongock legacy importer, if the user has required it
        engine.getMongockLegacyImporterModule().ifPresent(coreConfiguratorDelegate::addSystemModule);

        getSystemModuleManager().initialize();

        coreConfiguratorDelegate.getSystemModuleManager()
                .getDependencies()
                .forEach(d -> addDependency(d.getName(), d.getType(), d.getInstance()));

        registerTemplates();

        CoreConfigurable coreConfiguration = coreConfiguratorDelegate().getCoreConfiguration();

        Pipeline pipeline = buildPipeline(
                coreConfiguratorDelegate.getSystemModuleManager().getSortedSystemStagesBefore(),
                coreConfiguration.getStages(),
                coreConfiguratorDelegate.getSystemModuleManager().getSortedSystemStagesAfter()
        );

        return PipelineRunnerCreator.create(
                runnerId,
                pipeline,
                coreConfiguratorDelegate.getFlamingockMetadata(),
                engine,
                coreConfiguration,
                buildEventPublisher(),
                getDependencyContext(),
                getCoreConfiguration().isThrowExceptionIfCannotObtainLock()
        );
    }




    ///////////////////////////////////////////////////////////////////////////////////
    //  LOCAL
    ///////////////////////////////////////////////////////////////////////////////////

    @Override
    public StandaloneLocalBuilder setDriver(LocalDriver<?> connectionDriver) {
        return localConfiguratorDelegate.setDriver(connectionDriver);
    }

    @Override
    public LocalDriver<?> getDriver() {
        return localConfiguratorDelegate.getDriver();
    }

    @Override
    public LocalConfigurable getLocalConfiguration() {
        return localConfiguratorDelegate.getLocalConfiguration();
    }

    @Override
    public StandaloneLocalBuilder disableTransaction() {
        return localConfiguratorDelegate.disableTransaction();
    }

    @Override
    public boolean isTransactionDisabled() {
        return localConfiguratorDelegate.isTransactionDisabled();
    }

}
