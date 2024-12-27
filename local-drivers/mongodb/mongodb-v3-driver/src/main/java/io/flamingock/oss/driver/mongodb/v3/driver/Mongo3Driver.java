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

package io.flamingock.oss.driver.mongodb.v3.driver;

import com.mongodb.client.MongoClient;
import io.flamingock.commons.utils.RunnerId;
import io.flamingock.core.configurator.core.CoreConfigurable;
import io.flamingock.core.configurator.local.LocalConfigurable;
import io.flamingock.core.local.LocalEngine;
import io.flamingock.core.local.driver.LocalDriver;
import io.flamingock.oss.driver.mongodb.v3.MongoDB3Configuration;
import io.flamingock.oss.driver.mongodb.v3.internal.Mongo3Engine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Mongo3Driver implements LocalDriver<MongoDB3Configuration> {
    private static final Logger logger = LoggerFactory.getLogger(Mongo3Driver.class);


    private final MongoClient mongoClient;

    private final String databaseName;

    private MongoDB3Configuration driverConfiguration;


    @Deprecated
    public static Mongo3Driver withLockStrategy(MongoClient mongoClient,
                                                String databaseName,
                                                @Deprecated long lockAcquiredForMillis,
                                                @Deprecated long lockQuitTryingAfterMillis,
                                                @Deprecated long lockTryFrequencyMillis) {
        logWarningFieldIgnored("lockAcquiredForMillis", lockAcquiredForMillis);
        logWarningFieldIgnored("lockQuitTryingAfterMillis", lockQuitTryingAfterMillis);
        logWarningFieldIgnored("lockTryFrequencyMillis", lockTryFrequencyMillis);
        return new Mongo3Driver(mongoClient, databaseName);
    }

    @Deprecated
    public static Mongo3Driver withDefaultLock(MongoClient mongoClient, String databaseName) {
        return new Mongo3Driver(mongoClient, databaseName);
    }


    public Mongo3Driver(MongoClient mongoClient, String databaseName) {
        this.mongoClient = mongoClient;
        this.databaseName = databaseName;
    }

    @Override
    public Mongo3Driver setDriverConfiguration(MongoDB3Configuration driverConfiguration) {
        this.driverConfiguration = driverConfiguration;
        return this;
    }

    @Override
    public LocalEngine initializeAndGetEngine(RunnerId runnerId, CoreConfigurable coreConfiguration, LocalConfigurable localConfiguration) {
        Mongo3Engine engine = new Mongo3Engine(
                mongoClient,
                databaseName,
                coreConfiguration,
                localConfiguration,
                driverConfiguration != null ? driverConfiguration : MongoDB3Configuration.getDefault());

        engine.initialize(runnerId);
        return engine;
    }

    private static void logWarningFieldIgnored(String name, long value) {
        logger.warn("Parameter[{}] with value[{}] will be ignored. It needs to be injected in the configuration",
                name, value);
    }

}
