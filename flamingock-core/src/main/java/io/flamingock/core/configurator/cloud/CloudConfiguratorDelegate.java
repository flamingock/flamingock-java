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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.flamingock.core.cloud.transaction.CloudTransactioner;
import io.flamingock.core.configurator.core.CoreConfigurable;
import io.flamingock.core.cloud.CloudConnectionEngine;
import io.flamingock.commons.utils.RunnerId;
import io.flamingock.commons.utils.http.Http;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.function.Supplier;

import static com.fasterxml.jackson.databind.MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS;

public class CloudConfiguratorDelegate<HOLDER> implements CloudConfigurator<HOLDER> {

    private static final Logger logger = LoggerFactory.getLogger(CloudConfiguratorDelegate.class);

    private final static ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .enable(ACCEPT_CASE_INSENSITIVE_ENUMS)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .serializationInclusion(JsonInclude.Include.NON_NULL)
//            .registerModule(Jdk8Module())
//            .registerModule(JavaTimeModule()) //No needed for now
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(SerializationFeature.FAIL_ON_UNWRAPPED_TYPE_IDENTIFIERS)
            .build();

    private final Supplier<HOLDER> holderSupplier;

    private final CoreConfigurable coreConfiguration;

    private final CloudConfigurable cloudConfiguration;

    private CloudTransactioner cloudTransactioner;

    public CloudConfiguratorDelegate(CoreConfigurable coreConfiguration,
                                     CloudConfigurable cloudConfiguration,
                                     Supplier<HOLDER> holderSupplier) {
        this.holderSupplier = holderSupplier;
        this.coreConfiguration = coreConfiguration;
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

    public CloudConnectionEngine getAndInitializeConnectionEngine(RunnerId runnerId) {
        logger.info("Generated runnerId:  {}", runnerId);
        CloudConnectionEngine connectionEngine = new CloudConnectionEngine(
                coreConfiguration,
                cloudConfiguration,
                Http.builderFactory(HttpClients.createDefault(), OBJECT_MAPPER),
                cloudTransactioner
        );
        connectionEngine.initialize(runnerId);
        return connectionEngine;
    }
}
