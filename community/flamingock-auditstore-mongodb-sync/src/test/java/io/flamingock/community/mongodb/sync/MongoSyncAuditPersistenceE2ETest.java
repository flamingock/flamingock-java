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
package io.flamingock.community.mongodb.sync;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import io.flamingock.common.test.pipeline.CodeChangeUnitTestDefinition;
import io.flamingock.community.mongodb.sync.changes.audit.NonTxTargetSystemChange;
import io.flamingock.community.mongodb.sync.changes.audit.NonTxTransactionalFalseChange;
import io.flamingock.community.mongodb.sync.changes.audit.TxSeparateAndSameMongoClientChange;
import io.flamingock.community.mongodb.sync.changes.audit.TxSeparateChange;
import io.flamingock.community.mongodb.sync.changes.audit.TxSharedDefaultChange;
import io.flamingock.community.mongodb.sync.driver.MongoSyncAuditStore;
import io.flamingock.core.kit.TestKit;
import io.flamingock.core.kit.audit.AuditTestHelper;
import io.flamingock.core.kit.audit.AuditTestSupport;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.internal.core.targets.DefaultTargetSystem;
import io.flamingock.mongodb.kit.MongoSyncTestKit;
import io.flamingock.targetystem.mongodb.sync.MongoSyncTargetSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.Collections;

