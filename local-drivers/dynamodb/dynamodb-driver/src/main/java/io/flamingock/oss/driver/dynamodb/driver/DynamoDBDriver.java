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

import io.flamingock.commons.utils.id.RunnerId;
import io.flamingock.internal.core.builder.core.CoreConfigurable;
import io.flamingock.internal.core.builder.local.CommunityConfigurable;
import io.flamingock.internal.core.community.LocalEngine;
import io.flamingock.internal.core.community.driver.LocalDriver;
import io.flamingock.internal.core.context.ContextResolver;
import io.flamingock.oss.driver.dynamodb.DynamoDBConfiguration;
import io.flamingock.oss.driver.dynamodb.internal.DynamoDBEngine;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class DynamoDBDriver implements LocalDriver {

    private DynamoDbClient client;
    private RunnerId runnerId;
    private CoreConfigurable coreConfiguration;
    private CommunityConfigurable communityConfiguration;
    private DynamoDBConfiguration driverConfiguration;

    public DynamoDBDriver() {
    }

    @Override
    public void initialize(ContextResolver dependencyContext) {
        runnerId = dependencyContext.getRequiredDependencyValue(RunnerId.class);

        coreConfiguration = dependencyContext.getRequiredDependencyValue(CoreConfigurable.class);
        communityConfiguration = dependencyContext.getRequiredDependencyValue(CommunityConfigurable.class);

        this.client = dependencyContext.getRequiredDependencyValue(DynamoDbClient.class);

        this.driverConfiguration = dependencyContext.getDependencyValue(DynamoDBConfiguration.class)
                .orElse(new DynamoDBConfiguration());
        this.driverConfiguration.mergeConfig(dependencyContext);
    }

    @Override
    public LocalEngine getEngine() {
        DynamoDBEngine dynamodbEngine = new DynamoDBEngine(
                client,
                coreConfiguration,
                communityConfiguration,
                driverConfiguration);
        dynamodbEngine.initialize(runnerId);
        return dynamodbEngine;
    }

}
