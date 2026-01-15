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
package io.flamingock.store.couchbase;

import com.couchbase.client.core.io.CollectionIdentifier;
import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.core.configuration.community.CommunityConfigurable;
import io.flamingock.internal.core.external.store.CommunityAuditStore;
import io.flamingock.internal.core.external.store.audit.community.CommunityAuditPersistence;
import io.flamingock.internal.core.external.store.lock.community.CommunityLockService;
import io.flamingock.internal.util.Constants;
import io.flamingock.internal.util.TimeService;
import io.flamingock.internal.util.constants.CommunityPersistenceConstants;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.store.couchbase.internal.CouchbaseAuditPersistence;
import io.flamingock.store.couchbase.internal.CouchbaseLockService;
import io.flamingock.targetsystem.couchbase.CouchbaseExternalSystem;
import io.flamingock.targetsystem.couchbase.CouchbaseTargetSystem;

public class CouchbaseAuditStore implements CommunityAuditStore {

    private final Cluster cluster;
    private final String bucketName;
    private RunnerId runnerId;
    private CommunityConfigurable communityConfiguration;
    private CouchbaseAuditPersistence persistence;
    private CouchbaseLockService lockService;
    private Bucket bucket;
    private String scopeName = CollectionIdentifier.DEFAULT_SCOPE;
    private String auditRepositoryName = CommunityPersistenceConstants.DEFAULT_AUDIT_STORE_NAME;
    private String lockRepositoryName = CommunityPersistenceConstants.DEFAULT_LOCK_STORE_NAME;
    private boolean autoCreate = true;


    private CouchbaseAuditStore(Cluster cluster, String bucketName) {
        this.cluster = cluster;
        this.bucketName = bucketName;
    }

    /**
     * Creates a {@link CouchbaseAuditStore} using the same Couchbase cluster and
     * bucket configured in the given {@link CouchbaseTargetSystem}.
     * <p>
     * Only the underlying Couchbase instance (cluster + bucket name) is reused.
     * No additional target-system configuration is carried over.
     *
     * @param targetSystem the target system from which to derive the cluster and bucket
     * @return a new audit store bound to the same Couchbase instance as the target system
     */
    public static CouchbaseAuditStore from(CouchbaseExternalSystem targetSystem) {
        return new CouchbaseAuditStore(targetSystem.getCluster(), targetSystem.getBucketName());
    }

    @Override
    public String getId() {
        return Constants.DEFAULT_COUCHBASE_AUDIT_STORE;
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

    private void validate() {

        if (cluster == null) {
            throw new FlamingockException("The 'cluster' instance is required.");
        }

        if (bucketName == null || bucketName.trim().isEmpty()) {
            throw new FlamingockException("The 'bucketName' property is required.");
        }

        bucket = cluster.bucket(bucketName);
        if (bucket == null) {
            throw new FlamingockException("The 'bucketName' property is invalid. The cluster does not contain a bucket named '%s'", bucketName);
        }

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
    }
}
