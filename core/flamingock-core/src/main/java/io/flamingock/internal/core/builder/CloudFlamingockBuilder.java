/*
 * Copyright 2023 Flamingock (https://www.flamingock.io)
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
package io.flamingock.internal.core.builder;

import io.flamingock.internal.core.configuration.cloud.CloudConfiguration;
import io.flamingock.internal.core.configuration.cloud.CloudConfigurator;
import io.flamingock.internal.core.configuration.core.CoreConfiguration;
import io.flamingock.internal.core.store.CloudAuditStore;
import io.flamingock.internal.common.core.context.Context;
import io.flamingock.internal.core.plan.ExecutionPlanner;
import io.flamingock.internal.core.plugin.PluginManager;
import io.flamingock.internal.util.id.RunnerId;

//TODO to cloud module
public class CloudFlamingockBuilder
        extends AbstractFlamingockBuilder<CloudFlamingockBuilder>
        implements CloudConfigurator<CloudFlamingockBuilder> {


    private final CloudConfiguration cloudConfiguration;

    protected CloudFlamingockBuilder(CoreConfiguration coreConfiguration,
                                     CloudConfiguration cloudConfiguration,
                                     Context dependencyInjectableContext,
                                     PluginManager pluginManager,
                                     CloudAuditStore driver) {
        super(coreConfiguration, dependencyInjectableContext, pluginManager, driver);
        this.cloudConfiguration = cloudConfiguration;
    }

    @Override
    protected CloudFlamingockBuilder getSelf() {
        return this;
    }


    @Override
    protected void doUpdateContext() {
        addDependency(FlamingockEdition.CLOUD);
        addDependency(cloudConfiguration);
    }

    @Override
    protected ExecutionPlanner buildExecutionPlanner(RunnerId runnerId) {
        //TODO remove this once this builder is moved to cloud module
        return ((CloudAuditStore)auditStore).getPersistence().getExecutionPlanner();
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
}
