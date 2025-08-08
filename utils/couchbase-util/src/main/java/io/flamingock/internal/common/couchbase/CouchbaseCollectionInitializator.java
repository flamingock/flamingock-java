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
package io.flamingock.internal.common.couchbase;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.manager.collection.ScopeSpec;

import java.util.List;

public class CouchbaseCollectionInitializator {

    private final Cluster cluster;
    private final Bucket bucket;
    private final String scopeName;
    private final String collectionName;
    private boolean initialized = false;

    public CouchbaseCollectionInitializator(Cluster cluster, Bucket bucket, String scopeName, String collectionName) {
        this.cluster = cluster;
        this.bucket = bucket;
        this.scopeName = scopeName;
        this.collectionName = collectionName;
    }

    public void initialize(boolean autoCreate) {
        if (!initialized) {
            ensureCollectionAndIndex(autoCreate);
            initialized = true;
        }
    }

    /**
     * Ensures that the collection exists and has a primary index
     */
    private void ensureCollectionAndIndex(boolean autoCreate) {
        if (autoCreate) {
            CouchbaseCollectionHelper.createScopeIfNotExists(cluster, bucket.name(), scopeName);
            CouchbaseCollectionHelper.createCollectionIfNotExists(cluster, bucket.name(), scopeName, collectionName);
            CouchbaseCollectionHelper.createPrimaryIndexIfNotExists(cluster, bucket.name(), scopeName, collectionName);
        }
        else {
            if (!CouchbaseCollectionHelper.collectionExists(cluster, bucket.name(), scopeName, collectionName)) {
                throw new RuntimeException(String.format("Auto-creation is disabled and collection does not exist: `%s`.`%s`.`%s`", bucket.name(), scopeName, collectionName));
            }
            else if (!CouchbaseCollectionHelper.collectionHasPrimaryIndex(cluster, bucket.name(), scopeName, collectionName)) {
                throw new RuntimeException(String.format("Auto-creation is disabled and primary key does not exist for collection: `%s`.`%s`.`%s`", bucket.name(), scopeName, collectionName));
            }
        }
    }
}
