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
package io.flamingock.community.mongodb.springdata.internal;

import io.flamingock.internal.core.builder.core.CoreConfigurable;
import io.flamingock.internal.core.builder.local.CommunityConfigurable;
import io.flamingock.internal.core.community.AbstractLocalEngine;
import io.flamingock.internal.core.community.LocalAuditor;
import io.flamingock.internal.core.community.LocalExecutionPlanner;
import io.flamingock.internal.core.transaction.TransactionWrapper;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.targetsystem.mongodb.springdata.MongoSpringDataTargetSystem;

import java.util.Optional;

public class SpringDataMongoEngine extends AbstractLocalEngine {

    private final CoreConfigurable coreConfiguration;
    private final MongoSpringDataTargetSystem targetSystem;
    private final String auditCollectionName;
    private final String lockCollectionName;
    private SpringDataMongoAuditor auditor;
    private LocalExecutionPlanner executionPlanner;


    public SpringDataMongoEngine(MongoSpringDataTargetSystem targetSystem,
                                 String auditCollectionName,
                                 String lockCollectionName,
                                 CoreConfigurable coreConfiguration,
                                 CommunityConfigurable localConfiguration) {
        super(localConfiguration);
        this.targetSystem = targetSystem;
        this.auditCollectionName = auditCollectionName;
        this.lockCollectionName = lockCollectionName;
        this.coreConfiguration = coreConfiguration;
    }

    @Override
    protected void doInitialize(RunnerId runnerId) {

        auditor = new SpringDataMongoAuditor(targetSystem, auditCollectionName);

        auditor.initialize(targetSystem.isAutoCreate());

        SpringDataMongoLockService lockService = new SpringDataMongoLockService(targetSystem, lockCollectionName);
        lockService.initialize(targetSystem.isAutoCreate());
        executionPlanner = new LocalExecutionPlanner(runnerId, lockService, auditor, coreConfiguration);
    }

    @Override
    public LocalAuditor getAuditWriter() {
        return auditor;
    }

    @Override
    public LocalExecutionPlanner getExecutionPlanner() {
        return executionPlanner;
    }

    //TODO remove
    @Override
    @Deprecated
    public Optional<TransactionWrapper> getTransactionWrapper() {
        return Optional.of(targetSystem.getTxWrapper());
    }

}
