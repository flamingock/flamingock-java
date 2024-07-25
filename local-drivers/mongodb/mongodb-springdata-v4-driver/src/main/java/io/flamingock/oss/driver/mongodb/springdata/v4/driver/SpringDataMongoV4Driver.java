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

package io.flamingock.oss.driver.mongodb.springdata.v4.driver;

import io.flamingock.core.configurator.core.CoreConfigurable;
import io.flamingock.core.configurator.local.LocalConfigurable;
import io.flamingock.oss.driver.mongodb.springdata.v4.config.SpringDataMongoV4Configuration;
import io.flamingock.oss.driver.mongodb.springdata.v4.internal.SpringDataMongoV4Engine;
import io.flamingock.core.engine.local.driver.ConnectionDriver;
import io.flamingock.core.engine.local.LocalConnectionEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;

public class SpringDataMongoV4Driver implements ConnectionDriver<SpringDataMongoV4Configuration> {
    private static final Logger logger = LoggerFactory.getLogger(SpringDataMongoV4Driver.class);


    private final MongoTemplate mongoTemplate;

    private SpringDataMongoV4Configuration driverConfiguration;


    @Deprecated
    public static SpringDataMongoV4Driver withLockStrategy(MongoTemplate mongoTemplate,
                                                    @Deprecated long lockAcquiredForMillis,
                                                    @Deprecated long lockQuitTryingAfterMillis,
                                                    @Deprecated long lockTryFrequencyMillis) {
        logWarningFieldIgnored("lockAcquiredForMillis", lockAcquiredForMillis);
        logWarningFieldIgnored("lockQuitTryingAfterMillis", lockQuitTryingAfterMillis);
        logWarningFieldIgnored("lockTryFrequencyMillis", lockTryFrequencyMillis);
        return new SpringDataMongoV4Driver(mongoTemplate);
    }

    @Deprecated
    public static SpringDataMongoV4Driver withDefaultLock(MongoTemplate mongoTemplate) {
        return new SpringDataMongoV4Driver(mongoTemplate);
    }


    public SpringDataMongoV4Driver(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public SpringDataMongoV4Driver setDriverConfiguration(SpringDataMongoV4Configuration driverConfiguration) {
        this.driverConfiguration = driverConfiguration;
        return this;
    }

    @Override
    public LocalConnectionEngine getConnectionEngine(CoreConfigurable coreConfiguration, LocalConfigurable localConfiguration) {
        return new SpringDataMongoV4Engine(
                mongoTemplate,
                coreConfiguration,
                localConfiguration,
                driverConfiguration != null ? driverConfiguration : SpringDataMongoV4Configuration.getDefault());
    }

    private static void logWarningFieldIgnored(String name, long value) {
        logger.warn("Parameter[{}] with value[{}] will be ignored. It needs to be injected in the configuration",
                name, value);
    }

}
