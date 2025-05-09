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

package io.flamingock.core.builder;

import io.flamingock.commons.utils.JsonObjectMapper;
import io.flamingock.commons.utils.RunnerId;
import io.flamingock.commons.utils.http.Http;
import io.flamingock.core.cloud.CloudEngine;
import io.flamingock.core.cloud.transaction.CloudTransactioner;
import io.flamingock.core.builder.cloud.CloudConfiguration;
import io.flamingock.core.builder.cloud.CloudConfigurator;
import io.flamingock.core.builder.cloud.CloudSystemModuleManager;
import io.flamingock.core.builder.core.CoreConfiguration;
import io.flamingock.core.engine.ConnectionEngine;
import io.flamingock.core.runtime.dependency.DependencyInjectableContext;
import org.apache.http.impl.client.HttpClients;

public class CloudFlamingockBuilder
        extends AbstractFlamingockBuilder<CloudFlamingockBuilder>
        implements CloudConfigurator<CloudFlamingockBuilder> {

    private final CloudSystemModuleManager systemModuleManager;

    private final CloudConfiguration cloudConfiguration;

    private CloudTransactioner cloudTransactioner;

    private CloudEngine engine;

    protected CloudFlamingockBuilder(CoreConfiguration coreConfiguration,
                                     CloudConfiguration cloudConfiguration,
                                     DependencyInjectableContext dependencyInjectableContext,
                                     CloudSystemModuleManager systemModuleManager) {
        super(coreConfiguration, dependencyInjectableContext, systemModuleManager);
        this.cloudConfiguration = cloudConfiguration;
        this.systemModuleManager = systemModuleManager;

    }


    @Override
    protected CloudFlamingockBuilder getSelf() {
        return this;
    }


    @Override
    protected ConnectionEngine getConnectionEngine(RunnerId runnerId) {
        //TODO get the driver from serviceLoader
        engine = CloudEngine.newFactory(
                runnerId,
                coreConfiguration,
                cloudConfiguration,
                cloudTransactioner,
                Http.builderFactory(HttpClients.createDefault(), JsonObjectMapper.DEFAULT_INSTANCE)
        ).initializeAndGet();

        return engine;
    }

    @Override
    protected void configureSystemModules() {
        //todo change this
        systemModuleManager.initialize(
                engine.getEnvironmentId(), engine.getServiceId(), engine.getJwt(), cloudConfiguration.getHost());
    }

    @Override
    public CloudFlamingockBuilder setHost(String host) {
        cloudConfiguration.setHost(host);
        return this;
    }

    @Override
    public CloudFlamingockBuilder setService(String service) {
        cloudConfiguration.setServiceName(service);
        return this;
    }

    @Override
    public CloudFlamingockBuilder setEnvironment(String environment) {
        cloudConfiguration.setEnvironmentName(environment);
        return this;
    }

    @Override
    public CloudFlamingockBuilder setApiToken(String clientSecret) {
        cloudConfiguration.setApiToken(clientSecret);
        return this;
    }

    @Override
    public CloudFlamingockBuilder setCloudTransactioner(CloudTransactioner cloudTransactioner) {
        this.cloudTransactioner = cloudTransactioner;
        return this;
    }


}
