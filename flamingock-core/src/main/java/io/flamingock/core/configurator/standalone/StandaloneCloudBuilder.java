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

import io.flamingock.commons.utils.JsonObjectMapper;
import io.flamingock.commons.utils.RunnerId;
import io.flamingock.commons.utils.http.Http;
import io.flamingock.core.cloud.CloudEngine;
import io.flamingock.core.cloud.transaction.CloudTransactioner;
import io.flamingock.core.configurator.FrameworkPlugin;
import io.flamingock.core.configurator.cloud.CloudConfiguration;
import io.flamingock.core.configurator.cloud.CloudConfigurator;
import io.flamingock.core.configurator.cloud.CloudConfiguratorDelegate;
import io.flamingock.core.configurator.cloud.CloudSystemModuleManager;
import io.flamingock.core.configurator.core.CoreConfigurable;
import io.flamingock.core.configurator.core.CoreConfiguration;
import io.flamingock.core.configurator.core.CoreConfiguratorDelegate;
import io.flamingock.core.engine.audit.AuditWriter;
import io.flamingock.core.event.EventPublisher;
import io.flamingock.core.pipeline.Pipeline;
import io.flamingock.core.pipeline.PipelineDescriptor;
import io.flamingock.core.runner.PipelineRunnerCreator;
import io.flamingock.core.runner.Runner;
import io.flamingock.core.runtime.dependency.DependencyContext;
import io.flamingock.core.runtime.dependency.DependencyInjectableContext;
import io.flamingock.core.runtime.dependency.PriorityDependencyContext;
import io.flamingock.core.system.CloudSystemModule;
import io.flamingock.core.task.filter.TaskFilter;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;

public class StandaloneCloudBuilder
        extends AbstractStandaloneBuilder<StandaloneCloudBuilder, CloudSystemModule, CloudSystemModuleManager>
        implements CloudConfigurator<StandaloneCloudBuilder> {
    private static final Logger logger = LoggerFactory.getLogger(StandaloneCloudBuilder.class);

    private final CoreConfiguratorDelegate<StandaloneCloudBuilder, CloudSystemModule, CloudSystemModuleManager> coreConfiguratorDelegate;

    private final StandaloneConfiguratorDelegate<StandaloneCloudBuilder> standaloneConfiguratorDelegate;

    private final CloudConfiguratorDelegate<StandaloneCloudBuilder> cloudConfiguratorDelegate;


    protected StandaloneCloudBuilder(CoreConfiguration coreConfiguration,
                           CloudConfiguration cloudConfiguration,
                           DependencyInjectableContext dependencyInjectableContext,
                           CloudSystemModuleManager systemModuleManager) {
        this.coreConfiguratorDelegate = new CoreConfiguratorDelegate<>(coreConfiguration, () -> this, systemModuleManager);
        this.standaloneConfiguratorDelegate = new StandaloneConfiguratorDelegate<>(dependencyInjectableContext, () -> this);
        this.cloudConfiguratorDelegate = new CloudConfiguratorDelegate<>(cloudConfiguration, () -> this);

    }


    ///////////////////////////////////////////////////////////////////////////////////
    //  BUILD
    //////////////////////////////////////////////////////////////////////////////////

    @Override
    protected CoreConfiguratorDelegate<StandaloneCloudBuilder, CloudSystemModule, CloudSystemModuleManager> coreConfiguratorDelegate() {
        return coreConfiguratorDelegate;
    }

    @Override
    protected StandaloneConfiguratorDelegate<StandaloneCloudBuilder> standaloneConfiguratorDelegate() {
        return standaloneConfiguratorDelegate;
    }

    @Override
    public Runner build() {

        coreConfiguratorDelegate.initialize();
        cloudConfiguratorDelegate.initialize();
        standaloneConfiguratorDelegate.initialize();

        RunnerId runnerId = RunnerId.generate();
        logger.info("Generated runner id:  {}", runnerId);


        CloudEngine.Factory engineFactory = CloudEngine.newFactory(
                runnerId,
                coreConfiguratorDelegate.getCoreConfiguration(),
                cloudConfiguratorDelegate.getCloudConfiguration(),
                getCloudTransactioner().orElse(null),
                Http.builderFactory(HttpClients.createDefault(), JsonObjectMapper.DEFAULT_INSTANCE)
        );

        CloudEngine engine = engineFactory.initializeAndGet();

        CloudSystemModuleManager systemModuleManager = coreConfiguratorDelegate.getSystemModuleManager();
        systemModuleManager
                .initialize(engine.getEnvironmentId(), engine.getServiceId(), engine.getJwt(), cloudConfiguratorDelegate.getCloudConfiguration().getHost());

        systemModuleManager
                .getDependencies()
                .forEach(d -> addDependency(d.getName(), d.getType(), d.getInstance()));

        registerTemplates();

        CoreConfigurable coreConfiguration = coreConfiguratorDelegate().getCoreConfiguration();

        //Injecting auditWriter
        addDependency(AuditWriter.class, engine.getAuditWriter());


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

        DependencyContext mergedContext = dependencyContextsFromPlugins
                .stream()
                .filter(Objects::nonNull)
                .reduce((previous, current) -> new PriorityDependencyContext(current, previous))
                .<DependencyContext>map(accumulated -> new PriorityDependencyContext(dependencyContextFromBuilder, accumulated))
                .orElse(dependencyContextFromBuilder);


        Pipeline pipeline = buildPipeline(
                taskFilters,
                systemModuleManager.getSortedSystemStagesBefore(),
                coreConfiguration.getPreviewPipeline(),
                systemModuleManager.getSortedSystemStagesAfter()
        );

        //injecting the pipeline descriptor to the dependencies
        addDependency(PipelineDescriptor.class, pipeline);

        return PipelineRunnerCreator.createCloud(
                runnerId,
                pipeline,
                engine,
                coreConfiguration,
                buildEventPublisher(eventPublishersFromPlugins),
                mergedContext,
                getCoreConfiguration().isThrowExceptionIfCannotObtainLock(),
                engineFactory.getCloser()
        );
    }


    @Override
    public StandaloneCloudBuilder setHost(String host) {
        return cloudConfiguratorDelegate.setHost(host);
    }

    @Override
    public StandaloneCloudBuilder setService(String service) {
        return cloudConfiguratorDelegate.setService(service);
    }

    @Override
    public StandaloneCloudBuilder setEnvironment(String environment) {
        return cloudConfiguratorDelegate.setEnvironment(environment);
    }

    @Override
    public StandaloneCloudBuilder setApiToken(String clientSecret) {
        return cloudConfiguratorDelegate.setApiToken(clientSecret);
    }

    @Override
    public StandaloneCloudBuilder setCloudTransactioner(CloudTransactioner cloudTransactioner) {
        return cloudConfiguratorDelegate.setCloudTransactioner(cloudTransactioner);
    }

    @Override
    public Optional<CloudTransactioner> getCloudTransactioner() {
        return cloudConfiguratorDelegate.getCloudTransactioner();
    }

}
