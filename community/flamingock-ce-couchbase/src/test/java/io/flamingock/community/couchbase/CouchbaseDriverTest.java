/*
 * Copyright 2023 Flamingock (https://oss.flamingock.io)
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

//@Testcontainers
class CouchbaseDriverTest {
//
//    private static final String BUCKET_NAME = "bucket";
//
//    private static final String CLIENTS_COLLECTION = "clientCollection";
//
//    private static Cluster cluster;
//    private static CouchbaseTestHelper couchbaseTestHelper;
//
//    @Container
//    public static final CouchbaseContainer container = new CouchbaseContainer("couchbase/server").withBucket(new BucketDefinition(BUCKET_NAME));
//
//    @BeforeAll
//    static void beforeAll() {
//        cluster = Cluster.connect(container.getConnectionString(), container.getUsername(), container.getPassword());
//        couchbaseTestHelper = new CouchbaseTestHelper(cluster);
//    }
//
//    @BeforeEach
//    void setupEach() {
//    }
//
//    @AfterEach
//    void tearDownEach() {
//        cluster.query(String.format("DELETE FROM `%s`", BUCKET_NAME), QueryOptions.queryOptions().scanConsistency(QueryScanConsistency.REQUEST_PLUS));
//        cluster.queryIndexes().dropIndex(BUCKET_NAME, "idx_standalone_index", DropQueryIndexOptions.dropQueryIndexOptions().ignoreIfNotExists(true));
//    }
//
//
//    @Test
//    @DisplayName("When standalone runs the driver should persist the audit logs and the test data")
//    void happyPath() {
//        //Given-When
//        Collection collection = cluster.bucket(BUCKET_NAME).defaultCollection();
//        FlamingockStandalone.local()
//                .setDriver(new CouchbaseDriver(cluster, collection))
//                .addStage(new Stage("stage-name").addCodePackage("io.flamingock.oss.driver.couchbase.changes.happyPath"))
//                .addDependency(cluster)
//                .addDependency(collection)
//                
//                .disableTransaction()
//                .build()
//                .run();
//
//        //Then
//        //Checking auditLog
//        List<AuditEntry> auditLog = couchbaseTestHelper.getAuditEntriesSorted(collection);
//        assertEquals(3, auditLog.size());
//        assertEquals("create-index", auditLog.get(0).getTaskId());
//        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(0).getState());
//        assertEquals("insert-document", auditLog.get(1).getTaskId());
//        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(1).getState());
//        assertEquals("insert-another-document", auditLog.get(2).getTaskId());
//        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(2).getState());
//
//        //Checking created index and documents
//        assertTrue(couchbaseTestHelper.indexExists(collection.bucketName(), "idx_standalone_index"));
//        JsonObject jsonObject;
//        jsonObject = collection.get("test-client-Federico").contentAsObject();
//        assertNotNull(jsonObject);
//        assertEquals(jsonObject.get("name"), "Federico");
//        jsonObject = collection.get("test-client-Jorge").contentAsObject();
//        assertNotNull(jsonObject);
//        assertEquals(jsonObject.get("name"), "Jorge");
//    }
//
//
//
//    @Test
//    @DisplayName("When standalone runs the driver and execution fails (with rollback method) should persist all the audit logs up to the failed one (ROLLED_BACK)")
//    void failedWithRollback() {
//        //Given-When
//        Collection collection = cluster.bucket(BUCKET_NAME).defaultCollection();
//        assertThrows(StageExecutionException.class, () -> {
//            FlamingockStandalone.local()
//                    .setDriver(new CouchbaseDriver(cluster, collection))
//                    .addStage(new Stage("stage-name").addCodePackage("io.flamingock.oss.driver.couchbase.changes.failedWithRollback"))
//                    .addDependency(cluster)
//                    .addDependency(collection)
//                    
//                    .disableTransaction()
//                    .build()
//                    .run();
//        });
//
//        //Then
//        //Checking auditLog
//        List<AuditEntry> auditLog = couchbaseTestHelper.getAuditEntriesSorted(collection);
//        assertEquals(3, auditLog.size());
//        assertEquals("create-index", auditLog.get(0).getTaskId());
//        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(0).getState());
//        assertEquals("insert-document", auditLog.get(1).getTaskId());
//        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(1).getState());
//        assertEquals("execution-with-exception", auditLog.get(2).getTaskId());
//        assertEquals(AuditEntry.Status.ROLLED_BACK, auditLog.get(2).getState());
//
//        //Checking created index and documents
//        assertTrue(couchbaseTestHelper.indexExists(collection.bucketName(), "idx_standalone_index"));
//        JsonObject jsonObject;
//        jsonObject = collection.get("test-client-Federico").contentAsObject();
//        assertNotNull(jsonObject);
//        assertEquals(jsonObject.get("name"), "Federico");
//        assertFalse(collection.exists("test-client-Jorge").exists());
//    }
//
//
//    @Test
//    @DisplayName("When standalone runs the driver and execution fails (without rollback method) should persist all the audit logs up to the failed one (FAILED)")
//    void failedWithoutRollback() {
//        //Given-When
//        Collection collection = cluster.bucket(BUCKET_NAME).defaultCollection();
//        assertThrows(StageExecutionException.class, () -> {
//            Runner build = FlamingockStandalone.local()
//                    .setDriver(new CouchbaseDriver(cluster, collection))
//                    .addStage(new Stage("stage-name").addCodePackage("io.flamingock.oss.driver.couchbase.changes.failedWithoutRollback"))
//                    .addDependency(cluster)
//                    .addDependency(collection)
//                    
//                    .disableTransaction()
//                    .build();
//            build
//                    .run();
//        });
//
//        //Then
//        //Checking auditLog
//        List<AuditEntry> auditLog = couchbaseTestHelper.getAuditEntriesSorted(collection);
//        assertEquals(3, auditLog.size());
//        assertEquals("create-index", auditLog.get(0).getTaskId());
//        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(0).getState());
//        assertEquals("insert-document", auditLog.get(1).getTaskId());
//        assertEquals(AuditEntry.Status.EXECUTED, auditLog.get(1).getState());
//        assertEquals("execution-with-exception", auditLog.get(2).getTaskId());
//        assertEquals(AuditEntry.Status.EXECUTION_FAILED, auditLog.get(2).getState());
//
//        //Checking created index and documents
//        assertTrue(couchbaseTestHelper.indexExists(collection.bucketName(), "idx_standalone_index"));
//        JsonObject jsonObject;
//        jsonObject = collection.get("test-client-Federico").contentAsObject();
//        assertNotNull(jsonObject);
//        assertEquals(jsonObject.get("name"), "Federico");
//        jsonObject = collection.get("test-client-Jorge").contentAsObject();
//        assertNotNull(jsonObject);
//        assertEquals(jsonObject.get("name"), "Jorge");
//    }
}