import static io.flamingock.core.kit.audit.AuditEntryExpectation.APPLIED;
import static io.flamingock.core.kit.audit.AuditEntryExpectation.STARTED;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@Testcontainers
class MongoSyncAuditPersistenceE2ETest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:4.4.6"));

    private TestKit testKit;
    private AuditTestHelper auditHelper;
    private MongoClient sharedMongoClient;
    private MongoClient separateMongoClient;
    private MongoDatabase database;

    @BeforeEach
    void setUp() {
        // Create shared MongoClient for audit storage and TX_SHARED scenarios
        sharedMongoClient = MongoClients.create(mongoDBContainer.getConnectionString());
        database = sharedMongoClient.getDatabase("test");

        // Create separate MongoClient for TX_SEPARATE_NO_MARKER scenarios
        separateMongoClient = MongoClients.create(mongoDBContainer.getConnectionString());

        // Initialize test kit with MongoDB persistence
        testKit = MongoSyncTestKit.create(new MongoSyncAuditStore(sharedMongoClient, "test"), sharedMongoClient, database);
        auditHelper = testKit.getAuditHelper();
    }

    @AfterEach
    void tearDown() {
        testKit.cleanUp();
    }

    @Test
    @DisplayName("Should persist all audit entry fields correctly in MongoDB")
    void testCompleteAuditEntryPersistenceInMongoDB() {
        // Given
        String changeId = "non-tx-transactional-false";
        LocalDateTime testStart = LocalDateTime.now();
        LocalDateTime testEnd = testStart.plusMinutes(5); // Allow enough time for test execution

        // Given-When-Then - Test MongoDB audit persistence with AuditTestSupport
        AuditTestSupport.withTestKit(testKit)
                .GIVEN_ChangeUnits(
                        new CodeChangeUnitTestDefinition(NonTxTransactionalFalseChange.class, Collections.emptyList())
                )
                .WHEN(() -> {
                    assertDoesNotThrow(() -> {
                        testKit.createBuilder()
                                .setAuditStore(new MongoSyncAuditStore(sharedMongoClient, "test"))
                                .addTargetSystem(new MongoSyncTargetSystem("mongodb")
                                        .withMongoClient(sharedMongoClient)
                                        .withDatabase(database))
                                .addDependency(sharedMongoClient)
                                .addDependency(database)
                                .build()
                                .run();
                    });
                })
                .THEN_VerifyAuditSequenceStrict(
                        STARTED(changeId)
                                .withType(AuditEntry.ExecutionType.EXECUTION)
                                .withClass(io.flamingock.community.mongodb.sync.changes.audit.NonTxTransactionalFalseChange.class)
                                .withTxType(AuditTxType.NON_TX)
                                .withTargetSystemId("mongodb")
                                .withSystemChange(false)
                                .withTimestampBetween(testStart, testEnd),
                        APPLIED(changeId)
                                .withType(AuditEntry.ExecutionType.EXECUTION)
                                .withClass(io.flamingock.community.mongodb.sync.changes.audit.NonTxTransactionalFalseChange.class)
                                .withTxType(AuditTxType.NON_TX)
                                .withTargetSystemId("mongodb")
                                .withSystemChange(false)
                                .withTimestampBetween(testStart, testEnd)
                )
                .run();
    }

    @Test
    @DisplayName("Should persist NON_TX txType for transactional=false scenarios")
    void testNonTxScenarios() {
        // Given-When-Then - Test NON_TX scenarios with AuditTestSupport
        AuditTestSupport.withTestKit(testKit)
                .GIVEN_ChangeUnits(
                        new CodeChangeUnitTestDefinition(NonTxTransactionalFalseChange.class, Collections.emptyList()),
                        new CodeChangeUnitTestDefinition(NonTxTargetSystemChange.class, Collections.emptyList())
                )
                .WHEN(() -> {
                    assertDoesNotThrow(() -> {
                        testKit.createBuilder()
                                .setAuditStore(new MongoSyncAuditStore(sharedMongoClient, "test"))
                                .addTargetSystem(new MongoSyncTargetSystem("mongodb")
                                        .withMongoClient(sharedMongoClient)
                                        .withDatabase(database))
                                .addTargetSystem(new DefaultTargetSystem("non-tx-system")) // Non-transactional target system
                                .addDependency(sharedMongoClient)
                                .addDependency(database)
                                .build()
                                .run();
                    });
                })
                .THEN_VerifyAuditSequenceStrict(
                        // First change (NonTxTransactionalFalseChange) - STARTED & EXECUTED
                        STARTED("non-tx-transactional-false")
                                .withType(AuditEntry.ExecutionType.EXECUTION)
                                .withClass(io.flamingock.community.mongodb.sync.changes.audit.NonTxTransactionalFalseChange.class)
                                .withTxType(AuditTxType.NON_TX)
                                .withTargetSystemId("mongodb"),
                        APPLIED("non-tx-transactional-false")
                                .withType(AuditEntry.ExecutionType.EXECUTION)
                                .withClass(io.flamingock.community.mongodb.sync.changes.audit.NonTxTransactionalFalseChange.class)
                                .withTxType(AuditTxType.NON_TX)
                                .withTargetSystemId("mongodb"),

                        // Second change (NonTxTargetSystemChange) - STARTED & EXECUTED
                        STARTED("non-tx-target-system")
                                .withType(AuditEntry.ExecutionType.EXECUTION)
                                .withClass(io.flamingock.community.mongodb.sync.changes.audit.NonTxTargetSystemChange.class)
                                .withTxType(AuditTxType.NON_TX)
                                .withTargetSystemId("non-tx-system"),
                        APPLIED("non-tx-target-system")
                                .withType(AuditEntry.ExecutionType.EXECUTION)
                                .withClass(io.flamingock.community.mongodb.sync.changes.audit.NonTxTargetSystemChange.class)
                                .withTxType(AuditTxType.NON_TX)
                                .withTargetSystemId("non-tx-system")
                )
                .run();
    }

    @Test
    @DisplayName("Should persist TX_SHARED txType when targetSystem not defined in changeUnit")
    void testTxSharedScenarios() {
        MongoSyncTargetSystem sharedTargetSystem = new MongoSyncTargetSystem("tx-shared-system")
                .withMongoClient(sharedMongoClient) // Same MongoClient as audit storage
                .withDatabase(database);

        // Given-When-Then - Test TX_SHARED scenarios with AuditTestSupport
        AuditTestSupport.withTestKit(testKit)
                .GIVEN_ChangeUnits(
                        new CodeChangeUnitTestDefinition(TxSharedDefaultChange.class, Collections.emptyList())
                )
                .WHEN(() -> {
                    assertDoesNotThrow(() -> {
                        testKit.createBuilder()
                                .setAuditStore(new MongoSyncAuditStore(sharedMongoClient, "test"))
                                .addTargetSystem(new MongoSyncTargetSystem("mongodb")
                                        .withMongoClient(sharedMongoClient)
                                        .withDatabase(database))
                                .addTargetSystem(sharedTargetSystem)
                                .addDependency(sharedMongoClient)
                                .addDependency(database)
                                .build()
                                .run();
                    });
                })
                .THEN_VerifyAuditSequenceStrict(
                        STARTED("tx-shared-default")
                                .withType(AuditEntry.ExecutionType.EXECUTION)
                                .withClass(io.flamingock.community.mongodb.sync.changes.audit.TxSharedDefaultChange.class)
                                .withTxType(AuditTxType.TX_SEPARATE_NO_MARKER)
                                .withTargetSystemId("mongodb"),
                        APPLIED("tx-shared-default")
                                .withType(AuditEntry.ExecutionType.EXECUTION)
                                .withClass(io.flamingock.community.mongodb.sync.changes.audit.TxSharedDefaultChange.class)
                                .withTxType(AuditTxType.TX_SEPARATE_NO_MARKER)
                                .withTargetSystemId("mongodb")
                )
                .run();
    }

    @Test
    @DisplayName("Should persist TX_SEPARATE_NO_MARKER when targetSystem defined and different from auditStore")
    void testTxNoMarkerWhenSameMongoClientButDifferentTargetSystem() {
        MongoSyncTargetSystem sharedTargetSystem = new MongoSyncTargetSystem("mongo-system")
                .withMongoClient(sharedMongoClient) // Same MongoClient as audit storage
                .withDatabase(database);

        // Given-When-Then - Test TX_SEPARATE_NO_MARKER scenarios with AuditTestSupport
        AuditTestSupport.withTestKit(testKit)
                .GIVEN_ChangeUnits(
                        new CodeChangeUnitTestDefinition(TxSeparateAndSameMongoClientChange.class, Collections.emptyList())
                )
                .WHEN(() -> {
                    assertDoesNotThrow(() -> {
                        testKit.createBuilder()
                                .setAuditStore(new MongoSyncAuditStore(sharedMongoClient, "test"))
                                .addTargetSystem(new MongoSyncTargetSystem("mongodb")
                                        .withMongoClient(sharedMongoClient)
                                        .withDatabase(database))
                                .addTargetSystem(sharedTargetSystem)
                                .addDependency(sharedMongoClient)
                                .addDependency(database)
                                .build()
                                .run();
                    });
                })
                .THEN_VerifyAuditSequenceStrict(
                        STARTED("tx-separate-no-marker")
                                .withType(AuditEntry.ExecutionType.EXECUTION)
                                .withClass(io.flamingock.community.mongodb.sync.changes.audit.TxSeparateAndSameMongoClientChange.class)
                                .withTxType(AuditTxType.TX_SEPARATE_NO_MARKER)
                                .withTargetSystemId("mongo-system"),
                        APPLIED("tx-separate-no-marker")
                                .withType(AuditEntry.ExecutionType.EXECUTION)
                                .withClass(io.flamingock.community.mongodb.sync.changes.audit.TxSeparateAndSameMongoClientChange.class)
                                .withTxType(AuditTxType.TX_SEPARATE_NO_MARKER)
                                .withTargetSystemId("mongo-system")
                )
                .run();
    }

    @Test
    @DisplayName("Should persist TX_SEPARATE_NO_MARKER txType for different MongoClient scenario")
    void testTxSeparateNoMarkerScenario() {
        MongoDatabase separateDatabase = separateMongoClient.getDatabase("test");
        MongoSyncTargetSystem separateTargetSystem = new MongoSyncTargetSystem("tx-separate-system")
                .withMongoClient(separateMongoClient) // Different MongoClient from audit storage
                .withDatabase(separateDatabase);

        // Given-When-Then - Test TX_SEPARATE_NO_MARKER scenarios with AuditTestSupport
        AuditTestSupport.withTestKit(testKit)
                .GIVEN_ChangeUnits(
                        new CodeChangeUnitTestDefinition(TxSeparateChange.class, Collections.emptyList())
                )
                .WHEN(() -> {
                    assertDoesNotThrow(() -> {
                        testKit.createBuilder()
                                .setAuditStore(new MongoSyncAuditStore(sharedMongoClient, "test"))
                                .addTargetSystem(new MongoSyncTargetSystem("mongodb")
                                        .withMongoClient(sharedMongoClient)
                                        .withDatabase(database))
                                .addTargetSystem(separateTargetSystem)
                                .addDependency(sharedMongoClient)
                                .addDependency(database)
                                .build()
                                .run();
                    });
                })
                .THEN_VerifyAuditSequenceStrict(
                        STARTED("tx-separate-no-marker")
                                .withType(AuditEntry.ExecutionType.EXECUTION)
                                .withClass(io.flamingock.community.mongodb.sync.changes.audit.TxSeparateChange.class)
                                .withTxType(AuditTxType.TX_SEPARATE_NO_MARKER)
                                .withTargetSystemId("tx-separate-system"),
                        APPLIED("tx-separate-no-marker")
                                .withType(AuditEntry.ExecutionType.EXECUTION)
                                .withClass(io.flamingock.community.mongodb.sync.changes.audit.TxSeparateChange.class)
                                .withTxType(AuditTxType.TX_SEPARATE_NO_MARKER)
                                .withTargetSystemId("tx-separate-system")
                )
                .run();
    }

    @Test
    @DisplayName("Should persist correct targetSystemId for different target system configurations")
    void testTargetSystemIdVariations() {
        MongoDatabase separateDatabase = separateMongoClient.getDatabase("test");

        // Given-When-Then - Test multiple target system configurations with AuditTestSupport
        AuditTestSupport.withTestKit(testKit)
                .GIVEN_ChangeUnits(
                        new CodeChangeUnitTestDefinition(TxSharedDefaultChange.class, Collections.emptyList()),
                        new CodeChangeUnitTestDefinition(NonTxTargetSystemChange.class, Collections.emptyList()),
                        new CodeChangeUnitTestDefinition(TxSeparateChange.class, Collections.emptyList())
                )
                .WHEN(() -> {
                    assertDoesNotThrow(() -> {
                        testKit.createBuilder()
                                .setAuditStore(new MongoSyncAuditStore(sharedMongoClient, "test"))
                                .addTargetSystem(new MongoSyncTargetSystem("mongodb")
                                        .withMongoClient(sharedMongoClient)
                                        .withDatabase(database))
                                .addTargetSystem(new DefaultTargetSystem("non-tx-system"))
                                .addTargetSystem(new MongoSyncTargetSystem("tx-separate-system")
                                        .withMongoClient(separateMongoClient)
                                        .withDatabase(separateDatabase))
                                .addDependency(sharedMongoClient)
                                .addDependency(database)
                                .build()
                                .run();
                    });
                })
                .THEN_VerifyAuditSequenceStrict(
                        // TxSharedDefaultChange - STARTED & EXECUTED with mongodb target system
                        STARTED("tx-shared-default").withTargetSystemId("mongodb"),
                        APPLIED("tx-shared-default").withTargetSystemId("mongodb"),

                        // NonTxTargetSystemChange - STARTED & EXECUTED
                        STARTED("non-tx-target-system").withTargetSystemId("non-tx-system"),
                        APPLIED("non-tx-target-system").withTargetSystemId("non-tx-system"),

                        // TxSeparateChange - STARTED & EXECUTED with separate target system
                        STARTED("tx-separate-no-marker").withTargetSystemId("tx-separate-system"),
                        APPLIED("tx-separate-no-marker").withTargetSystemId("tx-separate-system")
                )
                .run();
    }

    @Test
    @DisplayName("Should persist multiple changes with different txType configurations correctly")
    void testMultipleChangesWithDifferentConfigurations() {


        AuditTestSupport.withTestKit(testKit)
                .GIVEN_ChangeUnits(
                        new CodeChangeUnitTestDefinition(NonTxTransactionalFalseChange.class, Collections.emptyList()),
                        new CodeChangeUnitTestDefinition(TxSharedDefaultChange.class, Collections.emptyList()),
                        new CodeChangeUnitTestDefinition(TxSeparateChange.class, Collections.emptyList())
                ).WHEN(() -> assertDoesNotThrow(() -> {
                    MongoDatabase separateDatabase = separateMongoClient.getDatabase("test");
                    testKit.createBuilder()
                            .setAuditStore(new MongoSyncAuditStore(sharedMongoClient, "test"))
                            .addTargetSystem(new MongoSyncTargetSystem("mongodb")
                                    .withMongoClient(sharedMongoClient)
                                    .withDatabase(database))
                            .addTargetSystem(new MongoSyncTargetSystem("tx-separate-system")
                                    .withMongoClient(separateMongoClient)
                                    .withDatabase(separateDatabase))
                            .addDependency(sharedMongoClient)
                            .addDependency(database)
                            .build()
                            .run();
                }))
                .THEN_VerifyAuditSequenceStrict(
                        STARTED("non-tx-transactional-false").withTxType(AuditTxType.NON_TX),
                        APPLIED("non-tx-transactional-false").withTxType(AuditTxType.NON_TX),

                        // TxSharedDefaultChange - STARTED & EXECUTED
                        STARTED("tx-shared-default").withTxType(AuditTxType.TX_SEPARATE_NO_MARKER),
                        APPLIED("tx-shared-default").withTxType(AuditTxType.TX_SEPARATE_NO_MARKER),

                        // TxSeparateChange - STARTED & EXECUTED
                        STARTED("tx-separate-no-marker").withTxType(AuditTxType.TX_SEPARATE_NO_MARKER),
                        APPLIED("tx-separate-no-marker").withTxType(AuditTxType.TX_SEPARATE_NO_MARKER)
                )
                .run();


    }
}
