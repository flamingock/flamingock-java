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

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.transactions.TransactionAttemptContext;
import io.flamingock.externalsystem.couchbase.api.CouchbaseExternalSystem;
import io.flamingock.importer.mongock.couchbase.MongockImporterCouchbase;
import io.flamingock.internal.common.core.audit.AuditHistoryReader;
import io.flamingock.internal.common.core.audit.AuditReaderType;
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.common.couchbase.CouchbaseUtils;
import io.flamingock.internal.core.builder.FlamingockEdition;
import io.flamingock.internal.core.external.targets.TransactionalTargetSystem;
import io.flamingock.internal.core.external.targets.mark.NoOpTargetSystemAuditMarker;
import io.flamingock.internal.core.transaction.TransactionManager;
import io.flamingock.internal.core.transaction.TransactionWrapper;
import java.util.function.Supplier;

import java.util.Objects;
import java.util.Optional;

import static io.flamingock.internal.common.core.audit.AuditReaderType.MONGOCK;
import static io.flamingock.internal.common.core.metadata.Constants.MONGOCK_IMPORT_ORIGIN_PROPERTY_KEY;
import static io.flamingock.internal.core.builder.FlamingockEdition.COMMUNITY;

public class CouchbaseTargetSystem extends TransactionalTargetSystem<CouchbaseTargetSystem> implements CouchbaseExternalSystem {

    private final Cluster cluster;
    private final String bucketName;
    private Bucket bucket;

    private ContextResolver baseContext;
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
        this.baseContext = baseContext;
        this.validate();
        targetSystemContext.addDependency(cluster);
        bucket = cluster.bucket(bucketName);
        targetSystemContext.addDependency(bucket);


        Supplier<TransactionAttemptContext> couchbaseTxSupplier = () -> {
            throw new FlamingockException(
                    "Couchbase TransactionAttemptContext can only be obtained inside cluster.transactions().run(); "
                            + "the wrapper must register the session via TransactionManager.startSession(sessionId, ctx).");
        };
        TransactionManager<TransactionAttemptContext> txManager = new TransactionManager<>(couchbaseTxSupplier);
        txWrapper = new CouchbaseTxWrapper(cluster, txManager);

        FlamingockEdition edition = baseContext.getDependencyValue(FlamingockEdition.class).orElse(COMMUNITY);
        auditMarker = edition == COMMUNITY
                ? new NoOpTargetSystemAuditMarker(this.getId())
                : CouchbaseTargetSystemAuditMarker.builder(cluster, bucket, txManager).build();
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
            CouchbaseUtils.ScopeCollection scopeCollection = CouchbaseUtils.getOriginScopeAndCollection(getOriginPropertyValue());
            return Optional.of(new MongockImporterCouchbase(cluster, bucketName, scopeCollection.getScope(), scopeCollection.getCollection()));
        } else {
            return Optional.empty();
        }
    }

    private String getOriginPropertyValue() {
        return targetSystemContext.getProperty(MONGOCK_IMPORT_ORIGIN_PROPERTY_KEY)
                .orElse(baseContext.getProperty(MONGOCK_IMPORT_ORIGIN_PROPERTY_KEY)
                        .orElse(null));
    }
}
