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
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.store.couchbase.CouchbaseAuditStore;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.common.couchbase.CouchbaseCollectionHelper;
import io.flamingock.internal.core.builder.FlamingockFactory;
import io.flamingock.internal.core.runner.Runner;
import io.flamingock.internal.util.constants.CommunityPersistenceConstants;
import io.flamingock.support.mongock.annotations.MongockSupport;
import io.flamingock.targetsystem.couchbase.CouchbaseTargetSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.couchbase.CouchbaseContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static io.flamingock.internal.common.core.metadata.Constants.DEFAULT_MONGOCK_ORIGIN;
import static io.flamingock.internal.util.constants.AuditEntryFieldConstants.KEY_CREATED_AT;
import static io.flamingock.internal.util.constants.AuditEntryFieldConstants.KEY_STATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
                .setAuditStore(CouchbaseAuditStore.from(targetSystem)
                        .withScopeName(FLAMINGOCK_SCOPE_NAME)
                        .withAuditRepositoryName(FLAMINGOCK_COLLECTION_NAME))
                .addTargetSystem(targetSystem)
                .build();

        flamingock.run();

        List<JsonObject> auditLog = getAuditLog();

        assertEquals(6, auditLog.size());

        assertEquals("mongock-change-1", auditLog.get(0).getString("changeId"));
        assertEquals("APPLIED", auditLog.get(0).getString("state"));

        assertEquals("mongock-change-2", auditLog.get(1).getString("changeId"));
        assertEquals("APPLIED", auditLog.get(1).getString("state"));

        assertEquals("migration-mongock-to-flamingock-community", auditLog.get(2).getString("changeId"));
        assertEquals("STARTED", auditLog.get(2).getString("state"));

        assertEquals("migration-mongock-to-flamingock-community", auditLog.get(3).getString("changeId"));
        assertEquals("APPLIED", auditLog.get(3).getString("state"));

        assertEquals("flamingock-change", auditLog.get(4).getString("changeId"));
        assertEquals("STARTED", auditLog.get(4).getString("state"));

        assertEquals("flamingock-change", auditLog.get(5).getString("changeId"));
        assertEquals("APPLIED", auditLog.get(5).getString("state"));
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
                .setAuditStore(CouchbaseAuditStore.from(targetSystem)
                        .withScopeName(FLAMINGOCK_SCOPE_NAME)
                        .withAuditRepositoryName(FLAMINGOCK_COLLECTION_NAME))
                .addTargetSystem(targetSystem)
                .build();

        flamingock.run();

        List<JsonObject> auditLog = getAuditLog();

        assertEquals(7, auditLog.size());

        assertEquals("mongock-change-1", auditLog.get(0).getString("changeId"));
        assertEquals("APPLIED", auditLog.get(0).getString("state"));

        assertEquals("migration-mongock-to-flamingock-community", auditLog.get(1).getString("changeId"));
        assertEquals("STARTED", auditLog.get(1).getString("state"));

        assertEquals("migration-mongock-to-flamingock-community", auditLog.get(2).getString("changeId"));
        assertEquals("APPLIED", auditLog.get(2).getString("state"));

        assertEquals("mongock-change-2", auditLog.get(3).getString("changeId"));
        assertEquals("STARTED", auditLog.get(3).getString("state"));

        assertEquals("mongock-change-2", auditLog.get(4).getString("changeId"));
        assertEquals("APPLIED", auditLog.get(4).getString("state"));

        assertEquals("flamingock-change", auditLog.get(5).getString("changeId"));
        assertEquals("STARTED", auditLog.get(5).getString("state"));

        assertEquals("flamingock-change", auditLog.get(6).getString("changeId"));
        assertEquals("APPLIED", auditLog.get(6).getString("state"));
    }

    @Test
    @DisplayName("GIVEN mongock audit history empty " +
            "WHEN migrating to Flamingock Community" +
            "THEN should throw exception")
    void GIVEN_mongockAuditHistoryEmpty_WHEN_migratingToFlamingockCommunity_THEN_shouldThrowException() {
        // Setup Mongock entries

        CouchbaseTargetSystem targetSystem = new CouchbaseTargetSystem("couchbase-target-system", cluster, FLAMINGOCK_BUCKET_NAME);

        Runner flamingock = FlamingockFactory.getCommunityBuilder()
                .setAuditStore(CouchbaseAuditStore.from(targetSystem)
                        .withScopeName(FLAMINGOCK_SCOPE_NAME)
                        .withAuditRepositoryName(FLAMINGOCK_COLLECTION_NAME))
                .addTargetSystem(targetSystem)
                .build();

        FlamingockException ex = assertThrows(FlamingockException.class, flamingock::run);
        assertEquals("No audit entries found when importing from 'couchbase-target-system'.", ex.getMessage());

    }

    private List<JsonObject> getAuditLog() {
        return CouchbaseCollectionHelper.selectAllDocuments(cluster, FLAMINGOCK_BUCKET_NAME, FLAMINGOCK_SCOPE_NAME, FLAMINGOCK_COLLECTION_NAME)
                .stream()
                .sorted(Comparator.comparingLong(o -> ((JsonObject)o).getLong(KEY_CREATED_AT))
                        .thenComparing(o -> AuditEntry.Status.valueOf(((JsonObject)o).getString(KEY_STATE)).getPriority()))
                .collect(Collectors.toList());
    }

    private static JsonObject createAuditObject(String value) {
        JsonObject doc = JsonObject.create()
                .put("executionId", "exec-1")
                .put("changeId", value)
                .put("author", "author1")
                .put("timestamp", Instant.now().toEpochMilli())
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
