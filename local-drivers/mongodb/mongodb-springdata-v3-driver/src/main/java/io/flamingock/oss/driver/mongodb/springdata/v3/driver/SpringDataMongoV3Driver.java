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

package io.flamingock.oss.driver.mongodb.springdata.v3.driver;

import io.flamingock.core.configurator.core.CoreConfigurable;
import io.flamingock.core.configurator.local.LocalConfigurable;
import io.flamingock.oss.driver.mongodb.springdata.v3.config.SpringDataMongoV3Configuration;
import io.flamingock.oss.driver.mongodb.springdata.v3.internal.SpringDataMongoV3Engine;
import io.flamingock.core.engine.local.driver.ConnectionDriver;
import io.flamingock.core.engine.local.LocalConnectionEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;

public class SpringDataMongoV3Driver implements ConnectionDriver<SpringDataMongoV3Configuration> {
    private static final Logger logger = LoggerFactory.getLogger(SpringDataMongoV3Driver.class);


    private final MongoTemplate mongoTemplate;

    private SpringDataMongoV3Configuration driverConfiguration;


    @Deprecated
    public static SpringDataMongoV3Driver withLockStrategy(MongoTemplate mongoTemplate,
                                                    @Deprecated long lockAcquiredForMillis,
                                                    @Deprecated long lockQuitTryingAfterMillis,
                                                    @Deprecated long lockTryFrequencyMillis) {
        logWarningFieldIgnored("lockAcquiredForMillis", lockAcquiredForMillis);
        logWarningFieldIgnored("lockQuitTryingAfterMillis", lockQuitTryingAfterMillis);
        logWarningFieldIgnored("lockTryFrequencyMillis", lockTryFrequencyMillis);
        return new SpringDataMongoV3Driver(mongoTemplate);
    }

    @Deprecated
    public static SpringDataMongoV3Driver withDefaultLock(MongoTemplate mongoTemplate) {
        return new SpringDataMongoV3Driver(mongoTemplate);
    }


    public SpringDataMongoV3Driver(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public SpringDataMongoV3Driver setDriverConfiguration(SpringDataMongoV3Configuration driverConfiguration) {
        this.driverConfiguration = driverConfiguration;
        return this;
    }

    @Override
    public LocalConnectionEngine getConnectionEngine(CoreConfigurable coreConfiguration, LocalConfigurable communityConfiguration) {
        return new SpringDataMongoV3Engine(
                mongoTemplate,
                coreConfiguration,
                communityConfiguration,
                driverConfiguration != null ? driverConfiguration : SpringDataMongoV3Configuration.getDefault());
    }

    private static void logWarningFieldIgnored(String name, long value) {
        logger.warn("Parameter[{}] with value[{}] will be ignored. It needs to be injected in the configuration",
                name, value);
    }

}
