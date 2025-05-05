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
import io.flamingock.core.configurator.FrameworkPlugin;
import io.flamingock.core.configurator.core.CoreConfigurable;
import io.flamingock.core.configurator.core.CoreConfiguration;
import io.flamingock.core.configurator.core.CoreConfiguratorDelegate;
import io.flamingock.core.configurator.local.LocalConfigurable;
import io.flamingock.core.configurator.local.LocalConfiguration;
import io.flamingock.core.configurator.local.LocalConfigurator;
import io.flamingock.core.configurator.local.LocalConfiguratorDelegate;
import io.flamingock.core.configurator.local.LocalSystemModuleManager;
import io.flamingock.core.engine.audit.AuditWriter;
import io.flamingock.core.event.EventPublisher;
import io.flamingock.core.local.LocalEngine;
import io.flamingock.core.local.driver.LocalDriver;
import io.flamingock.core.pipeline.Pipeline;
import io.flamingock.core.pipeline.PipelineDescriptor;
import io.flamingock.core.runner.PipelineRunnerCreator;
import io.flamingock.core.runner.Runner;
import io.flamingock.core.runtime.dependency.DependencyContext;
import io.flamingock.core.runtime.dependency.DependencyInjectableContext;
import io.flamingock.core.runtime.dependency.PriorityDependencyContext;
import io.flamingock.core.system.LocalSystemModule;
import io.flamingock.core.task.filter.TaskFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;

public class FlamingockLocalBuilder
        extends AbstractFlamingockBuilder<FlamingockLocalBuilder, LocalSystemModule, LocalSystemModuleManager>
        implements LocalConfigurator<FlamingockLocalBuilder> {

    private static final Logger logger = LoggerFactory.getLogger(FlamingockLocalBuilder.class);
    private final CoreConfiguratorDelegate<FlamingockLocalBuilder, LocalSystemModule, LocalSystemModuleManager> coreConfiguratorDelegate;

    private final StandaloneConfiguratorDelegate<FlamingockLocalBuilder> standaloneConfiguratorDelegate;

    private final LocalConfiguratorDelegate<FlamingockLocalBuilder> localConfiguratorDelegate;


    protected FlamingockLocalBuilder(CoreConfiguration coreConfiguration,
                                     LocalConfiguration communityConfiguration,
                                     DependencyInjectableContext dependencyInjectableContext,
                                     LocalSystemModuleManager systemModuleManager) {
        this.coreConfiguratorDelegate = new CoreConfiguratorDelegate<>(coreConfiguration, () -> this, systemModuleManager);
        this.standaloneConfiguratorDelegate = new StandaloneConfiguratorDelegate<>(dependencyInjectableContext, () -> this);
        this.localConfiguratorDelegate = new LocalConfiguratorDelegate<>(communityConfiguration, () -> this);

    }


    ///////////////////////////////////////////////////////////////////////////////////
    //  BUILD

    /// ////////////////////////////////////////////////////////////////////////////////

    @Override
    protected CoreConfiguratorDelegate<FlamingockLocalBuilder, LocalSystemModule, LocalSystemModuleManager> coreConfiguratorDelegate() {
        return coreConfiguratorDelegate;
    }

    @Override
    protected StandaloneConfiguratorDelegate<FlamingockLocalBuilder> standaloneConfiguratorDelegate() {
        return standaloneConfiguratorDelegate;
    }

    @Override
    public Runner build() {


        coreConfiguratorDelegate.initialize();
        localConfiguratorDelegate.initialize();
        standaloneConfiguratorDelegate.initialize();

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

        //Injecting auditWriter
        addDependency(AuditWriter.class, engine.getAuditor());


        List<TaskFilter> taskFilters = new LinkedList<>();
        List<EventPublisher> eventPublishersFromPlugins = new LinkedList<>();
        DependencyContext dependencyContextFromBuilder = getDependencyContext();

        List<DependencyContext> dependencyContextsFromPlugins = new LinkedList<>();
        for (FrameworkPlugin plugin : ServiceLoader.load(FrameworkPlugin.class)) {
            plugin.initialize(dependencyContextFromBuilder);

            if (plugin.getEventPublisher().isPresent()) {
                eventPublishersFromPlugins.add(plugin.getEventPublisher().get());
            }

            if (plugin.getDependencyContext().isPresent()) {
                dependencyContextsFromPlugins.add(plugin.getDependencyContext().get());
            }
            taskFilters.addAll(plugin.getTaskFilters());
        }


        // Builds a hierarchical dependency context by chaining all plugin-provided contexts,
        // placing Flamingock's core context at the top.
        DependencyContext mergedContext = dependencyContextsFromPlugins
                .stream()
                .filter(Objects::nonNull)
                .reduce((previous, current) -> new PriorityDependencyContext(current, previous))
                .<DependencyContext>map(accumulated -> new PriorityDependencyContext(dependencyContextFromBuilder, accumulated))
                .orElse(dependencyContextFromBuilder);

        Pipeline pipeline = buildPipeline(
                taskFilters,
                coreConfiguratorDelegate.getSystemModuleManager().getSortedSystemStagesBefore(),
                coreConfiguration.getPreviewPipeline(),
                coreConfiguratorDelegate.getSystemModuleManager().getSortedSystemStagesAfter()
        );

        //injecting the pipeline descriptor to the dependencies
        addDependency(PipelineDescriptor.class, pipeline);

        return PipelineRunnerCreator.createLocal(
                runnerId,
                pipeline,
                engine,
                coreConfiguration,
                buildEventPublisher(eventPublishersFromPlugins),
                mergedContext,
                getCoreConfiguration().isThrowExceptionIfCannotObtainLock()
        );
    }


    ///////////////////////////////////////////////////////////////////////////////////
    //  LOCAL

    /// ////////////////////////////////////////////////////////////////////////////////

    @Override
    public FlamingockLocalBuilder setDriver(LocalDriver<?> connectionDriver) {
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
    public FlamingockLocalBuilder disableTransaction() {
        return localConfiguratorDelegate.disableTransaction();
    }

    @Override
    public boolean isTransactionDisabled() {
        return localConfiguratorDelegate.isTransactionDisabled();
    }

}
