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
import com.couchbase.client.java.transactions.TransactionAttemptContext;
import io.flamingock.internal.common.core.audit.AuditPersistenceFactory;
import io.flamingock.internal.common.core.audit.AuditReader;
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.common.core.feature.Features;
import io.flamingock.internal.core.configuration.community.CommunityConfigurable;
import io.flamingock.internal.core.external.store.CommunityAuditStore;
import io.flamingock.internal.core.external.store.audit.community.CommunityAuditPersistence;
import io.flamingock.internal.core.external.store.lock.community.CommunityLockService;
import io.flamingock.internal.core.journal.JournalEventSequencer;
import io.flamingock.internal.core.journal.JournalEventSequencerFactory;
import io.flamingock.internal.util.Constants;
import io.flamingock.internal.util.FeatureFlag;
import io.flamingock.internal.util.TimeService;
import io.flamingock.internal.util.constants.CommunityPersistenceConstants;
import io.flamingock.internal.common.couchbase.journal.JournalEventPersistenceConstants;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.store.couchbase.internal.CouchbaseAuditPersistence;
import io.flamingock.store.couchbase.internal.CouchbaseAuditor;
import io.flamingock.store.couchbase.internal.CouchbaseJournalEventStore;
import io.flamingock.store.couchbase.internal.CouchbaseLockService;
import io.flamingock.externalsystem.couchbase.api.CouchbaseExternalSystem;

import java.util.Collections;
import java.util.Set;

public class CouchbaseAuditStore implements CommunityAuditStore {

    private final CouchbaseExternalSystem targetSystem;
    private final Cluster cluster;
    private final String bucketName;
    private RunnerId runnerId;
    private CommunityConfigurable communityConfiguration;
    private CouchbaseLockService lockService;
    private Bucket bucket;
    private String scopeName = CollectionIdentifier.DEFAULT_SCOPE;
    private String auditRepositoryName = CommunityPersistenceConstants.DEFAULT_AUDIT_STORE_NAME;
    private String lockRepositoryName = CommunityPersistenceConstants.DEFAULT_LOCK_STORE_NAME;
    private String journalRepositoryName = JournalEventPersistenceConstants.DEFAULT_JOURNAL_STORE_NAME;
    private boolean autoCreate = true;
    private CouchbaseAuditor auditor;
    private CouchbaseJournalEventStore journalEventStore;
    private JournalEventSequencerFactory journalEventSequencerFactory;


    private CouchbaseAuditStore(CouchbaseExternalSystem targetSystem) {
        // Cannot resolve targetSystem.getTxWrapper() here: the target system's own initialize() — which is
        // what sets it — runs later than this constructor (called eagerly when the caller builds this audit
        // store), so it would still be null at this point. Kept as a live reference and resolved lazily in
        // getPersistenceFactory(), by which point both the target system and this audit store are initialized.
        this.targetSystem = targetSystem;
        this.cluster = targetSystem.getCluster();
        this.bucketName = targetSystem.getBucketName();
    }

    /**
     * Creates a {@link CouchbaseAuditStore} using the same Couchbase cluster and
     * bucket configured in the given {@link CouchbaseExternalSystem}.
     * <p>
     * Only the underlying Couchbase instance (cluster + bucket name) is reused.
     * No additional target-system configuration is carried over.
     *
     * @param targetSystem the target system from which to derive the cluster and bucket
     * @return a new audit store bound to the same Couchbase instance as the target system
     */
    public static CouchbaseAuditStore from(CouchbaseExternalSystem targetSystem) {
        return new CouchbaseAuditStore(targetSystem);
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

    public CouchbaseAuditStore withJournalRepositoryName(String journalRepositoryName) {
        this.journalRepositoryName = journalRepositoryName;
        return this;
    }

    public CouchbaseAuditStore withAutoCreate(boolean autoCreate) {
        this.autoCreate = autoCreate;
        return this;
    }

    @Override
    public void initialize(ContextResolver baseContext) {
        this.validate();
        runnerId = baseContext.getRequiredDependencyValue(RunnerId.class);
        communityConfiguration = baseContext.getRequiredDependencyValue(CommunityConfigurable.class);

        auditor = new CouchbaseAuditor(cluster, bucket);
        journalEventStore = new CouchbaseJournalEventStore(cluster, bucket);
        journalEventSequencerFactory = new JournalEventSequencerFactory(journalEventStore);

        lockService = new CouchbaseLockService(cluster, bucket, TimeService.getDefault());
        lockService.initialize(autoCreate, scopeName, lockRepositoryName);
    }

    @Override
    public AuditPersistenceFactory<CommunityAuditPersistence> getPersistenceFactory() {
        return stageId -> {
            // Must run before forStream(stageId): forStream seeds the sequence from the last persisted event,
            // which Couchbase reports as empty until the journal store is initialized. Idempotent and
            // synchronized, so repeating it in CouchbaseAuditPersistence#doInitialize is safe.
            FeatureFlag.ifEnabled(Features.JOURNAL_EVENTS, () -> journalEventStore.initialize(autoCreate, scopeName, journalRepositoryName));
            JournalEventSequencer journalEventSequencer = journalEventSequencerFactory.forStream(stageId);
            CouchbaseAuditPersistence persistence = new CouchbaseAuditPersistence(
                    communityConfiguration,
                    auditor,
                    journalEventStore,
                    journalEventSequencer,
                    targetSystem.getTxWrapper(),
                    scopeName,
                    auditRepositoryName,
                    journalRepositoryName,
                    autoCreate);
            persistence.initialize(runnerId);
            return persistence;
        };
    }

    @Override
    public CommunityAuditPersistence getPersistence() {
        throw new UnsupportedOperationException("getPersistence shouldn't be called at Couchbase audit store; use getPersistenceFactory(stageId)");
    }

    @Override
    public AuditReader getAuditReader() {
        auditor.initialize(autoCreate, scopeName, auditRepositoryName);
        return () -> auditor.getAuditHistory();
    }

    @Override
    public synchronized CommunityLockService getLockService() {
        return lockService;
    }

    @Override
    public Set<Class<?>> getNonGuardedTypes() {
        return Collections.singleton(TransactionAttemptContext.class);
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

        if (journalRepositoryName == null || journalRepositoryName.trim().isEmpty()) {
            throw new FlamingockException("The 'journalRepositoryName' property is required.");
        }

        if (auditRepositoryName.trim().equalsIgnoreCase(lockRepositoryName.trim())) {
            throw new FlamingockException("The 'auditRepositoryName' and 'lockRepositoryName' properties must not be the same.");
        }

        if (journalRepositoryName.trim().equalsIgnoreCase(auditRepositoryName.trim())) {
            throw new FlamingockException("The 'journalRepositoryName' and 'auditRepositoryName' properties must not be the same.");
        }

        if (journalRepositoryName.trim().equalsIgnoreCase(lockRepositoryName.trim())) {
            throw new FlamingockException("The 'journalRepositoryName' and 'lockRepositoryName' properties must not be the same.");
        }
    }
}
