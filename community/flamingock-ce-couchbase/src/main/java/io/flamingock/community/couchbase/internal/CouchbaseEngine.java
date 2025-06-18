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

package io.flamingock.community.couchbase.internal;

import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import io.flamingock.internal.core.community.LocalExecutionPlanner;
import io.flamingock.internal.core.builder.core.CoreConfigurable;
import io.flamingock.internal.core.builder.local.CommunityConfigurable;
import io.flamingock.internal.core.community.AbstractLocalEngine;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.internal.core.transaction.TransactionWrapper;
import io.flamingock.internal.util.TimeService;
import io.flamingock.community.couchbase.CouchbaseConfiguration;

import java.util.Optional;

public class CouchbaseEngine extends AbstractLocalEngine {

    private final Collection collection;
    private final Cluster cluster;

    private CouchbaseAuditor auditor;
    private LocalExecutionPlanner executionPlanner;
    private final CouchbaseConfiguration driverConfiguration;
    private final CoreConfigurable coreConfiguration;


    public CouchbaseEngine(Cluster cluster,
                           Collection collection,
                           CoreConfigurable coreConfiguration,
                           CommunityConfigurable localConfiguration,
                           CouchbaseConfiguration driverConfiguration) {
        super(localConfiguration);
        this.cluster = cluster;
        this.collection = collection;
        this.driverConfiguration = driverConfiguration;
        this.coreConfiguration = coreConfiguration;
    }

    @Override
    protected void doInitialize(RunnerId runnerId) {
        auditor = new CouchbaseAuditor(cluster, collection);
        auditor.initialize(driverConfiguration.isAutoCreate());
        CouchbaseLockService lockService = new CouchbaseLockService(cluster, collection, TimeService.getDefault());
        lockService.initialize(driverConfiguration.isAutoCreate());
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


    @Override
    public Optional<TransactionWrapper> getTransactionWrapper() {
        return Optional.empty();
    }
}
