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
package io.flamingock.internal.common.couchbase;

import com.couchbase.client.core.error.*;
import com.couchbase.client.core.io.CollectionIdentifier;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.manager.collection.CollectionManager;
import com.couchbase.client.java.manager.collection.CreateCollectionSettings;
import com.couchbase.client.java.manager.collection.CreateScopeOptions;
import com.couchbase.client.java.manager.collection.DropCollectionOptions;
import com.couchbase.client.java.manager.query.QueryIndex;
import com.couchbase.client.java.query.QueryOptions;
import com.couchbase.client.java.query.QueryScanConsistency;
import io.flamingock.internal.common.core.error.FlamingockException;

import java.time.Duration;
import java.util.List;

import static io.flamingock.internal.common.couchbase.CouchbaseUtils.isDefaultCollection;

public final class CouchbaseCollectionHelper {

    private final static String KEYSPACE_TEMPLATE = "`%s`.`%s`.`%s`";
    private final static String SELECT_COUNT_QUERY_TEMPLATE = "SELECT COUNT(*) FROM `%s`.`%s`.`%s`";
    private final static String SELECT_ALL_QUERY_TEMPLATE = "SELECT %s.* FROM `%s`.`%s`.`%s`";
    private final static String DELETE_ALL_QUERY_TEMPLATE = "DELETE FROM `%s`.`%s`.`%s`";
    private final static String CREATE_PRIMARY_INDEX_TEMPLATE = "CREATE PRIMARY INDEX IF NOT EXISTS ON `%s`.`%s`.`%s`";
    private final static String DROP_PRIMARY_INDEX_TEMPLATE = "DROP PRIMARY INDEX IF EXISTS ON `%s`.`%s`.`%s`";
    private final static String DROP_INDEX_TEMPLATE = "DROP INDEX `%s` IF EXISTS ON `%s`.`%s`.`%s`";

    private CouchbaseCollectionHelper() {}

    public static boolean scopeExists(Cluster cluster, String bucketName, String scopeName) {
        try {
            return cluster
                    .bucket(bucketName)
                    .collections()
                    .getAllScopes()
                    .stream()
                    .anyMatch(scope -> scope.name().equals(scopeName));
        } catch (Exception e) {
            throw new FlamingockException("Error checking if scope exists: " + e.getMessage());
        }
    }

    public static boolean collectionExists(Cluster cluster, String bucketName, String scopeName, String collectionName) {
        try {
            return cluster
                    .bucket(bucketName)
                    .collections()
                    .getAllScopes()
                    .stream()
                    .filter(scope -> scope.name().equals(scopeName))
                    .flatMap(scope -> scope.collections().stream())
                    .anyMatch(collection -> collection.name().equals(collectionName));
        } catch (Exception e) {
            throw new RuntimeException("Error checking if collection exists: " + e.getMessage());
        }
    }

    public static boolean indexExists(Cluster cluster, String bucketName, String scopeName, String collectionName, String indexName) {
        return cluster
                .bucket(bucketName)
                .scope(scopeName)
                .collection(collectionName)
                .queryIndexes()
                .getAllIndexes()
                .stream()
                .anyMatch(i -> i.name().equals(indexName));
    }

    public static boolean collectionHasPrimaryIndex(Cluster cluster, String bucketName, String scopeName, String collectionName) {
        Collection collection = cluster.bucket(bucketName).scope(scopeName).collection(collectionName);
        return collection.queryIndexes().getAllIndexes().stream().anyMatch(QueryIndex::primary);
    }

    public static void createScopeIfNotExists(Cluster cluster, String bucketName, String scopeName) {
        try {
            if (!CouchbaseUtils.isDefaultScope(scopeName)) {
                CollectionManager cm = cluster.bucket(bucketName).collections();
                cm.createScope(scopeName, CreateScopeOptions.createScopeOptions());
            }
        } catch (ScopeExistsException ignored) {
            // Do nothing
        }
    }

    public static void createCollectionIfNotExists(Cluster cluster, String bucketName, String scopeName, String collectionName) {
        try {
            if (!isDefaultCollection(scopeName, collectionName)) {
                CollectionManager cm = cluster.bucket(bucketName).collections();
                cm.createCollection(scopeName, collectionName, CreateCollectionSettings.createCollectionSettings());
                waitForQueryableCollection(cluster, bucketName, scopeName, collectionName, 20, Duration.ofMillis(250));
            }
        } catch (CollectionExistsException ignored) {
            // Do nothing
        }
    }

    public static void dropCollectionIfExists(Cluster cluster, String bucketName, String scopeName, String collectionName) {
        try {
            if (!isDefaultCollection(scopeName, collectionName)) {
                CollectionManager cm = cluster.bucket(bucketName).collections();
                cm.dropCollection(scopeName, collectionName, DropCollectionOptions.dropCollectionOptions());
            }
        } catch (CollectionNotFoundException ignored) {
            // Do nothing
        }
    }

    public static void waitForQueryableCollection(
            Cluster cluster,
            String bucketName,
            String scopeName,
            String collectionName,
            int maxAttempts,
            Duration delay
    ) {
        String keyspace = String.format(KEYSPACE_TEMPLATE, bucketName, scopeName, collectionName);
        String countQuery = String.format(SELECT_COUNT_QUERY_TEMPLATE, bucketName, scopeName, collectionName);

        int attempts = 0;
        while (attempts++ < maxAttempts) {
            try {
                cluster.query(countQuery);
                return;
            } catch (IndexFailureException | PlanningFailureException e) {
                try {
                    Thread.sleep(delay.toMillis());
                } catch (InterruptedException ignored) {}
            }
            catch (Exception e) {
                throw new RuntimeException("Unexpected error while waiting for collection " + keyspace + " to become queryable: " + e.getMessage(), e);
            }
        }
        throw new RuntimeException("Timeout: Collection " + keyspace + " did not become queryable after " + maxAttempts + " attempts.");
    }

    public static List<JsonObject> selectAllDocuments(Cluster cluster, String bucketName, String scopeName, String collectionName) {
        return cluster.query(String.format(SELECT_ALL_QUERY_TEMPLATE, collectionName, bucketName, scopeName, collectionName),
                            QueryOptions.queryOptions().scanConsistency(QueryScanConsistency.REQUEST_PLUS)
        ).rowsAsObject();
    }

    public static void deleteAllDocuments(Cluster cluster, String bucketName, String scopeName, String collectionName) {
        cluster.query(String.format(DELETE_ALL_QUERY_TEMPLATE, bucketName, scopeName, collectionName));
    }

    public static void createPrimaryIndexIfNotExists(Cluster cluster, String bucketName, String scopeName, String collectionName) {
        cluster.query(String.format(CREATE_PRIMARY_INDEX_TEMPLATE, bucketName, scopeName, collectionName));
    }

    public static void dropPrimaryIndexIfExists(Cluster cluster, String bucketName, String scopeName, String collectionName) {
        cluster.query(String.format(DROP_PRIMARY_INDEX_TEMPLATE, bucketName, scopeName, collectionName));
    }

    public static void dropIndexIfExists(Cluster cluster, String bucketName, String scopeName, String collectionName, String indexName) {
        cluster.query(String.format(DROP_INDEX_TEMPLATE, indexName, bucketName, scopeName, collectionName));
    }
}
