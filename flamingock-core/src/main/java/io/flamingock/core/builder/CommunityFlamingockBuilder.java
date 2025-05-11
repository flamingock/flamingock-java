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

import io.flamingock.commons.utils.RunnerId;
import io.flamingock.core.api.exception.FlamingockException;
import io.flamingock.core.builder.core.CoreConfiguration;
import io.flamingock.core.builder.local.CommunityConfiguration;
import io.flamingock.core.builder.local.CommunityConfigurator;
import io.flamingock.core.builder.local.LocalSystemModuleManager;
import io.flamingock.core.community.driver.SpringdataDriver;
import io.flamingock.core.engine.ConnectionEngine;
import io.flamingock.core.community.LocalEngine;
import io.flamingock.core.community.driver.LocalDriver;
import io.flamingock.core.runtime.dependency.DependencyInjectableContext;

import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class CommunityFlamingockBuilder
        extends AbstractFlamingockBuilder<CommunityFlamingockBuilder>
        implements CommunityConfigurator<CommunityFlamingockBuilder> {

    private final LocalSystemModuleManager systemModuleManager;

    private final CommunityConfiguration communityConfiguration;

    private LocalDriver<?> connectionDriver;

    private LocalEngine engine;

    protected CommunityFlamingockBuilder(CoreConfiguration coreConfiguration,
                                         CommunityConfiguration communityConfiguration,
                                         DependencyInjectableContext dependencyInjectableContext,
                                         LocalSystemModuleManager systemModuleManager) {
        super(coreConfiguration, dependencyInjectableContext, systemModuleManager);
        this.communityConfiguration = communityConfiguration;
        this.systemModuleManager = systemModuleManager;
        this.connectionDriver = getLocalDriver();
    }

    @Override
    protected CommunityFlamingockBuilder getSelf() {
        return this;
    }

    private LocalDriver<?> getLocalDriver() {

        List<SpringdataDriver> springdataDrivers = StreamSupport
                .stream(ServiceLoader.load(SpringdataDriver.class).spliterator(), false)
                .collect(Collectors.toList());
        if (springdataDrivers.size() == 1) {
            return springdataDrivers.get(0);
        } else if (springdataDrivers.size() > 1) { //TODO: Check Exceptions Messages
            throw new FlamingockException("Only one driver is permitted");
        }

        List<LocalDriver> drivers = StreamSupport
                .stream(ServiceLoader.load(LocalDriver.class).spliterator(), false)
                .collect(Collectors.toList());
        if (drivers.size() == 1) {
            return drivers.get(0);
        } else if (drivers.size() > 1) { //TODO: Check Exceptions Messages
            throw new FlamingockException("Only one driver is permitted");
        } else {
            return null;
            //throw new FlamingockException("No driver dependency found");
        }
    }

    @Override
    protected ConnectionEngine getConnectionEngine(RunnerId runnerId) {
        connectionDriver.initialize(dependencyContext);
        engine = connectionDriver.initializeAndGetEngine(
                runnerId,
                coreConfiguration,
                communityConfiguration
        );
        return engine;
    }

    @Override
    protected void configureSystemModules() {
        //TODO change this
        engine.getMongockLegacyImporterModule().ifPresent(systemModuleManager::add);
        systemModuleManager.initialize();
    }

    @Override
    @Deprecated
    public CommunityFlamingockBuilder setDriver(LocalDriver<?> connectionDriver) {
        this.connectionDriver = connectionDriver;
        return this;
    }

    @Override
    public LocalDriver<?> getDriver() {
        return connectionDriver;
    }

    @Override
    public CommunityFlamingockBuilder disableTransaction() {
        communityConfiguration.setTransactionDisabled(true);
        return this;
    }

    @Override
    public boolean isTransactionDisabled() {
        return communityConfiguration.isTransactionDisabled();
    }
}
