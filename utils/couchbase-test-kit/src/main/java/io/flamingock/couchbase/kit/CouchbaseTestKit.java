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
package io.flamingock.couchbase.kit;

import com.couchbase.client.core.io.CollectionIdentifier;
import com.couchbase.client.java.Cluster;
import io.flamingock.core.kit.AbstractTestKit;
import io.flamingock.core.kit.audit.AuditStorage;
import io.flamingock.core.kit.lock.LockStorage;
import io.flamingock.internal.common.couchbase.CouchbaseCollectionHelper;
import io.flamingock.internal.core.external.store.CommunityAuditStore;
import io.flamingock.internal.util.constants.CommunityPersistenceConstants;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class CouchbaseTestKit extends AbstractTestKit {

    private final Cluster cluster;
    private final String bucketName;
    private final String scopeName;

    public CouchbaseTestKit(AuditStorage auditStorage, LockStorage lockStorage, CommunityAuditStore AuditStore, Cluster cluster, String bucketName, String scopeName) {
        super(auditStorage, lockStorage, AuditStore);
        this.cluster = cluster;
        this.bucketName = bucketName;
        this.scopeName = scopeName;
    }

    @Override
    public void cleanUp() {
        cluster.bucket(bucketName).collections().getAllScopes().stream()
                .filter(scopeSpec -> scopeSpec.name().equals(scopeName))
                .flatMap(scopeSpec -> scopeSpec.collections().stream())
                .forEach(collectionSpec -> {
                    CouchbaseCollectionHelper.deleteAllDocuments(cluster, bucketName, scopeName, collectionSpec.name());
                    cluster.bucket(bucketName).scope(scopeName).collection(collectionSpec.name())
                        .queryIndexes().getAllIndexes().stream()
                        .filter(index -> !index.primary())
                        .forEach(index ->
                            CouchbaseCollectionHelper.dropIndexIfExists(cluster, bucketName, scopeName, collectionSpec.name(), index.name())
                        );
                    if (!collectionSpec.name().equals(CollectionIdentifier.DEFAULT_COLLECTION)) {
                        CouchbaseCollectionHelper.dropPrimaryIndexIfExists(cluster, bucketName, scopeName, collectionSpec.name());
                        CouchbaseCollectionHelper.dropCollectionIfExists(cluster, bucketName, scopeName, collectionSpec.name());
                    }
                });
    }

    /**
     * Create a new CouchbaseTestKit with Couchbase cluster and bucketName
     */
    public static CouchbaseTestKit create(CommunityAuditStore AuditStore, Cluster cluster, String bucketName) {
        CouchbaseAuditStorage auditStorage = new CouchbaseAuditStorage(cluster, bucketName, CollectionIdentifier.DEFAULT_SCOPE);
        CouchbaseLockStorage lockStorage = new CouchbaseLockStorage(cluster, bucketName, CollectionIdentifier.DEFAULT_SCOPE);
        return new CouchbaseTestKit(auditStorage, lockStorage, AuditStore, cluster, bucketName, CollectionIdentifier.DEFAULT_SCOPE);
    }

    /**
     * Create a new CouchbaseTestKit with Couchbase cluster, bucketName and scopeName
     */
    public static CouchbaseTestKit create(CommunityAuditStore AuditStore, Cluster cluster, String bucketName, String scopeName) {
        CouchbaseAuditStorage auditStorage = new CouchbaseAuditStorage(cluster, bucketName, scopeName);
        CouchbaseLockStorage lockStorage = new CouchbaseLockStorage(cluster, bucketName, scopeName);
        return new CouchbaseTestKit(auditStorage, lockStorage, AuditStore, cluster, bucketName, scopeName);
    }
}
