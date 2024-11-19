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

import io.flamingock.core.api.CloudSystemModule;
import io.flamingock.commons.utils.JsonObjectMapper;
import io.flamingock.commons.utils.RunnerId;
import io.flamingock.commons.utils.http.Http;
import io.flamingock.core.cloud.transaction.CloudTransactioner;
import io.flamingock.core.configurator.cloud.CloudConfiguration;
import io.flamingock.core.configurator.cloud.CloudConfigurator;
import io.flamingock.core.configurator.cloud.CloudConfiguratorDelegate;
import io.flamingock.core.configurator.cloud.CloudSystemModuleManager;
import io.flamingock.core.configurator.core.CoreConfiguration;
import io.flamingock.core.engine.CloudConnectionEngine;
import io.flamingock.core.pipeline.Pipeline;
import io.flamingock.core.runner.PipelineRunnerCreator;
import io.flamingock.core.runner.Runner;
import io.flamingock.springboot.v2.SpringDependencyContext;
import io.flamingock.springboot.v2.SpringRunnerBuilder;
import io.flamingock.springboot.v2.SpringUtil;
import io.flamingock.springboot.v2.configurator.SpringbootConfiguration;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Optional;

public class SpringbootCloudBuilder extends SpringbootBaseBuilder<SpringbootCloudBuilder, CloudSystemModule, CloudSystemModuleManager>
        implements
        CloudConfigurator<SpringbootCloudBuilder>,
        SpringRunnerBuilder {

    private static final Logger logger = LoggerFactory.getLogger(SpringbootCloudBuilder.class);


    private final CloudConfiguratorDelegate<SpringbootCloudBuilder> cloudConfiguratorDelegate;


    SpringbootCloudBuilder(CoreConfiguration coreConfiguration,
                           SpringbootConfiguration springbootConfiguration,
                           CloudConfiguration cloudConfiguration,
                           CloudSystemModuleManager systemModuleManager) {
        super(coreConfiguration, springbootConfiguration, systemModuleManager);
        this.cloudConfiguratorDelegate = new CloudConfiguratorDelegate<>(cloudConfiguration, this::getSelf);
    }

    @Override
    protected SpringbootCloudBuilder getSelf() {
        return this;
    }

    ///////////////////////////////////////////////////////////////////////////////////
    //  BUILD
    ///////////////////////////////////////////////////////////////////////////////////
    @Override
    public Runner build() {
        RunnerId runnerId = RunnerId.generate();
        logger.info("Generated runner id:  {}", runnerId);


        Http.RequestBuilderFactory requestBuilderFactory = Http.builderFactory(HttpClients.createDefault(), JsonObjectMapper.DEFAULT_INSTANCE);
        CloudTransactioner transactioner = getCloudTransactioner().orElse(null);

        CloudConnectionEngine.Factory engineFactory = CloudConnectionEngine.newFactory(
                runnerId,
                getCoreConfiguration(),
                cloudConfiguratorDelegate.getCloudConfiguration(),
                transactioner,
                requestBuilderFactory
        );

        CloudConnectionEngine engine = engineFactory.initializeAndGet();

        getSystemModuleManager().initialize(engine.getEnvironmentId(), engine.getServiceId(), engine.getJwt(), cloudConfiguratorDelegate.getCloudConfiguration().getHost());

        getSystemModuleManager()
                .getDependencies()
                .forEach(this::addDependency);

        String[] activeProfiles = SpringUtil.getActiveProfiles(getSpringContext());
        logger.info("Creating runner with spring profiles[{}]", Arrays.toString(activeProfiles));
        Pipeline pipeline = buildPipeline(activeProfiles,
                getSystemModuleManager().getSortedSystemStagesBefore(),
                getCoreConfiguration().getStages(),
                getSystemModuleManager().getSortedSystemStagesAfter());

        return PipelineRunnerCreator.create(
                runnerId,
                pipeline,
                getFlamingockMetadata(),
                engine,
                getCoreConfiguration(),
                createEventPublisher(),
                new SpringDependencyContext(getSpringContext()),
                getCoreConfiguration().isThrowExceptionIfCannotObtainLock(),
                engineFactory.getCloser()
        );

    }

    ///////////////////////////////////////////////////////////////////////////////////
    //  CLOUD
    ///////////////////////////////////////////////////////////////////////////////////

    @Override
    public SpringbootCloudBuilder setHost(String host) {
        return cloudConfiguratorDelegate.setHost(host);
    }

    @Override
    public SpringbootCloudBuilder setService(String service) {
        return cloudConfiguratorDelegate.setService(service);
    }

    @Override
    public SpringbootCloudBuilder setEnvironment(String environment) {
        return cloudConfiguratorDelegate.setEnvironment(environment);
    }

    @Override
    public SpringbootCloudBuilder setApiToken(String clientSecret) {
        return cloudConfiguratorDelegate.setApiToken(clientSecret);
    }

    @Override
    public SpringbootCloudBuilder setCloudTransactioner(CloudTransactioner cloudTransactioner) {
        return cloudConfiguratorDelegate.setCloudTransactioner(cloudTransactioner);
    }

    @Override
    public Optional<CloudTransactioner> getCloudTransactioner() {
        return cloudConfiguratorDelegate.getCloudTransactioner();
    }
}
