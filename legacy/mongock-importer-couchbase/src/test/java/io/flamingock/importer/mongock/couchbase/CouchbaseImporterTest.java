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
package io.flamingock.importer.mongock.couchbase;

import com.couchbase.client.core.io.CollectionIdentifier;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.manager.bucket.BucketManager;
import com.couchbase.client.java.manager.bucket.BucketSettings;
import io.flamingock.api.annotations.EnableFlamingock;
import io.flamingock.api.annotations.Stage;
import io.flamingock.community.couchbase.driver.CouchbaseAuditStore;
import io.flamingock.internal.common.couchbase.CouchbaseCollectionHelper;
import io.flamingock.internal.core.builder.FlamingockFactory;
import io.flamingock.internal.core.runner.Runner;
import io.flamingock.internal.util.constants.CommunityPersistenceConstants;
import io.flamingock.support.mongock.annotations.MongockSupport;
import io.flamingock.targetsystem.couchbase.CouchbaseTargetSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.couchbase.CouchbaseContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.flamingock.internal.common.core.metadata.Constants.DEFAULT_MONGOCK_ORIGIN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@MongockSupport(targetSystem = "couchbase-target-system")
@EnableFlamingock(stages = {@Stage(location = "io.flamingock.importer.mongock.couchbase.changes")})
public class CouchbaseImporterTest {

    public static final String FLAMINGOCK_BUCKET_NAME = "test";
    public static final String FLAMINGOCK_SCOPE_NAME = "flamingock";
    public static final String FLAMINGOCK_COLLECTION_NAME = CommunityPersistenceConstants.DEFAULT_AUDIT_STORE_NAME;

    public static final String MONGOCK_BUCKET_NAME = "test";
    public static final String MONGOCK_SCOPE_NAME = CollectionIdentifier.DEFAULT_SCOPE;
    public static final String MONGOCK_COLLECTION_NAME = DEFAULT_MONGOCK_ORIGIN;


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
    @DisplayName("GIVEN all Mongock changeUnits already executed" +
            "WHEN migrating to Flamingock Community " +
            "THEN should import the entire history " +
            "AND execute the pending flamingock changes")
    void GIVEN_allMongockChangeUnitsAlreadyExecuted_WHEN_migratingToFlamingockCommunity_THEN_shouldImportEntireHistory() {
        Collection originCollection = cluster.bucket(MONGOCK_BUCKET_NAME).scope(MONGOCK_SCOPE_NAME).collection(MONGOCK_COLLECTION_NAME);

        originCollection.upsert("mongock-change-1", createAuditObject("mongock-change-1"));
        originCollection.upsert("mongock-change-2", createAuditObject("mongock-change-2"));

        CouchbaseTargetSystem targetSystem = new CouchbaseTargetSystem("couchbase-target-system", cluster, FLAMINGOCK_BUCKET_NAME);

        Runner flamingock = FlamingockFactory.getCommunityBuilder()
                .setAuditStore(new CouchbaseAuditStore(cluster, FLAMINGOCK_BUCKET_NAME)
                        .withScopeName(FLAMINGOCK_SCOPE_NAME)
                        .withAuditRepositoryName(FLAMINGOCK_COLLECTION_NAME))
                .addTargetSystem(targetSystem)
                .build();

        flamingock.run();

        validateFlamingockAuditOutput();

    }

    @Test
    @DisplayName("GIVEN some Mongock changeUnits already executed " +
            "AND some other Mongock changeUnits pending for execution" +
            "WHEN migrating to Flamingock Community" +
            "THEN migrates the history with the executed changeUnits " +
            "AND executes the pending Mongock changeUnits " +
            "AND executes the pending Flamingock changes")
    void GIVEN_someChangeUnitsAlreadyExecuted_WHEN_migratingToFlamingockCommunity_THEN_shouldImportEntireHistory() {
        Collection originCollection = cluster.bucket(MONGOCK_BUCKET_NAME).scope(MONGOCK_SCOPE_NAME).collection(MONGOCK_COLLECTION_NAME);

        originCollection.upsert("mongock-change-1", createAuditObject("mongock-change-1"));

        CouchbaseTargetSystem targetSystem = new CouchbaseTargetSystem("couchbase-target-system", cluster, FLAMINGOCK_BUCKET_NAME);

        Runner flamingock = FlamingockFactory.getCommunityBuilder()
                .setAuditStore(new CouchbaseAuditStore(cluster, FLAMINGOCK_BUCKET_NAME)
                        .withScopeName(FLAMINGOCK_SCOPE_NAME)
                        .withAuditRepositoryName(FLAMINGOCK_COLLECTION_NAME))
                .addTargetSystem(targetSystem)
                .build();

        flamingock.run();

        validateFlamingockAuditOutput();

    }


    private static void validateFlamingockAuditOutput() {
        List<JsonObject> auditLog = CouchbaseCollectionHelper.selectAllDocuments(cluster, FLAMINGOCK_BUCKET_NAME, FLAMINGOCK_SCOPE_NAME, FLAMINGOCK_COLLECTION_NAME);


        Map<String, JsonObject> byChangeId = auditLog.stream()
                .collect(Collectors.toMap(
                        e -> e.getString("changeId"),
                        e -> e
                ));


        assertEquals(4, auditLog.size());
        JsonObject importAudit = byChangeId.get("migration-mongock-to-flamingock-community");
        JsonObject mongockAudit1 = byChangeId.get("mongock-change-1");
        JsonObject mongockAudit2 = byChangeId.get("mongock-change-2");
        JsonObject flamingockAudit = byChangeId.get("flamingock-change");
        assertNotNull(importAudit);
        assertEquals("APPLIED", importAudit.getString("state"));

        assertNotNull(mongockAudit1);
        assertEquals("APPLIED", mongockAudit1.getString("state"));

        assertNotNull(mongockAudit2);
        assertEquals("APPLIED", mongockAudit2.getString("state"));

        assertNotNull(flamingockAudit);
        assertEquals("APPLIED", flamingockAudit.getString("state"));
    }

    private static JsonObject createAuditObject(String value) {
        JsonObject doc = JsonObject.create()
                .put("executionId", "exec-1")
                .put("changeId", value)
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
        return doc;
    }

}