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
import io.flamingock.importer.mongock.couchbase.MongockImporterCouchbase;
import io.flamingock.internal.common.core.audit.AuditHistoryReader;
import io.flamingock.internal.common.core.audit.AuditReaderType;
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.core.external.targets.TransactionalTargetSystem;
import io.flamingock.internal.core.external.targets.mark.NoOpTargetSystemAuditMarker;
import io.flamingock.internal.core.transaction.TransactionManager;
import io.flamingock.internal.core.transaction.TransactionWrapper;

import java.util.Objects;
import java.util.Optional;

import static io.flamingock.internal.common.core.audit.AuditReaderType.MONGOCK;

public class CouchbaseTargetSystem extends TransactionalTargetSystem<CouchbaseTargetSystem> implements CouchbaseExternalSystem {

    private Cluster cluster;
    private Bucket bucket;
    private String bucketName;
    private String scopeName = CollectionIdentifier.DEFAULT_SCOPE;

    private CouchbaseTxWrapper txWrapper;

    public CouchbaseTargetSystem(String id, Cluster cluster, String bucketName) {
        super(id);
        this.cluster = cluster;
        this.bucketName = bucketName;
    }

    @Override
    public Cluster getCluster() {
        return this.cluster;
    }

    @Override
    public Bucket getBucket() {
        return this.bucket;
    }

    @Override
    public String getBucketName() {
        return this.bucketName;
    }

    public TransactionManager<TransactionAttemptContext> getTxManager() {
        return txWrapper.getTxManager();
    }

    @Override
    public void initialize(ContextResolver baseContext) {
        this.validate();
        targetSystemContext.addDependency(cluster);
        bucket = cluster.bucket(bucketName);
        targetSystemContext.addDependency(bucket);


        TransactionManager<TransactionAttemptContext> txManager = new TransactionManager<>(null); //TODO: update as needed
        txWrapper = new CouchbaseTxWrapper(cluster, txManager);

        //TODO: inject marker repository based on edition(baseContext.getDependencyValue(FlamingockEdition.class))
        markerRepository = new NoOpTargetSystemAuditMarker(this.getId());
    }

    private void validate() {
        if (cluster == null) {
            throw new FlamingockException("The 'cluster' instance is required.");
        }
        if (bucketName == null || bucketName.trim().isEmpty()) {
            throw new FlamingockException("The 'bucketName' property is required.");
        }
    }

    @Override
    protected CouchbaseTargetSystem getSelf() {
        return this;
    }

    @Override
    public TransactionWrapper getTxWrapper() {
        return txWrapper;
    }

    @Override
    public Optional<AuditHistoryReader> getAuditAuditReader(AuditReaderType type) {
        if (Objects.requireNonNull(type) == MONGOCK) {
            //TODO: Allow scope and collection to be parameterized
            return Optional.of(new MongockImporterCouchbase(cluster, bucketName, CollectionIdentifier.DEFAULT_SCOPE, CollectionIdentifier.DEFAULT_COLLECTION));
        } else {
            return Optional.empty();
        }
    }
}
