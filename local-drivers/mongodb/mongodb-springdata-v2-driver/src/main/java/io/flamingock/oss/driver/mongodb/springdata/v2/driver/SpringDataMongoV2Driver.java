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

package io.flamingock.oss.driver.mongodb.springdata.v2.driver;

import io.flamingock.commons.utils.RunnerId;
import io.flamingock.core.configurator.core.CoreConfigurable;
import io.flamingock.core.configurator.local.LocalConfigurable;
import io.flamingock.oss.driver.mongodb.springdata.v2.config.SpringDataMongoV2Configuration;
import io.flamingock.oss.driver.mongodb.springdata.v2.internal.SpringDataMongoV2Engine;
import io.flamingock.core.local.driver.LocalDriver;
import io.flamingock.core.local.LocalEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;

public class SpringDataMongoV2Driver implements LocalDriver<SpringDataMongoV2Configuration> {
    private static final Logger logger = LoggerFactory.getLogger(SpringDataMongoV2Driver.class);


    private final MongoTemplate mongoTemplate;

    private SpringDataMongoV2Configuration driverConfiguration;


    @Deprecated
    public static SpringDataMongoV2Driver withLockStrategy(MongoTemplate mongoTemplate,
                                                    @Deprecated long lockAcquiredForMillis,
                                                    @Deprecated long lockQuitTryingAfterMillis,
                                                    @Deprecated long lockTryFrequencyMillis) {
        logWarningFieldIgnored("lockAcquiredForMillis", lockAcquiredForMillis);
        logWarningFieldIgnored("lockQuitTryingAfterMillis", lockQuitTryingAfterMillis);
        logWarningFieldIgnored("lockTryFrequencyMillis", lockTryFrequencyMillis);
        return new SpringDataMongoV2Driver(mongoTemplate);
    }

    @Deprecated
    public static SpringDataMongoV2Driver withDefaultLock(MongoTemplate mongoTemplate) {
        return new SpringDataMongoV2Driver(mongoTemplate);
    }


    public SpringDataMongoV2Driver(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public SpringDataMongoV2Driver setDriverConfiguration(SpringDataMongoV2Configuration driverConfiguration) {
        this.driverConfiguration = driverConfiguration;
        return this;
    }

    @Override
    public LocalEngine initializeAndGetEngine(RunnerId runnerId, CoreConfigurable coreConfiguration, LocalConfigurable localConfiguration) {
        SpringDataMongoV2Engine engine = new SpringDataMongoV2Engine(
                mongoTemplate,
                coreConfiguration,
                localConfiguration,
                driverConfiguration != null ? driverConfiguration : SpringDataMongoV2Configuration.getDefault());
        engine.initialize(runnerId);
        return engine;
    }

    private static void logWarningFieldIgnored(String name, long value) {
        logger.warn("Parameter[{}] with value[{}] will be ignored. It needs to be injected in the configuration",
                name, value);
    }

}
