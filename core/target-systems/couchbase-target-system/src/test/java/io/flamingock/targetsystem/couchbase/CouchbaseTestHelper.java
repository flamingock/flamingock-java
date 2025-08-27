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
package io.flamingock.targetsystem.couchbase;

import com.couchbase.client.core.error.DocumentExistsException;
import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.json.JsonObject;
import io.flamingock.internal.common.cloud.vo.TargetSystemAuditMarkType;
import io.flamingock.internal.common.couchbase.CouchbaseCollectionHelper;
import io.flamingock.internal.common.couchbase.CouchbaseCollectionInitializator;
import io.flamingock.internal.core.store.audit.domain.AuditContextBundle;
import io.flamingock.internal.core.targets.mark.TargetSystemAuditMark;

import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CouchbaseTestHelper {

    private final Cluster cluster;
    private final Bucket bucket;
    private final String scopeName;
    private final String collectionName;

    public CouchbaseTestHelper(Cluster cluster, Bucket bucket, String scopeName, String collectionName) {
        this.cluster = cluster;
        this.bucket = bucket;
        this.scopeName = scopeName;
        this.collectionName = collectionName;
    }

    public void insertOngoingExecution(String taskId) {

        CouchbaseCollectionInitializator collectionInitializator =
                new CouchbaseCollectionInitializator(cluster, bucket, scopeName, collectionName);
        collectionInitializator.initialize(true);

        Collection collection = this.getOnGoingStatusCollection();

        String key = taskId;

        JsonObject document = JsonObject.create()
                .put("taskId", taskId)
                .put("operation", AuditContextBundle.Operation.EXECUTION.toString());

        try {
            collection.insert(key, document);
        } catch (DocumentExistsException exists) {
            collection.replace(key, document);
        }

        checkEmptyTargetSystemAuditMarker();
    }

    public <T> void checkCount(Collection collection, int count) {
        long result = CouchbaseCollectionHelper.selectAllDocuments(cluster,
                                                                    collection.bucketName(),
                                                                    collection.scopeName(),
                                                                    collection.name()).size();
        assertEquals(count, (int) result);
    }

    public void checkEmptyTargetSystemAuditMarker() {
        checkOngoingTask(result -> result == 0);
    }

    public void checkOngoingTask(Predicate<Long> predicate) {
        Collection collection = this.getOnGoingStatusCollection();
        long result = CouchbaseCollectionHelper.selectAllDocuments(cluster,
                collection.bucketName(),
                collection.scopeName(),
                collection.name()).size();
        assertTrue(predicate.test(result));
    }

    public static TargetSystemAuditMark mapToOnGoingStatus(JsonObject document) {
        TargetSystemAuditMarkType operation = TargetSystemAuditMarkType.valueOf(document.getString("operation"));
        return new TargetSystemAuditMark(document.getString("taskId"), operation);
    }

    private Collection getOnGoingStatusCollection() {
        return bucket.scope(scopeName).collection(collectionName);
    }
}
