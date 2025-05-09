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

package io.flamingock.oss.driver.mongodb.sync.v4.driver;

import com.mongodb.client.MongoClient;
import io.flamingock.commons.utils.RunnerId;
import io.flamingock.core.builder.core.CoreConfigurable;
import io.flamingock.core.builder.local.CommunityConfigurable;
import io.flamingock.core.community.driver.LocalDriver;
import io.flamingock.core.community.LocalEngine;
import io.flamingock.cloud.transaction.mongodb.sync.v4.cofig.MongoDBSync4Configuration;
import io.flamingock.core.runtime.dependency.DependencyContext;
import io.flamingock.oss.driver.mongodb.sync.v4.internal.MongoSync4Engine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MongoSync4Driver implements LocalDriver<MongoDBSync4Configuration> {
    private static final Logger logger = LoggerFactory.getLogger(MongoSync4Driver.class);


    private final MongoClient mongoClient;

    private final String databaseName;

    private MongoDBSync4Configuration driverConfiguration;


    @Deprecated
    public static MongoSync4Driver withLockStrategy(MongoClient mongoClient,
                                                    String databaseName,
                                                    @Deprecated long lockAcquiredForMillis,
                                                    @Deprecated long lockQuitTryingAfterMillis,
                                                    @Deprecated long lockTryFrequencyMillis) {
        logWarningFieldIgnored("lockAcquiredForMillis", lockAcquiredForMillis);
        logWarningFieldIgnored("lockQuitTryingAfterMillis", lockQuitTryingAfterMillis);
        logWarningFieldIgnored("lockTryFrequencyMillis", lockTryFrequencyMillis);
        return new MongoSync4Driver(mongoClient, databaseName);
    }

    @Deprecated
    public static MongoSync4Driver withDefaultLock(MongoClient mongoClient, String databaseName) {
        return new MongoSync4Driver(mongoClient, databaseName);
    }


    public MongoSync4Driver(MongoClient mongoClient, String databaseName) {
        this.mongoClient = mongoClient;
        this.databaseName = databaseName;
    }

    @Override
    public void initialize(DependencyContext dependencyContext) {
        //TODO: Implement
    }

    @Override
    public MongoSync4Driver setDriverConfiguration(MongoDBSync4Configuration driverConfiguration) {
        this.driverConfiguration = driverConfiguration;
        return this;
    }


    @Override
    public LocalEngine initializeAndGetEngine(RunnerId runnerId, CoreConfigurable coreConfiguration, CommunityConfigurable localConfiguration) {
        MongoSync4Engine engine = new MongoSync4Engine(
                mongoClient,
                databaseName,
                coreConfiguration,
                localConfiguration,
                driverConfiguration != null ? driverConfiguration : MongoDBSync4Configuration.getDefault());
        engine.initialize(runnerId);
        return engine;
    }

    private static void logWarningFieldIgnored(String name, long value) {
        logger.warn("Parameter[{}] with value[{}] will be ignored. It needs to be injected in the configuration",
                name, value);
    }

}
