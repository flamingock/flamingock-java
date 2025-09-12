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
package io.flamingock.importer.couchbase;

import com.couchbase.client.core.io.CollectionIdentifier;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.manager.bucket.BucketManager;
import com.couchbase.client.java.manager.bucket.BucketSettings;
import io.flamingock.api.annotations.EnableFlamingock;
import io.flamingock.api.annotations.Stage;
import io.flamingock.community.couchbase.driver.CouchbaseAuditStore;
import io.flamingock.internal.core.builder.FlamingockFactory;
import io.flamingock.internal.core.store.audit.community.CommunityPersistenceConstants;
import io.flamingock.internal.core.runner.Runner;
import io.flamingock.internal.common.couchbase.CouchbaseCollectionHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.couchbase.CouchbaseContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static io.flamingock.api.StageType.LEGACY;
import static io.flamingock.api.StageType.SYSTEM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

@EnableFlamingock(
        stages = {
                @Stage(location = "io.flamingock.importer.couchbase.system", type = SYSTEM),
                @Stage(location = "io.flamingock.importer.couchbase.legacy", type = LEGACY),
                @Stage(location = "io.flamingock.importer.couchbase.couchbase")
        }
)
@Testcontainers
public class CouchbaseImporterTest {

    public static final String FLAMINGOCK_BUCKET_NAME = "test";
    public static final String FLAMINGOCK_SCOPE_NAME = "flamingock";
    public static final String FLAMINGOCK_COLLECTION_NAME = CommunityPersistenceConstants.DEFAULT_AUDIT_STORE_NAME;

    public static final String MONGOCK_BUCKET_NAME = "test";
    public static final String MONGOCK_SCOPE_NAME = CollectionIdentifier.DEFAULT_SCOPE;
    public static final String MONGOCK_COLLECTION_NAME = CollectionIdentifier.DEFAULT_COLLECTION;


    @Container
    static final CouchbaseContainer couchbaseContainer = new CouchbaseContainer("couchbase/server:7.2.4")
            .withBucket(new org.testcontainers.couchbase.BucketDefinition(FLAMINGOCK_BUCKET_NAME));

    private static Cluster cluster;

    @BeforeAll
    static void setupAll() {
        couchbaseContainer.start();
        cluster = Cluster.connect(
                couchbaseContainer.getConnectionString(),
                couchbaseContainer.getUsername(),
                couchbaseContainer.getPassword()
        );
        cluster.bucket(FLAMINGOCK_BUCKET_NAME).waitUntilReady(Duration.ofSeconds(10));

        // Setup Mongock Bucket, Scope and Collection
        BucketManager bucketManager = cluster.buckets();

        int ramQuotaMB = 100;

        if (!bucketManager.getAllBuckets().containsKey(MONGOCK_BUCKET_NAME)) {
            bucketManager.createBucket(BucketSettings.create(MONGOCK_BUCKET_NAME).ramQuotaMB(ramQuotaMB));
            cluster.bucket(MONGOCK_BUCKET_NAME).waitUntilReady(Duration.ofSeconds(10));
        }

        CouchbaseCollectionHelper.createScopeIfNotExists(cluster, MONGOCK_BUCKET_NAME, MONGOCK_SCOPE_NAME);
        CouchbaseCollectionHelper.createCollectionIfNotExists(cluster, MONGOCK_BUCKET_NAME, MONGOCK_SCOPE_NAME, MONGOCK_COLLECTION_NAME);
        CouchbaseCollectionHelper.createPrimaryIndexIfNotExists(cluster, MONGOCK_BUCKET_NAME, MONGOCK_SCOPE_NAME, MONGOCK_COLLECTION_NAME);
    }

    @AfterEach
    void cleanUp() {
        CouchbaseCollectionHelper.deleteAllDocuments(cluster, FLAMINGOCK_BUCKET_NAME, FLAMINGOCK_SCOPE_NAME, FLAMINGOCK_COLLECTION_NAME);
        CouchbaseCollectionHelper.deleteAllDocuments(cluster, MONGOCK_BUCKET_NAME, MONGOCK_SCOPE_NAME, MONGOCK_COLLECTION_NAME);
    }

    @Test
    void testImporterIntegration() {
        Collection originCollection = cluster.bucket(MONGOCK_BUCKET_NAME).scope(MONGOCK_SCOPE_NAME).collection(MONGOCK_COLLECTION_NAME);
        JsonObject doc = JsonObject.create()
                .put("executionId", "exec-1")
                .put("changeId", "change-1")
                .put("author", "author1")
                .put("timestamp", String.valueOf(Instant.now().toEpochMilli()))
                .put("state", "EXECUTED")
                .put("type", "EXECUTION")
                .put("changeLogClass", "io.flamingock.changelog.Class1")
                .put("changeSetMethod", "method1")
                .putNull("metadata")
                .put("executionMillis", 123L)
                .put("executionHostName", "host1")
                .putNull("errorTrace")
                .put("systemChange", true)
                .put("_doctype", "mongockChangeEntry");
        originCollection.upsert("change-1", doc);

        Runner flamingock = FlamingockFactory.getCommunityBuilder()
                .setAuditStore(new CouchbaseAuditStore())
                .addDependency(cluster)
                .addDependency(cluster.bucket(FLAMINGOCK_BUCKET_NAME))
                .setProperty("couchbase.scopeName", FLAMINGOCK_SCOPE_NAME)
                .setProperty("couchbase.auditRepositoryName", FLAMINGOCK_COLLECTION_NAME)
                .setRelaxTargetSystemValidation(true)
                .build();

        flamingock.run();

        List<JsonObject> auditLog = CouchbaseCollectionHelper.selectAllDocuments(cluster, FLAMINGOCK_BUCKET_NAME, FLAMINGOCK_SCOPE_NAME, FLAMINGOCK_COLLECTION_NAME);

        assertFalse(auditLog.isEmpty(), "Audit log should not be empty");

        JsonObject entry = auditLog.stream()
                .filter(e -> "change-1".equals(e.getString("changeId")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Entry with changeId 'change-1' not found"));

        assertEquals("change-1", entry.getString("changeId"));
        assertEquals("author1", entry.getString("author"));
        assertEquals("exec-1", entry.getString("executionId"));
        assertEquals("APPLIED", entry.getString("state"));
        assertTrue(entry.getBoolean("systemChange"));
    }

    @Test
    void failIfEmptyOrigin() {
        Runner flamingock = FlamingockFactory.getCommunityBuilder()
                .setAuditStore(new CouchbaseAuditStore())
                .addDependency(cluster)
                .addDependency(cluster.bucket(FLAMINGOCK_BUCKET_NAME))
                .setProperty("couchbase.scopeName", FLAMINGOCK_SCOPE_NAME)
                .setProperty("couchbase.auditRepositoryName", FLAMINGOCK_COLLECTION_NAME)
                .setRelaxTargetSystemValidation(true)
                .build();

        org.junit.jupiter.api.Assertions.assertThrows(
                io.flamingock.internal.common.core.error.FlamingockException.class,
                flamingock::run
        );
    }
}