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

package io.flamingock.oss.driver.dynamodb.driver;

import io.flamingock.commons.utils.RunnerId;
import io.flamingock.core.configurator.core.CoreConfigurable;
import io.flamingock.core.configurator.local.LocalConfigurable;
import io.flamingock.core.engine.local.LocalConnectionEngine;
import io.flamingock.core.engine.local.driver.ConnectionDriver;
import io.flamingock.oss.driver.dynamodb.DynamoDBConfiguration;
import io.flamingock.oss.driver.dynamodb.internal.DynamoDBEngine;
import io.flamingock.oss.driver.dynamodb.internal.util.DynamoClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class DynamoDBDriver implements ConnectionDriver<DynamoDBConfiguration> {

    private static final Logger logger = LoggerFactory.getLogger(DynamoDBDriver.class);

    private final DynamoClients client;

    private DynamoDBConfiguration driverConfiguration;


    public DynamoDBDriver(DynamoDbClient client) {
        this.client = new DynamoClients(client);
    }

    @Deprecated
    public static DynamoDBDriver withLockStrategy(DynamoDbClient client,
                                                  @Deprecated long lockAcquiredForMillis,
                                                  @Deprecated long lockQuitTryingAfterMillis,
                                                  @Deprecated long lockTryFrequencyMillis) {
        logWarningFieldIgnored("lockAcquiredForMillis", lockAcquiredForMillis);
        logWarningFieldIgnored("lockQuitTryingAfterMillis", lockQuitTryingAfterMillis);
        logWarningFieldIgnored("lockTryFrequencyMillis", lockTryFrequencyMillis);
        return new DynamoDBDriver(client);
    }

    @Deprecated
    public static DynamoDBDriver withDefaultLock(DynamoDbClient client) {
        return new DynamoDBDriver(client);
    }

    private static void logWarningFieldIgnored(String name, long value) {
        logger.warn("Parameter[{}] with value[{}] will be ignored. It needs to be injected in the configuration",
                name, value);
    }

    @Override
    public DynamoDBDriver setDriverConfiguration(DynamoDBConfiguration driverConfiguration) {
        this.driverConfiguration = driverConfiguration;
        return this;
    }

    @Override
    public LocalConnectionEngine initializeAndGetEngine(RunnerId runnerId, CoreConfigurable coreConfiguration, LocalConfigurable communityConfiguration) {
        DynamoDBEngine dynamodbEngine = new DynamoDBEngine(
                client,
                coreConfiguration,
                communityConfiguration,
                driverConfiguration != null ? driverConfiguration : DynamoDBConfiguration.getDefault());
        dynamodbEngine.initialize(runnerId);
        return dynamodbEngine;
    }

}
