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

package io.flamingock.core.configurator.cloud;

import io.flamingock.core.cloud.transaction.CloudTransactioner;
import io.flamingock.core.configurator.ConfigurationDelegate;

import java.util.Optional;
import java.util.function.Supplier;

public class CloudConfiguratorDelegate<HOLDER> implements CloudConfigurator<HOLDER>, ConfigurationDelegate {

    private final Supplier<HOLDER> holderSupplier;

    private final CloudConfigurable cloudConfiguration;

    private CloudTransactioner cloudTransactioner;

    public CloudConfiguratorDelegate(CloudConfigurable cloudConfiguration,
                                     Supplier<HOLDER> holderSupplier) {
        this.holderSupplier = holderSupplier;
        this.cloudConfiguration = cloudConfiguration;

    }

    @Override
    public HOLDER setHost(String host) {
        cloudConfiguration.setHost(host);
        return holderSupplier.get();
    }

    @Override
    public HOLDER setService(String service) {
        cloudConfiguration.setServiceName(service);
        return holderSupplier.get();
    }

    @Override
    public HOLDER setEnvironment(String environment) {
        cloudConfiguration.setEnvironmentName(environment);
        return holderSupplier.get();
    }

    @Override
    public HOLDER setApiToken(String clientSecret) {
        cloudConfiguration.setApiToken(clientSecret);
        return holderSupplier.get();
    }

    @Override
    public HOLDER setCloudTransactioner(CloudTransactioner cloudTransactioner) {
        this.cloudTransactioner = cloudTransactioner;
        return holderSupplier.get();
    }

    @Override
    public Optional<CloudTransactioner> getCloudTransactioner() {
        return Optional.ofNullable(cloudTransactioner);
    }

    public CloudConfigurable getCloudConfiguration() {
        return cloudConfiguration;
    }

    @Override
    public void initialize() {

    }
}
