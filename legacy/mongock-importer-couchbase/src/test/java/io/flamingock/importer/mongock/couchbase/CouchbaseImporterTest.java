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
import io.flamingock.core.kit.TestKit;
import io.flamingock.core.kit.audit.AuditTestHelper;
import io.flamingock.couchbase.kit.CouchbaseTestKit;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.store.couchbase.CouchbaseAuditStore;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.common.couchbase.CouchbaseCollectionHelper;
import io.flamingock.internal.core.builder.FlamingockFactory;
import io.flamingock.internal.core.builder.runner.Runner;
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

import static io.flamingock.core.kit.audit.AuditEntryExpectation.APPLIED;
import static io.flamingock.core.kit.audit.AuditEntryExpectation.STARTED;
import static io.flamingock.internal.common.core.metadata.Constants.MONGOCK_IMPORT_EMPTY_ORIGIN_ALLOWED_PROPERTY_KEY;
import static io.flamingock.internal.common.core.metadata.Constants.MONGOCK_IMPORT_ORIGIN_PROPERTY_KEY;
import static io.flamingock.internal.common.core.metadata.Constants.MONGOCK_IMPORT_SKIP_PROPERTY_KEY;
import static io.flamingock.internal.util.constants.AuditEntryFieldConstants.KEY_CREATED_AT;
import static io.flamingock.internal.util.constants.AuditEntryFieldConstants.KEY_STATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    public static final String CUSTOM_MONGOCK_ORIGIN_SCOPE = "mongockCustomScope";
    public static final String CUSTOM_MONGOCK_ORIGIN_COLLECTION = "mongockCustomOriginCollection";


    @Container
    static final CouchbaseContainer couchbaseContainer = new CouchbaseContainer("couchbase/server:7.2.4")
            .withBucket(new org.testcontainers.couchbase.BucketDefinition(FLAMINGOCK_BUCKET_NAME));

    private static Cluster cluster;
    private static CouchbaseTargetSystem targetSystem;
    private static CouchbaseAuditStore auditStore;
    private static TestKit testKit;
    private static AuditTestHelper auditHelper;

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

        targetSystem = new CouchbaseTargetSystem("couchbase-target-system", cluster, FLAMINGOCK_BUCKET_NAME);
        auditStore = CouchbaseAuditStore.from(targetSystem)
            .withScopeName(FLAMINGOCK_SCOPE_NAME)
            .withAuditRepositoryName(FLAMINGOCK_COLLECTION_NAME);

        testKit = CouchbaseTestKit.create(auditStore, cluster, FLAMINGOCK_BUCKET_NAME, FLAMINGOCK_SCOPE_NAME);
        auditHelper = testKit.getAuditHelper();

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
        CouchbaseCollectionHelper.waitUntilEmpty(cluster, FLAMINGOCK_BUCKET_NAME, FLAMINGOCK_SCOPE_NAME, FLAMINGOCK_COLLECTION_NAME, Duration.ofSeconds(10));

        CouchbaseCollectionHelper.deleteAllDocuments(cluster, MONGOCK_BUCKET_NAME, MONGOCK_SCOPE_NAME, MONGOCK_COLLECTION_NAME);
        CouchbaseCollectionHelper.waitUntilEmpty(cluster, MONGOCK_BUCKET_NAME, MONGOCK_SCOPE_NAME, MONGOCK_COLLECTION_NAME, Duration.ofSeconds(10));

        if (CouchbaseCollectionHelper.collectionExists(cluster, MONGOCK_BUCKET_NAME, CUSTOM_MONGOCK_ORIGIN_SCOPE, CUSTOM_MONGOCK_ORIGIN_COLLECTION)) {
            CouchbaseCollectionHelper.deleteAllDocuments(cluster, MONGOCK_BUCKET_NAME, CUSTOM_MONGOCK_ORIGIN_SCOPE, CUSTOM_MONGOCK_ORIGIN_COLLECTION);
            CouchbaseCollectionHelper.waitUntilEmpty(cluster, MONGOCK_BUCKET_NAME, CUSTOM_MONGOCK_ORIGIN_SCOPE, CUSTOM_MONGOCK_ORIGIN_COLLECTION, Duration.ofSeconds(10));
        }
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

        Runner flamingock = testKit.createBuilder()
                .setAuditStore(auditStore)
                .addTargetSystem(targetSystem)
                .build();

        flamingock.run();

        auditHelper.verifyAuditSequenceStrict(
            // Legacy imports from Mongock (APPLIED only - no STARTED for imported changes)
            APPLIED("mongock-change-1"),
            APPLIED("mongock-change-2"),

            // System stage - actual system importer change
            STARTED("migration-mongock-to-flamingock-community"),
            APPLIED("migration-mongock-to-flamingock-community"),

            // Application stage - new changes
            STARTED("flamingock-change"),
            APPLIED("flamingock-change")
        );

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

        Runner flamingock = testKit.createBuilder()
                .setAuditStore(auditStore)
                .addTargetSystem(targetSystem)
                .build();

        flamingock.run();

        auditHelper.verifyAuditSequenceStrict(
            // Legacy imports from Mongock (APPLIED only - no STARTED for imported changes)
            APPLIED("mongock-change-1"),

            // System stage - actual system importer change
            STARTED("migration-mongock-to-flamingock-community"),
            APPLIED("migration-mongock-to-flamingock-community"),

            // Application stage - new changes
            STARTED("mongock-change-2"),
            APPLIED("mongock-change-2"),
            STARTED("flamingock-change"),
            APPLIED("flamingock-change")
        );

    }

    @Test
    @DisplayName("GIVEN mongock audit history empty " +
            "AND no empty origen allowed value provided " +
            "WHEN migrating to Flamingock Community" +
            "THEN should throw exception")
    void GIVEN_mongockAuditHistoryEmptyAndNoFailIfEmptyOriginValueProvided_WHEN_migratingToFlamingockCommunity_THEN_shouldThrowException() {

        Runner flamingock = testKit.createBuilder()
                .setAuditStore(auditStore)
                .addTargetSystem(targetSystem)
                .build();

        FlamingockException ex = assertThrows(FlamingockException.class, flamingock::run);
        assertEquals("No audit entries found when importing from 'couchbase-target-system'.", ex.getMessage());

    }

    @Test
    @DisplayName("GIVEN mongock audit history empty " +
            "AND explicit empty origin allowed disabled " +
            "WHEN migrating to Flamingock Community" +
            "THEN should throw exception")
    void GIVEN_mongockAuditHistoryEmptyAndFailIfEmptyOriginEnabled_WHEN_migratingToFlamingockCommunity_THEN_shouldThrowException() {

        Runner flamingock = testKit.createBuilder()
                .setAuditStore(auditStore)
                .addTargetSystem(targetSystem)
                .setProperty(MONGOCK_IMPORT_EMPTY_ORIGIN_ALLOWED_PROPERTY_KEY, Boolean.FALSE.toString())
                .build();

        FlamingockException ex = assertThrows(FlamingockException.class, flamingock::run);
        assertEquals("No audit entries found when importing from 'couchbase-target-system'.", ex.getMessage());

    }

    @Test
    @DisplayName("GIVEN mongock audit history empty " +
            "AND explicit empty origin allowed enabled " +
            "WHEN migrating to Flamingock Community " +
            "THEN should execute the pending Mongock changeUnits " +
            "AND execute the pending flamingock changes")
    void GIVEN_mongockAuditHistoryEmptyAndFailIfEmptyOriginDisabled_WHEN_migratingToFlamingockCommunity_THEN_shouldThrowException() {

        CouchbaseTargetSystem targetSystem = new CouchbaseTargetSystem("couchbase-target-system", cluster, FLAMINGOCK_BUCKET_NAME);

        Runner flamingock = testKit.createBuilder()
                .setAuditStore(auditStore)
                .addTargetSystem(targetSystem)
                .setProperty(MONGOCK_IMPORT_EMPTY_ORIGIN_ALLOWED_PROPERTY_KEY, Boolean.TRUE.toString())
                .build();

        flamingock.run();

        auditHelper.verifyAuditSequenceStrict(
            // System stage - actual system importer change
            STARTED("migration-mongock-to-flamingock-community"),
            APPLIED("migration-mongock-to-flamingock-community"),

            // Application stage - new changes
            STARTED("mongock-change-1"),
            APPLIED("mongock-change-1"),
            STARTED("mongock-change-2"),
            APPLIED("mongock-change-2"),
            STARTED("flamingock-change"),
            APPLIED("flamingock-change")
        );

    }

    @Test
    @DisplayName("GIVEN all Mongock changeUnits already executed" +
            "AND custom origin repository name provided" +
            "WHEN migrating to Flamingock Community " +
            "THEN should import the entire history " +
            "AND execute the pending flamingock changes")
    void GIVEN_allMongockChangeUnitsAlreadyExecutedAndCustomOriginProvided_WHEN_migratingToFlamingockCommunity_THEN_shouldImportEntireHistory() {
        // Setup Mongock entries
        final String customMongockOrigin = String.format("%s.%s", CUSTOM_MONGOCK_ORIGIN_SCOPE, CUSTOM_MONGOCK_ORIGIN_COLLECTION);

        CouchbaseCollectionHelper.createScopeIfNotExists(cluster, MONGOCK_BUCKET_NAME, CUSTOM_MONGOCK_ORIGIN_SCOPE);
        CouchbaseCollectionHelper.createCollectionIfNotExists(cluster, MONGOCK_BUCKET_NAME, CUSTOM_MONGOCK_ORIGIN_SCOPE, CUSTOM_MONGOCK_ORIGIN_COLLECTION);
        CouchbaseCollectionHelper.createPrimaryIndexIfNotExists(cluster, MONGOCK_BUCKET_NAME, CUSTOM_MONGOCK_ORIGIN_SCOPE, CUSTOM_MONGOCK_ORIGIN_COLLECTION);

        Collection originCollection = cluster.bucket(MONGOCK_BUCKET_NAME).scope(CUSTOM_MONGOCK_ORIGIN_SCOPE).collection(CUSTOM_MONGOCK_ORIGIN_COLLECTION);

        originCollection.upsert("mongock-change-1", createAuditObject("mongock-change-1"));

        CouchbaseTargetSystem targetSystem = new CouchbaseTargetSystem("couchbase-target-system", cluster, FLAMINGOCK_BUCKET_NAME);

        Runner flamingock = testKit.createBuilder()
                .setAuditStore(auditStore)
                .addTargetSystem(targetSystem)
                .setProperty(MONGOCK_IMPORT_ORIGIN_PROPERTY_KEY, customMongockOrigin)
                .build();

        flamingock.run();

        auditHelper.verifyAuditSequenceStrict(
            // Legacy imports from Mongock (APPLIED only - no STARTED for imported changes)
            APPLIED("mongock-change-1"),

            // System stage - actual system importer change
            STARTED("migration-mongock-to-flamingock-community"),
            APPLIED("migration-mongock-to-flamingock-community"),

            // Application stage - new changes
            STARTED("mongock-change-2"),
            APPLIED("mongock-change-2"),
            STARTED("flamingock-change"),
            APPLIED("flamingock-change")
        );

    }

    @Test
    @DisplayName("GIVEN skip import flag with invalid value " +
            "WHEN migrating to Flamingock Community" +
            "THEN should throw exception")
    void GIVEN_skipImportFlagWithInvalidValue_WHEN_migratingToFlamingockCommunity_THEN_shouldThrowException() {

        final String SKIP_IMPORT_VALUE = "invalid_value";

        CouchbaseTargetSystem targetSystem = new CouchbaseTargetSystem("couchbase-target-system", cluster, FLAMINGOCK_BUCKET_NAME);

        Runner flamingock = testKit.createBuilder()
                .setAuditStore(auditStore)
                .addTargetSystem(targetSystem)
                .setProperty(MONGOCK_IMPORT_SKIP_PROPERTY_KEY, SKIP_IMPORT_VALUE) // only allows empty / true / false
                .build();

        FlamingockException ex = assertThrows(FlamingockException.class, flamingock::run);
        assertEquals("Invalid value for " + MONGOCK_IMPORT_SKIP_PROPERTY_KEY + ": " + SKIP_IMPORT_VALUE
                + " (expected \"true\" or \"false\" or empty)", ex.getMessage());
    }

    @Test
    @DisplayName("GIVEN all Mongock changeUnits already executed " +
            "AND skip import flag enabled " +
            "WHEN migrating to Flamingock Community" +
            "THEN should not import any audit history entry " +
            "AND execute the all mongock and flamingock changes")
    void GIVEN_skipImportFlagEnabled_WHEN_migratingToFlamingockCommunity_THEN_shouldNotMigrateAnyAuditLog() {

        Collection originCollection = cluster.bucket(MONGOCK_BUCKET_NAME).scope(MONGOCK_SCOPE_NAME).collection(MONGOCK_COLLECTION_NAME);

        originCollection.upsert("mongock-change-1", createAuditObject("mongock-change-1"));
        originCollection.upsert("mongock-change-2", createAuditObject("mongock-change-2"));

        final String SKIP_IMPORT_VALUE = "true";

        CouchbaseTargetSystem targetSystem = new CouchbaseTargetSystem("couchbase-target-system", cluster, FLAMINGOCK_BUCKET_NAME);

        Runner flamingock = testKit.createBuilder()
                .setAuditStore(auditStore)
                .addTargetSystem(targetSystem)
                .setProperty(MONGOCK_IMPORT_SKIP_PROPERTY_KEY, SKIP_IMPORT_VALUE) // only allows empty / true / false
                .build();

        flamingock.run();

        auditHelper.verifyAuditSequenceStrict(
            // System stage - actual system importer change
            STARTED("migration-mongock-to-flamingock-community"),
            APPLIED("migration-mongock-to-flamingock-community"),

            // Application stage - new changes
            STARTED("mongock-change-1"),
            APPLIED("mongock-change-1"),
            STARTED("mongock-change-2"),
            APPLIED("mongock-change-2"),
            STARTED("flamingock-change"),
            APPLIED("flamingock-change")
        );

    }

    @Test
    @DisplayName("GIVEN all Mongock changeUnits already executed " +
            "AND skip import flag disabled (explicit) " +
            "WHEN migrating to Flamingock Community " +
            "THEN should import the entire history " +
            "AND execute the pending flamingock changes")
    void GIVEN_allMongockChangeUnitsAlreadyExecutedAndSkipImportFlagDisabledExplicit_WHEN_migratingToFlamingockCommunity_THEN_shouldImportEntireHistory() {
        Collection originCollection = cluster.bucket(MONGOCK_BUCKET_NAME).scope(MONGOCK_SCOPE_NAME).collection(MONGOCK_COLLECTION_NAME);

        originCollection.upsert("mongock-change-1", createAuditObject("mongock-change-1"));
        originCollection.upsert("mongock-change-2", createAuditObject("mongock-change-2"));

        CouchbaseTargetSystem targetSystem = new CouchbaseTargetSystem("couchbase-target-system", cluster, FLAMINGOCK_BUCKET_NAME);

        final String SKIP_IMPORT_VALUE = "false";

        Runner flamingock = testKit.createBuilder()
                .setAuditStore(auditStore)
                .addTargetSystem(targetSystem)
                .setProperty(MONGOCK_IMPORT_SKIP_PROPERTY_KEY, SKIP_IMPORT_VALUE) // only allows empty / true / false
                .build();

        flamingock.run();

        auditHelper.verifyAuditSequenceStrict(
            // Legacy imports from Mongock (APPLIED only - no STARTED for imported changes)
            APPLIED("mongock-change-1"),
            APPLIED("mongock-change-2"),

            // System stage - actual system importer change
            STARTED("migration-mongock-to-flamingock-community"),
            APPLIED("migration-mongock-to-flamingock-community"),

            // Application stage - new changes
            STARTED("flamingock-change"),
            APPLIED("flamingock-change")
        );

    }

    @Test
    @DisplayName("GIVEN all Mongock changeUnits already executed " +
            "AND skip import flag disabled (implicit) " +
            "WHEN migrating to Flamingock Community " +
            "THEN should import the entire history " +
            "AND execute the pending flamingock changes")
    void GIVEN_allMongockChangeUnitsAlreadyExecutedAndSkipImportFlagDisabledImplicit_WHEN_migratingToFlamingockCommunity_THEN_shouldImportEntireHistory() {
        Collection originCollection = cluster.bucket(MONGOCK_BUCKET_NAME).scope(MONGOCK_SCOPE_NAME).collection(MONGOCK_COLLECTION_NAME);

        originCollection.upsert("mongock-change-1", createAuditObject("mongock-change-1"));
        originCollection.upsert("mongock-change-2", createAuditObject("mongock-change-2"));

        CouchbaseTargetSystem targetSystem = new CouchbaseTargetSystem("couchbase-target-system", cluster, FLAMINGOCK_BUCKET_NAME);

        final String SKIP_IMPORT_VALUE = "";

        Runner flamingock = testKit.createBuilder()
                .setAuditStore(auditStore)
                .addTargetSystem(targetSystem)
                .setProperty(MONGOCK_IMPORT_SKIP_PROPERTY_KEY, SKIP_IMPORT_VALUE) // only allows empty / true / false
                .build();

        flamingock.run();

        auditHelper.verifyAuditSequenceStrict(
            // Legacy imports from Mongock (APPLIED only - no STARTED for imported changes)
            APPLIED("mongock-change-1"),
            APPLIED("mongock-change-2"),

            // System stage - actual system importer change
            STARTED("migration-mongock-to-flamingock-community"),
            APPLIED("migration-mongock-to-flamingock-community"),

            // Application stage - new changes
            STARTED("flamingock-change"),
            APPLIED("flamingock-change")
        );

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
