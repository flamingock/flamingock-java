/*
 * Copyright 2023 Flamingock (https://www.flamingock.io)
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
package io.flamingock.community.dynamodb.driver;

import io.flamingock.api.targets.TargetSystem;
import io.flamingock.internal.core.targets.DefaultTargetSystem;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.internal.core.builder.core.CoreConfigurable;
import io.flamingock.internal.core.builder.local.CommunityConfigurable;
import io.flamingock.internal.core.community.LocalEngine;
import io.flamingock.internal.core.community.driver.LocalDriver;
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.community.dynamodb.DynamoDBConfiguration;
import io.flamingock.community.dynamodb.internal.DynamoDBEngine;
import io.flamingock.targetsystem.dynamodb.DynamoDBTargetSystem;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class DynamoDBDriver implements LocalDriver {

    private RunnerId runnerId;
    private DynamoDBTargetSystem targetSystem;
    private CoreConfigurable coreConfiguration;
    private CommunityConfigurable communityConfiguration;
    private DynamoDBConfiguration driverConfiguration;

    public static DynamoDBDriver fromTargetSystem(DynamoDBTargetSystem targetSystem) {
        return new DynamoDBDriver(targetSystem);
    }

    public DynamoDBDriver() {
        this(null);
    }

    public DynamoDBDriver(DynamoDBTargetSystem targetSystem) {
        this.targetSystem = targetSystem;
    }

    @Override
    public void initialize(ContextResolver baseContext) {
        runnerId = baseContext.getRequiredDependencyValue(RunnerId.class);
        coreConfiguration = baseContext.getRequiredDependencyValue(CoreConfigurable.class);
        communityConfiguration = baseContext.getRequiredDependencyValue(CommunityConfigurable.class);

        this.driverConfiguration = baseContext.getDependencyValue(DynamoDBConfiguration.class)
                .orElse(new DynamoDBConfiguration());
        this.driverConfiguration.mergeConfig(baseContext);

        if(targetSystem == null) {
            DynamoDbClient client = baseContext.getRequiredDependencyValue(DynamoDbClient.class);


            targetSystem = new DynamoDBTargetSystem(DEFAULT_AUDIT_STORE_TARGET_SYSTEM)
                    .withDynamoDBClient(client);
        }

    }

    @Override
    public LocalEngine getEngine() {
        DynamoDBEngine dynamodbEngine = new DynamoDBEngine(
                targetSystem,
                driverConfiguration.getAuditRepositoryName(),
                driverConfiguration.getLockRepositoryName(),
                driverConfiguration.getReadCapacityUnits(),
                driverConfiguration.getWriteCapacityUnits(),
                coreConfiguration,
                communityConfiguration);
        dynamodbEngine.initialize(runnerId);
        return dynamodbEngine;
    }

    @Override
    public TargetSystem getTargetSystem() {
        return targetSystem;
    }

}
