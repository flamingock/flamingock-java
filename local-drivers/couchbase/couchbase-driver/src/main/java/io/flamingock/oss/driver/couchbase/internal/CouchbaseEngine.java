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

package io.flamingock.oss.driver.couchbase.internal;

import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import io.flamingock.community.internal.LocalExecutionPlanner;
import io.flamingock.core.configurator.core.CoreConfigurable;
import io.flamingock.core.configurator.local.LocalConfigurable;
import io.flamingock.core.engine.local.LocalConnectionEngine;
import io.flamingock.core.runner.RunnerId;
import io.flamingock.core.transaction.TransactionWrapper;
import io.flamingock.core.util.TimeService;
import io.flamingock.oss.driver.couchbase.CouchbaseConfiguration;

import java.util.Optional;

public class CouchbaseEngine implements LocalConnectionEngine {

    private final Collection collection;
    private final Cluster cluster;
    private final LocalConfigurable LocalConfiguration;

    private CouchbaseAuditor auditor;
    private LocalExecutionPlanner executionPlanner;
    private final CouchbaseConfiguration driverConfiguration;
    private final CoreConfigurable coreConfiguration;


    public CouchbaseEngine(Cluster cluster,
                           Collection collection,
                           CoreConfigurable coreConfiguration,
                           LocalConfigurable LocalConfiguration,
                           CouchbaseConfiguration driverConfiguration) {
        this.cluster = cluster;
        this.collection = collection;
        this.driverConfiguration = driverConfiguration;
        this.coreConfiguration = coreConfiguration;
        this.LocalConfiguration = LocalConfiguration;
    }

    @Override
    public void initialize(RunnerId runnerId) {
        auditor = new CouchbaseAuditor(cluster, collection);
        auditor.initialize(driverConfiguration.isIndexCreation());
        CouchbaseLockService lockService = new CouchbaseLockService(cluster, collection, TimeService.getDefault());
        lockService.initialize(driverConfiguration.isIndexCreation());
        executionPlanner = new LocalExecutionPlanner(runnerId, lockService, auditor, coreConfiguration);
    }

    @Override
    public CouchbaseAuditor getAuditor() {
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
