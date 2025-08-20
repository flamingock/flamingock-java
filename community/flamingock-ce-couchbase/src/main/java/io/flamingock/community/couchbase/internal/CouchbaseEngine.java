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
package io.flamingock.community.couchbase.internal;

import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Bucket;
import io.flamingock.internal.core.community.LocalExecutionPlanner;
import io.flamingock.internal.core.builder.core.CoreConfigurable;
import io.flamingock.internal.core.builder.local.CommunityConfigurable;
import io.flamingock.internal.core.community.AbstractLocalEngine;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.internal.core.transaction.TransactionWrapper;
import io.flamingock.internal.util.TimeService;
import io.flamingock.community.couchbase.CouchbaseConfiguration;
import io.flamingock.targetsystem.couchbase.CouchbaseTargetSystem;

import java.util.Optional;

public class CouchbaseEngine extends AbstractLocalEngine {

    private final CouchbaseTargetSystem targetSystem;
    private CouchbaseAuditor auditor;
    private LocalExecutionPlanner executionPlanner;
    private final CouchbaseConfiguration driverConfiguration;
    private final CoreConfigurable coreConfiguration;


    public CouchbaseEngine(CouchbaseTargetSystem targetSystem,
                           CoreConfigurable coreConfiguration,
                           CommunityConfigurable localConfiguration,
                           CouchbaseConfiguration driverConfiguration) {
        super(localConfiguration);
        this.targetSystem = targetSystem;
        this.coreConfiguration = coreConfiguration;
        this.driverConfiguration = driverConfiguration;
    }

    @Override
    protected void doInitialize(RunnerId runnerId) {
        auditor = new CouchbaseAuditor(targetSystem);
        auditor.initialize(targetSystem.isAutoCreate(), driverConfiguration.getScopeName(), driverConfiguration.getAuditRepositoryName());
        CouchbaseLockService lockService = new CouchbaseLockService(targetSystem, TimeService.getDefault());
        lockService.initialize(driverConfiguration.isAutoCreate(), driverConfiguration.getScopeName(), driverConfiguration.getLockRepositoryName());
        executionPlanner = new LocalExecutionPlanner(runnerId, lockService, auditor, coreConfiguration);
    }

    @Override
    public CouchbaseAuditor getAuditWriter() {
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
