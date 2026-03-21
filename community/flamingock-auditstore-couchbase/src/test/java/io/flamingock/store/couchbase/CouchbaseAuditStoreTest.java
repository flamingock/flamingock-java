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
package io.flamingock.store.couchbase;

import com.couchbase.client.core.io.CollectionIdentifier;
import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.json.JsonObject;
import io.flamingock.common.test.pipeline.CodeChangeTestDefinition;
import io.flamingock.core.kit.audit.AuditTestSupport;
import io.flamingock.couchbase.kit.CouchbaseTestKit;
import io.flamingock.targetsystem.couchbase.CouchbaseTargetSystem;
import io.flamingock.internal.common.couchbase.CouchbaseCollectionHelper;
import io.flamingock.internal.core.operation.OperationException;
import org.junit.jupiter.api.*;
import org.testcontainers.couchbase.BucketDefinition;
import org.testcontainers.couchbase.CouchbaseContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.Collections;

import static io.flamingock.core.kit.audit.AuditEntryExpectation.*;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class CouchbaseAuditStoreTest {

    private static final String BUCKET_NAME = "test";

    private static Cluster cluster;
    private static CouchbaseTestHelper couchbaseTestHelper;
    private CouchbaseTargetSystem couchbaseTargetSystem;
    private CouchbaseAuditStore couchbaseAuditStore;
    private CouchbaseTestKit testKit;

    @Container
    public static final CouchbaseContainer couchbaseContainer = new CouchbaseContainer("couchbase/server:7.2.4")
            .withBucket(new BucketDefinition(BUCKET_NAME));

    @BeforeAll
    static void beforeAll() {
        couchbaseContainer.start();
        cluster = Cluster.connect(
                couchbaseContainer.getConnectionString(),
                couchbaseContainer.getUsername(),
                couchbaseContainer.getPassword());
        cluster.bucket(BUCKET_NAME).waitUntilReady(Duration.ofSeconds(10));
        couchbaseTestHelper = new CouchbaseTestHelper(cluster);
    }

    @BeforeEach
    void setupEach() {
        couchbaseTargetSystem = new CouchbaseTargetSystem("couchbase", cluster, BUCKET_NAME);
        couchbaseAuditStore = CouchbaseAuditStore.from(couchbaseTargetSystem);
        testKit = CouchbaseTestKit.create(couchbaseAuditStore, cluster, BUCKET_NAME, CollectionIdentifier.DEFAULT_SCOPE);
    }

    @AfterEach
    void tearDownEach() {
        testKit.cleanUp();
    }


    @Test
    @DisplayName("When standalone runs the AuditStore should persist the audit logs and the test data")
    void happyPath() {
        Bucket bucket = cluster.bucket(BUCKET_NAME);
        Collection testCollection = bucket.defaultCollection();

        String[] expectedTaskIds = {"create-index", "insert-document", "insert-another-document"};
        //Given-When-Then
        AuditTestSupport.withTestKit(testKit)
            .GIVEN_Changes(
                new CodeChangeTestDefinition(io.flamingock.store.couchbase.changes.happyPath._001__create_index.class, Collections.singletonList(Collection.class)),
                new CodeChangeTestDefinition(io.flamingock.store.couchbase.changes.happyPath._002__insert_document.class, Collections.singletonList(Collection.class)),
                new CodeChangeTestDefinition(io.flamingock.store.couchbase.changes.happyPath._003__insert_another_document.class, Collections.singletonList(Collection.class))
            )
            .WHEN(() -> testKit.createBuilder()
                .setAuditStore(couchbaseAuditStore)
                .addTargetSystem(couchbaseTargetSystem)
                .addDependency(testCollection) // for test purpose only
                .build()
                .run())
            .THEN_VerifyAuditSequenceStrict(
                STARTED(expectedTaskIds[0]),
                APPLIED(expectedTaskIds[0]),
                STARTED(expectedTaskIds[1]),
                APPLIED(expectedTaskIds[1]),
                STARTED(expectedTaskIds[2]),
                APPLIED(expectedTaskIds[2])
            )
            .run();

        //Checking created index and documents
        assertTrue(CouchbaseCollectionHelper.indexExists(cluster, testCollection.bucketName(), testCollection.scopeName(), testCollection.name(), "idx_standalone_index"));
        JsonObject jsonObject;
        jsonObject = testCollection.get("test-client-Federico").contentAsObject();
        assertNotNull(jsonObject);
        assertEquals("Federico", jsonObject.get("name"));
        jsonObject = testCollection.get("test-client-Jorge").contentAsObject();
        assertNotNull(jsonObject);
        assertEquals("Jorge", jsonObject.get("name"));
    }


    @Test
    @DisplayName("When standalone runs the AuditStore and execution fails (with rollback method) should persist all the audit logs up to the failed one (ROLLED_BACK)")
    void failedWithRollback() {
        Bucket bucket = cluster.bucket(BUCKET_NAME);
        Collection testCollection = bucket.defaultCollection();

        String[] expectedTaskIds = {"create-index", "insert-document", "execution-with-exception"};
        //Given-When-Then
        AuditTestSupport.withTestKit(testKit)
            .GIVEN_Changes(
                new CodeChangeTestDefinition(io.flamingock.store.couchbase.changes.failedWithRollback._001__create_index.class, Collections.singletonList(Collection.class)),
                new CodeChangeTestDefinition(io.flamingock.store.couchbase.changes.failedWithRollback._002__insert_document.class, Collections.singletonList(Collection.class)),
                new CodeChangeTestDefinition(io.flamingock.store.couchbase.changes.failedWithRollback._003__execution_with_exception.class, Collections.singletonList(Collection.class), Collections.singletonList(Collection.class))
            )
            .WHEN(() -> {
                assertThrows(OperationException.class, () -> {
                    testKit.createBuilder()
                        .setAuditStore(couchbaseAuditStore)
                        .addTargetSystem(couchbaseTargetSystem)
                        .addDependency(testCollection) // for test purpose only
                        .build()
                        .run();
                });
            })
            .THEN_VerifyAuditSequenceStrict(
                STARTED(expectedTaskIds[0]),
                APPLIED(expectedTaskIds[0]),
                STARTED(expectedTaskIds[1]),
                APPLIED(expectedTaskIds[1]),
                STARTED(expectedTaskIds[2]),
                FAILED(expectedTaskIds[2]),
                ROLLED_BACK(expectedTaskIds[2])
            )
            .run();

        //Checking created index and documents
        assertTrue(CouchbaseCollectionHelper.indexExists(cluster, testCollection.bucketName(), testCollection.scopeName(), testCollection.name(), "idx_standalone_index"));
        JsonObject jsonObject;
        jsonObject = testCollection.get("test-client-Federico").contentAsObject();
        assertNotNull(jsonObject);
        assertEquals("Federico", jsonObject.get("name"));
        assertFalse(testCollection.exists("test-client-Jorge").exists());
    }

    @Test
    @DisplayName("When standalone runs the AuditStore and execution fails (without rollback method) should persist all the audit logs up to the failed one (FAILED)")
    void failedWithoutRollback() {
        Bucket bucket = cluster.bucket(BUCKET_NAME);
        Collection testCollection = bucket.defaultCollection();

        String[] expectedTaskIds = {"create-index", "insert-document", "execution-with-exception"};
        //Given-When-Then
        AuditTestSupport.withTestKit(testKit)
            .GIVEN_Changes(
                new CodeChangeTestDefinition(io.flamingock.store.couchbase.changes.failedWithoutRollback._001__create_index.class, Collections.singletonList(Collection.class)),
                new CodeChangeTestDefinition(io.flamingock.store.couchbase.changes.failedWithoutRollback._002__insert_document.class, Collections.singletonList(Collection.class)),
                new CodeChangeTestDefinition(io.flamingock.store.couchbase.changes.failedWithoutRollback._003__execution_with_exception.class, Collections.singletonList(Collection.class))
            )
            .WHEN(() -> {
                assertThrows(OperationException.class, () -> {
                    testKit.createBuilder()
                        .setAuditStore(couchbaseAuditStore)
                        .addTargetSystem(couchbaseTargetSystem)
                        .addDependency(testCollection) // for test purpose only
                        .build()
                        .run();
                });
            })
            .THEN_VerifyAuditSequenceStrict(
                STARTED(expectedTaskIds[0]),
                APPLIED(expectedTaskIds[0]),
                STARTED(expectedTaskIds[1]),
                APPLIED(expectedTaskIds[1]),
                STARTED(expectedTaskIds[2]),
                FAILED(expectedTaskIds[2]),
                ROLLED_BACK(expectedTaskIds[2])
            )
            .run();

        //Checking created index and documents
        assertTrue(CouchbaseCollectionHelper.indexExists(cluster, testCollection.bucketName(), testCollection.scopeName(), testCollection.name(), "idx_standalone_index"));
        JsonObject jsonObject;
        jsonObject = testCollection.get("test-client-Federico").contentAsObject();
        assertNotNull(jsonObject);
        assertEquals("Federico", jsonObject.get("name"));
        assertFalse(testCollection.exists("test-client-Jorge").exists());
    }
}
