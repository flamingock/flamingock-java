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

package io.flamingock.springboot.v2.builder;

import io.flamingock.core.system.LocalSystemModule;
import io.flamingock.commons.utils.RunnerId;
import io.flamingock.core.configurator.core.CoreConfigurable;
import io.flamingock.core.configurator.core.CoreConfiguration;
import io.flamingock.core.configurator.local.LocalConfigurable;
import io.flamingock.core.configurator.local.LocalConfigurator;
import io.flamingock.core.configurator.local.LocalConfiguratorDelegate;
import io.flamingock.core.configurator.local.LocalSystemModuleManager;
import io.flamingock.core.engine.audit.AuditWriter;
import io.flamingock.core.local.LocalEngine;
import io.flamingock.core.local.driver.LocalDriver;
import io.flamingock.core.pipeline.Pipeline;
import io.flamingock.core.pipeline.PipelineDescriptor;
import io.flamingock.core.runner.PipelineRunnerCreator;
import io.flamingock.core.runner.Runner;
import io.flamingock.core.runtime.dependency.Dependency;
import io.flamingock.springboot.v2.SpringDependencyContext;
import io.flamingock.springboot.v2.SpringRunnerBuilder;
import io.flamingock.springboot.v2.SpringUtil;
import io.flamingock.springboot.v2.configurator.SpringbootConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public class SpringbootLocalBuilder extends AbstractSpringbootBuilder<SpringbootLocalBuilder, LocalSystemModule, LocalSystemModuleManager>
        implements
        LocalConfigurator<SpringbootLocalBuilder>,
        SpringRunnerBuilder {

    private static final Logger logger = LoggerFactory.getLogger(SpringbootLocalBuilder.class);

    private final LocalConfiguratorDelegate<SpringbootLocalBuilder> localConfiguratorDelegate;

    SpringbootLocalBuilder(CoreConfiguration coreConfiguration,
                           SpringbootConfiguration springbootConfiguration,
                           LocalConfigurable localConfiguration,
                           LocalSystemModuleManager systemModuleManager) {
        super(coreConfiguration, springbootConfiguration, systemModuleManager);
        this.localConfiguratorDelegate = new LocalConfiguratorDelegate<>(localConfiguration, () -> this);
    }

    ///////////////////////////////////////////////////////////////////////////////////
    //  BUILD
    ///////////////////////////////////////////////////////////////////////////////////

    @Override
    public Runner build() {

        coreConfiguratorDelegate.initialize();
        localConfiguratorDelegate.initialize();
        springbootConfiguratorDelegate.initialize();

        RunnerId runnerId = RunnerId.generate();
        logger.info("Generated runner id:  {}", runnerId);

        CoreConfigurable coreConfiguration = getCoreConfiguration();
        LocalEngine engine = localConfiguratorDelegate.getDriver().initializeAndGetEngine(
                runnerId,
                coreConfiguration,
                localConfiguratorDelegate.getLocalConfiguration()
        );

        //adds Mongock legacy importer, if the user has required it
        engine.getMongockLegacyImporterModule().ifPresent(coreConfiguratorDelegate::addSystemModule);


        getSystemModuleManager().initialize();

        getSystemModuleManager()
                .getDependencies()
                .forEach(this::addDependency);

        String[] activeProfiles = SpringUtil.getActiveProfiles(getSpringContext());
        logger.info("Creating runner with spring profiles[{}]", Arrays.toString(activeProfiles));
        Pipeline pipeline = buildPipeline(activeProfiles,
                getSystemModuleManager().getSortedSystemStagesBefore(),
                coreConfiguration.getPreviewPipeline(),
                getSystemModuleManager().getSortedSystemStagesAfter());

        //injecting the pipeline descriptor to the dependencies
        addDependency(new Dependency(PipelineDescriptor.class, pipeline));
        //Injecting auditWriter
        addDependency(new Dependency(AuditWriter.class, engine.getAuditor()));

        return PipelineRunnerCreator.createLocal(
                runnerId,
                pipeline,
                engine,
                coreConfiguration,
                createEventPublisher(),
                new SpringDependencyContext(getSpringContext()),
                coreConfiguration.isThrowExceptionIfCannotObtainLock()
        );

    }

    @Override
    protected SpringbootLocalBuilder getSelf() {
        return this;
    }

    ///////////////////////////////////////////////////////////////////////////////////
    //  LOCAL
    ///////////////////////////////////////////////////////////////////////////////////

    @Override
    public SpringbootLocalBuilder setDriver(LocalDriver<?> connectionDriver) {
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
    public SpringbootLocalBuilder disableTransaction() {
        return localConfiguratorDelegate.disableTransaction();
    }

    @Override
    public boolean isTransactionDisabled() {
        return localConfiguratorDelegate.isTransactionDisabled();
    }
}
