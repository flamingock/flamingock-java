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
package io.flamingock.community.mongodb.sync.internal;

import com.mongodb.ReadConcern;
import com.mongodb.client.ClientSession;
import io.flamingock.api.targets.TargetSystem;
import io.flamingock.community.mongodb.sync.MongoDBSyncConfiguration;
import io.flamingock.internal.core.builder.core.CoreConfigurable;
import io.flamingock.internal.core.builder.local.CommunityConfigurable;
import io.flamingock.internal.core.community.AbstractLocalEngine;
import io.flamingock.internal.core.community.LocalAuditor;
import io.flamingock.internal.core.community.LocalExecutionPlanner;
import io.flamingock.internal.core.community.TransactionManager;
import io.flamingock.internal.core.transaction.TransactionWrapper;
import io.flamingock.internal.util.TimeService;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.targetystem.mongodb.sync.MongoSyncTargetSystem;

import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class MongoSyncEngine extends AbstractLocalEngine {

    private MongoSyncAuditor auditor;
    private MongoSyncTargetSystem targetSystem;
    private final String lockCollectionName;
    private final String auditCollectionName;
    private LocalExecutionPlanner executionPlanner;
    private final CoreConfigurable coreConfiguration;


    public MongoSyncEngine(MongoSyncTargetSystem targetSystem,
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


        //Auditor
        auditor = buildAuditor();
        auditor.initialize(targetSystem.isAutoCreate());

        //Lock
        MongoSyncLockService lockService = buildLockService();
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

    @Deprecated
    @Override
    public Set<Class<?>> getNonGuardedTypes() {
        return new HashSet<>(Collections.singletonList(ClientSession.class));
    }

    //TODO remove
    @Override
    @Deprecated
    public Optional<TransactionWrapper> getTransactionWrapper() {
        return Optional.of(targetSystem.getTxWrapper());
    }


    private MongoSyncAuditor buildAuditor() {
        return new MongoSyncAuditor(targetSystem, auditCollectionName);
    }

    private MongoSyncLockService buildLockService() {
        return new MongoSyncLockService(
                targetSystem,
                lockCollectionName,
                TimeService.getDefault());
    }

}
