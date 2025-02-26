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

import io.flamingock.core.api.CloudSystemModule;
import io.flamingock.commons.utils.JsonObjectMapper;
import io.flamingock.commons.utils.RunnerId;
import io.flamingock.commons.utils.http.Http;
import io.flamingock.core.cloud.transaction.CloudTransactioner;
import io.flamingock.core.configurator.cloud.CloudConfiguration;
import io.flamingock.core.configurator.cloud.CloudConfigurator;
import io.flamingock.core.configurator.cloud.CloudConfiguratorDelegate;
import io.flamingock.core.configurator.cloud.CloudSystemModuleManager;
import io.flamingock.core.configurator.core.CoreConfigurable;
import io.flamingock.core.configurator.core.CoreConfiguration;
import io.flamingock.core.configurator.core.CoreConfiguratorDelegate;
import io.flamingock.core.cloud.CloudEngine;
import io.flamingock.core.engine.audit.AuditWriter;
import io.flamingock.core.pipeline.Pipeline;
import io.flamingock.core.pipeline.PipelineDescriptor;
import io.flamingock.core.runner.PipelineRunnerCreator;
import io.flamingock.core.runner.Runner;
import io.flamingock.core.runtime.dependency.Dependency;
import io.flamingock.core.runtime.dependency.DependencyInjectableContext;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class StandaloneCloudBuilder
        extends AbstractStandaloneBuilder<StandaloneCloudBuilder, CloudSystemModule, CloudSystemModuleManager>
        implements CloudConfigurator<StandaloneCloudBuilder> {
    private static final Logger logger = LoggerFactory.getLogger(StandaloneCloudBuilder.class);

    private final CoreConfiguratorDelegate<StandaloneCloudBuilder, CloudSystemModule, CloudSystemModuleManager> coreConfiguratorDelegate;

    private final StandaloneConfiguratorDelegate<StandaloneCloudBuilder> standaloneConfiguratorDelegate;

    private final CloudConfiguratorDelegate<StandaloneCloudBuilder> cloudConfiguratorDelegate;


    StandaloneCloudBuilder(CoreConfiguration coreConfiguration,
                           CloudConfiguration cloudConfiguration,
                           DependencyInjectableContext dependencyInjectableContext,
                           CloudSystemModuleManager systemModuleManager) {
        this.coreConfiguratorDelegate = new CoreConfiguratorDelegate<>(coreConfiguration, () -> this, systemModuleManager);
        this.standaloneConfiguratorDelegate = new StandaloneConfiguratorDelegate<>(dependencyInjectableContext, () -> this);
        this.cloudConfiguratorDelegate = new CloudConfiguratorDelegate<>(cloudConfiguration, () -> this);

    }


    ///////////////////////////////////////////////////////////////////////////////////
    //  BUILD
    ///////////////////////////////////////////////////////////////////////////////////

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

        coreConfiguratorDelegate.getSystemModuleManager()
                .initialize(engine.getEnvironmentId(), engine.getServiceId(), engine.getJwt(), cloudConfiguratorDelegate.getCloudConfiguration().getHost());

        coreConfiguratorDelegate.getSystemModuleManager()
                .getDependencies()
                .forEach(d -> addDependency(d.getName(), d.getType(), d.getInstance()));

        registerTemplates();

        CoreConfigurable coreConfiguration = coreConfiguratorDelegate().getCoreConfiguration();

        Pipeline pipeline = buildPipeline(
                coreConfiguratorDelegate.getSystemModuleManager().getSortedSystemStagesBefore(),
                coreConfiguration.getStages(),
                coreConfiguratorDelegate.getSystemModuleManager().getSortedSystemStagesAfter(),
                coreConfiguratorDelegate.getFlamingockMetadata()
        );

        //injecting the pipeline descriptor to the dependencies
        addDependency(PipelineDescriptor.class, pipeline);
        //Injecting auditWriter
        addDependency(AuditWriter.class, engine.getAuditWriter());

        return PipelineRunnerCreator.createCloud(
                runnerId,
                pipeline,
                coreConfiguratorDelegate.getFlamingockMetadata(),
                engine,
                coreConfiguration,
                buildEventPublisher(),
                getDependencyContext(),
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
