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
package io.flamingock.community.couchbase;

import com.couchbase.client.core.io.CollectionIdentifier;
import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.json.JsonObject;
import io.flamingock.community.couchbase.driver.CouchbaseAuditStore;
import io.flamingock.core.processor.util.Deserializer;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.couchbase.CouchbaseCollectionHelper;
import io.flamingock.internal.core.builder.FlamingockFactory;
import io.flamingock.internal.core.community.Constants;
import io.flamingock.internal.core.runner.PipelineExecutionException;
import io.flamingock.internal.util.Trio;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.testcontainers.couchbase.BucketDefinition;
import org.testcontainers.couchbase.CouchbaseContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class CouchbaseAuditStoreTest {

    private static final String BUCKET_NAME = "test";

    private static Cluster cluster;
    private static CouchbaseTestHelper couchbaseTestHelper;

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
    }

    @AfterEach
    void tearDownEach() {
        CouchbaseCollectionHelper.deleteAllDocuments(cluster, BUCKET_NAME, CollectionIdentifier.DEFAULT_SCOPE, CollectionIdentifier.DEFAULT_COLLECTION);
        CouchbaseCollectionHelper.deleteAllDocuments(cluster, BUCKET_NAME, CollectionIdentifier.DEFAULT_SCOPE, Constants.DEFAULT_AUDIT_STORE_NAME);
        CouchbaseCollectionHelper.dropIndexIfExists(cluster, BUCKET_NAME, CollectionIdentifier.DEFAULT_SCOPE, CollectionIdentifier.DEFAULT_COLLECTION, "idx_standalone_index");
    }


    @Test
    @DisplayName("When standalone runs the driver should persist the audit logs and the test data")
    void happyPath() {
        //Given-When
        Bucket bucket = cluster.bucket(BUCKET_NAME);
        Collection testCollection = bucket.defaultCollection();

        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(PipelineTestHelper.getPreviewPipeline(
                    new Trio<>(io.flamingock.community.couchbase.changes.happyPath._001_create_index.class, Collections.singletonList(Collection.class)),
                    new Trio<>(io.flamingock.community.couchbase.changes.happyPath._002_insert_document.class, Collections.singletonList(Collection.class)),
                    new Trio<>(io.flamingock.community.couchbase.changes.happyPath._003_insert_another_document.class, Collections.singletonList(Collection.class)))
            );

            FlamingockFactory.getCommunityBuilder()
                    .setAuditStore(new CouchbaseAuditStore())
                    .setAuditStore(new CouchbaseAuditStore())
                    .addDependency(cluster)
                    .addDependency(bucket)
                    .addDependency(testCollection) // for test purpose only
                    .setRelaxTargetSystemValidation(true)
                    .build()
                    .run();
        }

        //Then
        //Checking auditLog
        Collection auditLogCollection = bucket.collection(Constants.DEFAULT_AUDIT_STORE_NAME);
        List<AuditEntry> auditLog = couchbaseTestHelper.getAuditEntriesSorted(auditLogCollection);
        assertEquals(3, auditLog.size());
        assertEquals("create-index", auditLog.get(0).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(0).getState());
        assertEquals("insert-document", auditLog.get(1).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(1).getState());
        assertEquals("insert-another-document", auditLog.get(2).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(2).getState());

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
    @DisplayName("When standalone runs the driver and execution fails (with rollback method) should persist all the audit logs up to the failed one (ROLLED_BACK)")
    void failedWithRollback() {
        //Given-When
        Bucket bucket = cluster.bucket(BUCKET_NAME);
        Collection testCollection = bucket.defaultCollection();

        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(PipelineTestHelper.getPreviewPipeline(
                    new Trio<>(io.flamingock.community.couchbase.changes.failedWithRollback._001_create_index.class, Collections.singletonList(Collection.class)),
                    new Trio<>(io.flamingock.community.couchbase.changes.failedWithRollback._002_insert_document.class, Collections.singletonList(Collection.class)),
                    new Trio<>(io.flamingock.community.couchbase.changes.failedWithRollback._003_execution_with_exception.class, Collections.singletonList(Collection.class)))
            );

            assertThrows(PipelineExecutionException.class, () -> {
                FlamingockFactory.getCommunityBuilder()
                    .setAuditStore(new CouchbaseAuditStore())
                        .addDependency(cluster)
                        .addDependency(bucket)
                        .addDependency(testCollection) // for test purpose only
                        .setRelaxTargetSystemValidation(true)
                        .build()
                        .run();
            });
        }

        //Then
        //Checking auditLog
        Collection auditLogCollection = bucket.collection(Constants.DEFAULT_AUDIT_STORE_NAME);
        List<AuditEntry> auditLog = couchbaseTestHelper.getAuditEntriesSorted(auditLogCollection);
        assertEquals(3, auditLog.size());
        assertEquals("create-index", auditLog.get(0).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(0).getState());
        assertEquals("insert-document", auditLog.get(1).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(1).getState());
        assertEquals("execution-with-exception", auditLog.get(2).getTaskId());
        assertEquals(AuditEntry.Status.ROLLED_BACK, auditLog.get(2).getState());

        //Checking created index and documents
        assertTrue(CouchbaseCollectionHelper.indexExists(cluster, testCollection.bucketName(), testCollection.scopeName(), testCollection.name(), "idx_standalone_index"));
        JsonObject jsonObject;
        jsonObject = testCollection.get("test-client-Federico").contentAsObject();
        assertNotNull(jsonObject);
        assertEquals("Federico", jsonObject.get("name"));
        assertFalse(testCollection.exists("test-client-Jorge").exists());
    }

    @Test
    @DisplayName("When standalone runs the driver and execution fails (without rollback method) should persist all the audit logs up to the failed one (FAILED)")
    void failedWithoutRollback() {
        //Given-When
        Bucket bucket = cluster.bucket(BUCKET_NAME);
        Collection testCollection = bucket.defaultCollection();

        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(PipelineTestHelper.getPreviewPipeline(
                    new Trio<>(io.flamingock.community.couchbase.changes.failedWithoutRollback._001_create_index.class, Collections.singletonList(Collection.class)),
                    new Trio<>(io.flamingock.community.couchbase.changes.failedWithoutRollback._002_insert_document.class, Collections.singletonList(Collection.class)),
                    new Trio<>(io.flamingock.community.couchbase.changes.failedWithoutRollback._003_execution_with_exception.class, Collections.singletonList(Collection.class)))
            );

            assertThrows(PipelineExecutionException.class, () -> {
                FlamingockFactory.getCommunityBuilder()
                    .setAuditStore(new CouchbaseAuditStore())
                        .addDependency(cluster)
                        .addDependency(bucket)
                        .addDependency(testCollection) // for test purpose only
                        .setRelaxTargetSystemValidation(true)
                        .build()
                        .run();
            });
        }

        //Then
        //Checking auditLog
        Collection auditLogCollection = bucket.collection(Constants.DEFAULT_AUDIT_STORE_NAME);
        List<AuditEntry> auditLog = couchbaseTestHelper.getAuditEntriesSorted(auditLogCollection);
        assertEquals(3, auditLog.size());
        assertEquals("create-index", auditLog.get(0).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(0).getState());
        assertEquals("insert-document", auditLog.get(1).getTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(1).getState());
        assertEquals("execution-with-exception", auditLog.get(2).getTaskId());
        assertEquals(AuditEntry.Status.ROLLED_BACK, auditLog.get(2).getState());

        //Checking created index and documents
        assertTrue(CouchbaseCollectionHelper.indexExists(cluster, testCollection.bucketName(), testCollection.scopeName(), testCollection.name(), "idx_standalone_index"));
        JsonObject jsonObject;
        jsonObject = testCollection.get("test-client-Federico").contentAsObject();
        assertNotNull(jsonObject);
        assertEquals("Federico", jsonObject.get("name"));
        assertFalse(testCollection.exists("test-client-Jorge").exists());
    }
}