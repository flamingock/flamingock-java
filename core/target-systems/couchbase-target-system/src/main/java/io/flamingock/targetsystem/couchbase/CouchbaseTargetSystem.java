/*
 * Copyright 2025 Flamingock (https://www.flamingock.io)
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
package io.flamingock.targetsystem.couchbase;

import com.couchbase.client.core.io.CollectionIdentifier;
import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;

import com.couchbase.client.java.transactions.TransactionAttemptContext;
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.core.builder.FlamingockEdition;
import io.flamingock.internal.core.community.Constants;
import io.flamingock.internal.core.community.TransactionManager;
import io.flamingock.internal.core.targets.TransactionalTargetSystem;
import io.flamingock.internal.core.targets.mark.NoOpTargetSystemAuditMarker;
import io.flamingock.internal.core.targets.mark.TargetSystemAuditMarker;
import io.flamingock.internal.core.transaction.TransactionWrapper;

public class CouchbaseTargetSystem extends TransactionalTargetSystem<CouchbaseTargetSystem> {

    private static final String COUCHBASE_SCOPE_NAME_PROPERTY = "couchbase.scopeName";
    private static final String COUCHBASE_ON_GOING_TASKS_REPOSITORY_NAME_PROPERTY = "couchbase.onGoingTasksRepositoryName";

    private TargetSystemAuditMarker taskStatusRepository;

    private CouchbaseTxWrapper txWrapper;
    private Cluster cluster;
    private Bucket bucket;

    public CouchbaseTargetSystem(String id) { super(id); }

    public CouchbaseTargetSystem withCluster(Cluster cluster) {
        targetSystemContext.addDependency(cluster);
        return this;
    }

    public CouchbaseTargetSystem withBucket(Bucket bucket) {
        targetSystemContext.addDependency(bucket);
        return this;
    }

    public CouchbaseTargetSystem withScopeName(String scopeName) {
        targetSystemContext.setProperty(COUCHBASE_SCOPE_NAME_PROPERTY, scopeName);
        return this;
    }

    public CouchbaseTargetSystem withOnGoingTasksRepositoryName(String onGoingTasksRepositoryName) {
        targetSystemContext.setProperty(COUCHBASE_ON_GOING_TASKS_REPOSITORY_NAME_PROPERTY, onGoingTasksRepositoryName);
        return this;
    }

    public Cluster getCluster() {
        return this.cluster;
    }

    public Bucket getBucket() {
        return this.bucket;
    }

    public TransactionManager<TransactionAttemptContext> getTxManager() {
        return txWrapper.getTxManager();
    }

    @Override
    public void initialize(ContextResolver baseContext) {
        FlamingockEdition edition = baseContext.getDependencyValue(FlamingockEdition.class)
                .orElse(FlamingockEdition.CLOUD);

        cluster = targetSystemContext.getDependencyValue(Cluster.class)
                .orElseGet(() -> baseContext.getRequiredDependencyValue(Cluster.class));

        bucket = targetSystemContext.getDependencyValue(Bucket.class)
                .orElseGet(() -> baseContext.getRequiredDependencyValue(Bucket.class));

        String scopeName = targetSystemContext.getPropertyAs(COUCHBASE_SCOPE_NAME_PROPERTY, String.class)
                .orElseGet(() -> baseContext.getPropertyAs(COUCHBASE_SCOPE_NAME_PROPERTY, String.class)
                                            .orElse(CollectionIdentifier.DEFAULT_SCOPE));

        String onGoingTasksRepositoryName = targetSystemContext.getPropertyAs(COUCHBASE_ON_GOING_TASKS_REPOSITORY_NAME_PROPERTY, String.class)
                .orElseGet(() -> baseContext.getPropertyAs(COUCHBASE_ON_GOING_TASKS_REPOSITORY_NAME_PROPERTY, String.class)
                        .orElse(Constants.DEFAULT_ON_GOING_TASKS_STORE_NAME));

        TransactionManager<TransactionAttemptContext> txManager = new TransactionManager<>(null); //TODO change to a new constructor without args
        txWrapper = new CouchbaseTxWrapper(cluster, txManager);

        taskStatusRepository = edition == FlamingockEdition.COMMUNITY
                ? new NoOpTargetSystemAuditMarker(this.getId())
                : CouchbaseTargetSystemAuditMarker.builder(cluster, bucket, txManager)
                .withScopeName(scopeName)
                .withCollectionName(onGoingTasksRepositoryName)
                .withAutoCreate(autoCreate)
                .build();
    }

    @Override
    protected CouchbaseTargetSystem getSelf() {
        return this;
    }

    @Override
    public TargetSystemAuditMarker getOnGoingTaskStatusRepository() {
        return taskStatusRepository;
    }

    @Override
    public TransactionWrapper getTxWrapper() {
        return txWrapper;
    }

    @Override
    public boolean isSameTxResourceAs(TransactionalTargetSystem<?> other) {
        if (!(other instanceof CouchbaseTargetSystem)) {
            return false;
        }
        Cluster otherCluster = ((CouchbaseTargetSystem) other).cluster;
        if (otherCluster == null) {
            return false;
        }
        return otherCluster.equals(this.cluster);
    }
}
