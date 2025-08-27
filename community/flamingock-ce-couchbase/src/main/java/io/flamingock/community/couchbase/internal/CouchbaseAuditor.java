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

import com.couchbase.client.core.error.CouchbaseException;
import com.couchbase.client.core.error.DocumentNotFoundException;
import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.kv.PersistTo;
import com.couchbase.client.java.kv.ReplicateTo;
import com.couchbase.client.java.kv.UpsertOptions;
import com.couchbase.client.java.transactions.TransactionAttemptContext;
import com.couchbase.client.java.transactions.TransactionGetResult;
import io.flamingock.internal.common.couchbase.CouchbaseAuditMapper;
import io.flamingock.internal.common.couchbase.CouchbaseCollectionHelper;
import io.flamingock.internal.common.couchbase.CouchbaseCollectionInitializator;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.core.transaction.TransactionManager;
import io.flamingock.internal.common.core.audit.AuditReader;
import io.flamingock.internal.core.store.audit.LifecycleAuditWriter;
import io.flamingock.internal.core.store.audit.domain.AuditSnapshotMapBuilder;

import io.flamingock.internal.util.Result;
import io.flamingock.internal.util.FlamingockLoggerFactory;
import io.flamingock.targetsystem.couchbase.CouchbaseTargetSystem;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;


public class CouchbaseAuditor implements LifecycleAuditWriter, AuditReader {

    private static final Logger logger = FlamingockLoggerFactory.getLogger("CouchbaseAuditor");

    protected final Cluster cluster;
    protected final Bucket bucket;
    private final TransactionManager<TransactionAttemptContext> txManager;
    protected Collection collection;
    protected CouchbaseCollectionInitializator collectionInitializator;


    private final CouchbaseAuditMapper mapper = new CouchbaseAuditMapper();

    protected CouchbaseAuditor(CouchbaseTargetSystem targetSystem) {
        this.cluster = targetSystem.getCluster();
        this.bucket = targetSystem.getBucket();
        this.txManager = targetSystem.getTxManager();
    }

    protected void initialize(boolean autoCreate, String scopeName, String collectionName) {
        this.collectionInitializator = new CouchbaseCollectionInitializator(cluster, bucket, scopeName, collectionName);
        this.collectionInitializator.initialize(autoCreate);
        this.collection = this.bucket.scope(scopeName).collection(collectionName);
    }

    @Override
    public Result writeEntry(AuditEntry auditEntry) {

        String key = toKey(auditEntry);
        logger.debug("Saving audit entry with key {}", key);

        JsonObject document = mapper.toDocument(auditEntry);

        Optional<TransactionAttemptContext> ctxOpt = txManager.getSession(auditEntry.getTaskId());

        if (ctxOpt.isPresent()) {
            TransactionAttemptContext ctx = ctxOpt.get();
            try {
                try {
                    TransactionGetResult existing = ctx.get(collection, key);
                    ctx.replace(existing, document);
                } catch (DocumentNotFoundException e) {
                    ctx.insert(collection, key, document);
                }
            }
            catch (CouchbaseException couchbaseException) {
                logger.warn("Error saving audit entry with key {}", key, couchbaseException);
                throw new RuntimeException(couchbaseException);
            }
        }
        else {
            try {
                collection.upsert(key, document,
                        UpsertOptions.upsertOptions().durability(PersistTo.ACTIVE, ReplicateTo.NONE));
            } catch (CouchbaseException couchbaseException) {
                logger.warn("Error saving audit entry with key {}", key, couchbaseException);
                throw new RuntimeException(couchbaseException);
            }
        }

        return Result.OK();
    }


    @Override
    public Map<String, AuditEntry> getAuditSnapshotByChangeId() {
        AuditSnapshotMapBuilder builder = new AuditSnapshotMapBuilder();
        List<JsonObject> documents = CouchbaseCollectionHelper.selectAllDocuments(cluster, collection.bucketName(), collection.scopeName(), collection.name());
        documents.stream()
                .map(mapper::fromDocument)
                .collect(Collectors.toList())
                .forEach(builder::addEntry);
        return builder.build();
    }

    private String toKey(AuditEntry auditEntry) {
        return auditEntry.getExecutionId() +
                '-' +
                auditEntry.getAuthor() +
                '-' +
                auditEntry.getTaskId();
    }
}
