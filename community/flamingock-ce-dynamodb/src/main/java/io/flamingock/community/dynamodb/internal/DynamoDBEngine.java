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
package io.flamingock.community.dynamodb.internal;

import io.flamingock.internal.core.builder.core.CoreConfigurable;
import io.flamingock.internal.core.builder.local.CommunityConfigurable;
import io.flamingock.internal.core.community.AbstractLocalEngine;
import io.flamingock.internal.core.community.LocalExecutionPlanner;
import io.flamingock.internal.util.TimeService;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.targetsystem.dynamodb.DynamoDBTargetSystem;

public class DynamoDBEngine extends AbstractLocalEngine {

    private final DynamoDBTargetSystem targetSystem;
    private final String lockTableName;
    private final String auditTableName;
    private final long readCapacityUnits;
    private final long writeCapacityUnits;

    private final CoreConfigurable coreConfiguration;
    private DynamoDBAuditor auditor;
    private LocalExecutionPlanner executionPlanner;


    public DynamoDBEngine(DynamoDBTargetSystem targetSystem,
                          String auditTableName,
                          String lockTableName,
                          long readCapacityUnits,
                          long writeCapacityUnits,
                          CoreConfigurable coreConfiguration,
                          CommunityConfigurable localConfiguration) {
        super(localConfiguration);
        this.targetSystem = targetSystem;
        this.auditTableName = auditTableName;
        this.lockTableName = lockTableName;
        this.readCapacityUnits = readCapacityUnits;
        this.writeCapacityUnits = writeCapacityUnits;
        this.coreConfiguration = coreConfiguration;
    }

    @Override
    protected void doInitialize(RunnerId runnerId) {
        auditor = new DynamoDBAuditor(targetSystem);
        auditor.initialize(
                targetSystem.isAutoCreate(),
                auditTableName,
                readCapacityUnits,
                writeCapacityUnits);

        DynamoDBLockService lockService = new DynamoDBLockService(targetSystem, TimeService.getDefault());
        lockService.initialize(
                targetSystem.isAutoCreate(),
                lockTableName,
                readCapacityUnits,
                writeCapacityUnits);
        executionPlanner = new LocalExecutionPlanner(runnerId, lockService, auditor, coreConfiguration);
    }

    @Override
    public DynamoDBAuditor getAuditWriter() {
        return auditor;
    }

    @Override
    public LocalExecutionPlanner getExecutionPlanner() {
        return executionPlanner;
    }
}