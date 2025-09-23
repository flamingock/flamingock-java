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

import com.couchbase.client.core.error.DocumentNotFoundException;
import com.couchbase.client.core.io.CollectionIdentifier;
import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.query.QueryOptions;
import com.couchbase.client.java.query.QueryScanConsistency;
import com.couchbase.client.java.transactions.TransactionAttemptContext;
import com.couchbase.client.java.transactions.TransactionGetResult;
import io.flamingock.internal.common.cloud.vo.TargetSystemAuditMarkType;
import io.flamingock.internal.common.couchbase.CouchbaseCollectionHelper;
import io.flamingock.internal.common.couchbase.CouchbaseCollectionInitializator;
import io.flamingock.internal.util.constants.CommunityPersistenceConstants;
import io.flamingock.internal.core.transaction.TransactionManager;
import io.flamingock.internal.core.targets.mark.TargetSystemAuditMark;
import io.flamingock.internal.core.targets.mark.TargetSystemAuditMarker;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class CouchbaseTargetSystemAuditMarker implements TargetSystemAuditMarker {
    private static final String OPERATION = "operation";
    private static final String TASK_ID = "taskId";
    private final Cluster cluster;
    private final Collection onGoingTaskStatusCollection;
    private final TransactionManager<TransactionAttemptContext> txManager;

    public static Builder builder(Cluster cluster,
                                  Bucket bucket,
                                  TransactionManager<TransactionAttemptContext> txManager) {
        return new Builder(cluster, bucket, txManager);
    }

    public CouchbaseTargetSystemAuditMarker(Cluster cluster, Collection onGoingTaskStatusCollection, TransactionManager<TransactionAttemptContext> txManager) {
        this.cluster = cluster;
        this.onGoingTaskStatusCollection = onGoingTaskStatusCollection;
        this.txManager = txManager;
    }

    @Override
    public Set<TargetSystemAuditMark> listAll() {
        List<JsonObject> results = CouchbaseCollectionHelper.selectAllDocuments(cluster,
                                                                                onGoingTaskStatusCollection.bucketName(),
                                                                                onGoingTaskStatusCollection.scopeName(),
                                                                                onGoingTaskStatusCollection.name());
        return results
                .stream()
                .map(CouchbaseTargetSystemAuditMarker::mapToOnGoingStatus)
                .collect(Collectors.toCollection(HashSet::new));
    }

    @Override
    public void clearMark(String changeId) {
        cluster.query(
                String.format(
                        "DELETE FROM `%s`.`%s`.`%s` WHERE `%s`= $p1",
                        onGoingTaskStatusCollection.bucketName(),
                        onGoingTaskStatusCollection.scopeName(),
                        onGoingTaskStatusCollection.name(),
                        TASK_ID),
                QueryOptions.queryOptions()
                        .scanConsistency(QueryScanConsistency.REQUEST_PLUS)
                        .parameters(JsonObject.create().put("p1", changeId)));
    }

    @Override
    public void mark(TargetSystemAuditMark auditMark) {

        String key = auditMark.getTaskId();

        JsonObject document = JsonObject.create()
                .put(TASK_ID, auditMark.getTaskId())
                .put(OPERATION, auditMark.getOperation().name());

        TransactionAttemptContext ctx = txManager.getSessionOrThrow(auditMark.getTaskId());

        try {
            TransactionGetResult existing = ctx.get(onGoingTaskStatusCollection, key);
            ctx.replace(existing, document);
        } catch (DocumentNotFoundException e) {
            ctx.insert(onGoingTaskStatusCollection, key, document);
        }
    }

    public static TargetSystemAuditMark mapToOnGoingStatus(JsonObject jsonObject) {
        TargetSystemAuditMarkType operation = TargetSystemAuditMarkType.valueOf(jsonObject.getString(OPERATION));
        return new TargetSystemAuditMark(jsonObject.getString(TASK_ID), operation);
    }


    public static class Builder {

        private final Cluster cluster;
        private final Bucket bucket;
        private final TransactionManager<TransactionAttemptContext> txManager;
        private boolean autoCreate = true;
        private String scopeName = CollectionIdentifier.DEFAULT_SCOPE;
        private String collectionName = CommunityPersistenceConstants.DEFAULT_MARKER_STORE_NAME;

        public Builder(Cluster cluster, Bucket bucket, TransactionManager<TransactionAttemptContext> txManager) {
            this.cluster = cluster;
            this.bucket = bucket;
            this.txManager = txManager;
        }

        public Builder withScopeName(String scopeName) {
            this.scopeName = scopeName;
            return this;
        }

        public Builder withCollectionName(String collectionName) {
            this.collectionName = collectionName;
            return this;
        }

        public Builder withAutoCreate(boolean autoCreate) {
            this.autoCreate = autoCreate;
            return this;
        }

        public CouchbaseTargetSystemAuditMarker build() {
            CouchbaseCollectionInitializator collectionInitializator = new CouchbaseCollectionInitializator(cluster, bucket, scopeName, collectionName);
            collectionInitializator.initialize(autoCreate);
            Collection onGoingTasksStatusCollection = this.bucket.scope(scopeName).collection(collectionName);
            return new CouchbaseTargetSystemAuditMarker(cluster, onGoingTasksStatusCollection, txManager);
        }
    }
}
