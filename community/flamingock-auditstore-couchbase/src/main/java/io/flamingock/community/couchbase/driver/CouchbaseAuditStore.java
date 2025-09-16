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
package io.flamingock.community.couchbase.driver;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import io.flamingock.api.targets.TargetSystem;
import io.flamingock.community.couchbase.internal.CouchbaseLockService;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.core.store.lock.community.CommunityLockService;
import io.flamingock.internal.util.TimeService;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.internal.core.configuration.community.CommunityConfigurable;
import io.flamingock.internal.core.store.audit.community.CommunityAuditPersistence;
import io.flamingock.internal.core.store.CommunityAuditStore;
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.community.couchbase.CouchbaseConfiguration;
import io.flamingock.community.couchbase.internal.CouchbaseAuditPersistence;

public class CouchbaseAuditStore implements CommunityAuditStore {

    private RunnerId runnerId;
    private CommunityConfigurable communityConfiguration;
    private CouchbaseAuditPersistence persistence;
    private CouchbaseLockService lockService;
    private Cluster cluster;
    private Bucket bucket;
    private String scopeName;
    private String auditRepositoryName;
    private String lockRepositoryName;
    private Boolean autoCreate;

    public CouchbaseAuditStore() {}

    public CouchbaseAuditStore withCluster(Cluster cluster) {
        this.cluster = cluster;
        return this;
    }

    public CouchbaseAuditStore withBucket(Bucket bucket) {
        this.bucket = bucket;
        return this;
    }

    public CouchbaseAuditStore withScopeName(String scopeName) {
        this.scopeName = scopeName;
        return this;
    }

    public CouchbaseAuditStore withAuditRepositoryName(String auditRepositoryName) {
        this.auditRepositoryName = auditRepositoryName;
        return this;
    }

    public CouchbaseAuditStore withLockRepositoryName(String lockRepositoryName) {
        this.lockRepositoryName = lockRepositoryName;
        return this;
    }

    public CouchbaseAuditStore withAutoCreate(boolean autoCreate) {
        this.autoCreate = autoCreate;
        return this;
    }

    @Override
    public void initialize(ContextResolver baseContext) {
        runnerId = baseContext.getRequiredDependencyValue(RunnerId.class);
        communityConfiguration = baseContext.getRequiredDependencyValue(CommunityConfigurable.class);
        CouchbaseConfiguration driverConfiguration = baseContext.getDependencyValue(CouchbaseConfiguration.class).orElse(new CouchbaseConfiguration());
        driverConfiguration.mergeConfig(baseContext);

        if (cluster == null) {
            cluster = baseContext.getRequiredDependencyValue(Cluster.class);
        }

        if (bucket == null) {
            bucket = baseContext.getRequiredDependencyValue(Bucket.class);
        }

        if (scopeName == null) {
            scopeName = driverConfiguration.getScopeName();
        }

        if (auditRepositoryName == null) {
            auditRepositoryName = driverConfiguration.getAuditRepositoryName();
        }

        if (lockRepositoryName == null) {
            lockRepositoryName = driverConfiguration.getLockRepositoryName();
        }

        if (autoCreate == null) {
            autoCreate = driverConfiguration.isAutoCreate();
        }

        this.validate();
    }

    @Override
    public synchronized CommunityAuditPersistence getPersistence() {
        if (persistence == null) {
            persistence = new CouchbaseAuditPersistence(
                    communityConfiguration,
                    cluster,
                    bucket,
                    scopeName,
                    auditRepositoryName,
                    autoCreate);
            persistence.initialize(runnerId);
        }
        return persistence;
    }

    @Override
    public synchronized CommunityLockService getLockService() {
        if (lockService == null) {
            lockService = new CouchbaseLockService(cluster, bucket, TimeService.getDefault());
            lockService.initialize(
                    autoCreate,
                    scopeName,
                    lockRepositoryName);
        }
        return lockService;
    }

    @Override
    public TargetSystem getTargetSystem() {
        return null;
    }

    private void validate() {

        if (scopeName == null || scopeName.trim().isEmpty()) {
            throw new FlamingockException("The 'scopeName' property is required.");
        }

        if (auditRepositoryName == null || auditRepositoryName.trim().isEmpty()) {
            throw new FlamingockException("The 'auditRepositoryName' property is required.");
        }

        if (lockRepositoryName == null || lockRepositoryName.trim().isEmpty()) {
            throw new FlamingockException("The 'lockRepositoryName' property is required.");
        }

        if (auditRepositoryName.trim().equalsIgnoreCase(lockRepositoryName.trim())) {
            throw new FlamingockException("The 'auditRepositoryName' and 'lockRepositoryName' properties must not be the same.");
        }

        if (autoCreate == null) {
            throw new FlamingockException("The 'autoCreate' property is required.");
        }
    }
}
