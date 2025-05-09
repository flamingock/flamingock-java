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
import io.flamingock.core.configurator.cloud.CloudConfiguration;
import io.flamingock.core.configurator.cloud.CloudConfigurator;
import io.flamingock.core.configurator.cloud.CloudConfiguratorDelegate;
import io.flamingock.core.configurator.cloud.CloudSystemModuleManager;
import io.flamingock.core.configurator.core.CoreConfiguration;
import io.flamingock.core.configurator.core.CoreConfiguratorDelegate;
import io.flamingock.core.engine.ConnectionEngine;
import io.flamingock.core.runtime.dependency.DependencyInjectableContext;
import org.apache.http.impl.client.HttpClients;

import java.util.Optional;

public class FlamingockCloudBuilder
        extends AbstractFlamingockBuilder<FlamingockCloudBuilder>
        implements CloudConfigurator<FlamingockCloudBuilder> {

    private final CloudSystemModuleManager systemModuleManager;


    private final CoreConfiguratorDelegate<FlamingockCloudBuilder> coreConfiguratorDelegate;

    private final StandaloneConfiguratorDelegate<FlamingockCloudBuilder> standaloneConfiguratorDelegate;

    private final CloudConfiguratorDelegate<FlamingockCloudBuilder> cloudConfiguratorDelegate;

    private CloudEngine engine;

    protected FlamingockCloudBuilder(CoreConfiguration coreConfiguration,
                                     CloudConfiguration cloudConfiguration,
                                     DependencyInjectableContext dependencyInjectableContext,
                                     CloudSystemModuleManager systemModuleManager) {
        super(systemModuleManager);
        this.coreConfiguratorDelegate = new CoreConfiguratorDelegate<>(coreConfiguration, () -> this);
        this.standaloneConfiguratorDelegate = new StandaloneConfiguratorDelegate<>(dependencyInjectableContext, () -> this);
        this.cloudConfiguratorDelegate = new CloudConfiguratorDelegate<>(cloudConfiguration, () -> this);
        this.systemModuleManager = systemModuleManager;

    }


    @Override
    protected CoreConfiguratorDelegate<FlamingockCloudBuilder> coreConfiguratorDelegate() {
        return coreConfiguratorDelegate;
    }

    @Override
    protected StandaloneConfiguratorDelegate<FlamingockCloudBuilder> standaloneConfiguratorDelegate() {
        return standaloneConfiguratorDelegate;
    }


    @Override
    protected ConnectionEngine getConnectionEngine(RunnerId runnerId) {
        //TODO get the driver from serviceLoader
        engine = CloudEngine.newFactory(
                runnerId,
                coreConfiguratorDelegate.getCoreConfiguration(),
                cloudConfiguratorDelegate.getCloudConfiguration(),
                getCloudTransactioner().orElse(null),
                Http.builderFactory(HttpClients.createDefault(), JsonObjectMapper.DEFAULT_INSTANCE)
        ).initializeAndGet();

        return engine;
    }

    @Override
    protected void configureSystemModules() {
        //todo change this
        systemModuleManager.initialize(
                engine.getEnvironmentId(), engine.getServiceId(), engine.getJwt(), cloudConfiguratorDelegate.getCloudConfiguration().getHost());
    }

    @Override
    public FlamingockCloudBuilder setHost(String host) {
        return cloudConfiguratorDelegate.setHost(host);
    }

    @Override
    public FlamingockCloudBuilder setService(String service) {
        return cloudConfiguratorDelegate.setService(service);
    }

    @Override
    public FlamingockCloudBuilder setEnvironment(String environment) {
        return cloudConfiguratorDelegate.setEnvironment(environment);
    }

    @Override
    public FlamingockCloudBuilder setApiToken(String clientSecret) {
        return cloudConfiguratorDelegate.setApiToken(clientSecret);
    }

    @Override
    public FlamingockCloudBuilder setCloudTransactioner(CloudTransactioner cloudTransactioner) {
        return cloudConfiguratorDelegate.setCloudTransactioner(cloudTransactioner);
    }

    @Override
    public Optional<CloudTransactioner> getCloudTransactioner() {
        return cloudConfiguratorDelegate.getCloudTransactioner();
    }

}
