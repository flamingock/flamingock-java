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
package io.flamingock.store.mongodb.sync;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import io.flamingock.common.test.pipeline.CodeChangeTestDefinition;
import io.flamingock.store.mongodb.sync.changes.audit._003__NonTxTargetSystemChange;
import io.flamingock.store.mongodb.sync.changes.audit._001__NonTxTransactionalFalseChange;
import io.flamingock.store.mongodb.sync.changes.audit._004__TxSeparateAndSameMongoClientChange;
import io.flamingock.store.mongodb.sync.changes.audit._005__TxSeparateChange;
import io.flamingock.store.mongodb.sync.changes.audit._002__TxSharedDefaultChange;
import io.flamingock.core.kit.TestKit;
import io.flamingock.core.kit.audit.AuditTestHelper;
import io.flamingock.core.kit.audit.AuditTestSupport;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.targetsystem.nontransactional.NonTransactionalTargetSystem;
import io.flamingock.mongodb.kit.MongoDBSyncTestKit;
import io.flamingock.targetsystem.mongodb.sync.MongoDBSyncTargetSystem;
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
class MongoDBSyncAuditPersistenceE2ETest {

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
        testKit = MongoDBSyncTestKit.create(MongoDBSyncAuditStore.from(new MongoDBSyncTargetSystem("mongodb", sharedMongoClient, "test")), sharedMongoClient, database);
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
        MongoDBSyncTargetSystem mongoDBSyncTargetSystem = new MongoDBSyncTargetSystem("mongodb", sharedMongoClient, "test");

        // Given-When-Then - Test MongoDB audit persistence with AuditTestSupport
        AuditTestSupport.withTestKit(testKit)
                .GIVEN_Changes(
                        new CodeChangeTestDefinition(_001__NonTxTransactionalFalseChange.class, Collections.emptyList())
                )
                .WHEN(() -> {
                    assertDoesNotThrow(() -> {
                        testKit.createBuilder()
                                .setAuditStore(MongoDBSyncAuditStore.from(mongoDBSyncTargetSystem))
                                .addTargetSystem(mongoDBSyncTargetSystem)
                                .build()
                                .run();
                    });
                })
                .THEN_VerifyAuditSequenceStrict(
                        STARTED(changeId)
                                .withType(AuditEntry.ChangeType.STANDARD_CODE)
                                .withClass(_001__NonTxTransactionalFalseChange.class)
                                .withTxType(AuditTxType.NON_TX)
                                .withTargetSystemId("mongodb")
                                .withSystemChange(false)
                                .withTimestampBetween(testStart, testEnd),
                        APPLIED(changeId)
                                .withType(AuditEntry.ChangeType.STANDARD_CODE)
                                .withClass(_001__NonTxTransactionalFalseChange.class)
                                .withTxType(AuditTxType.NON_TX)
                                .withTargetSystemId("mongodb")
                                .withSystemChange(false)
                                .withTimestampBetween(testStart, testEnd)
                )
                .run();
    }

    @Test
    @DisplayName("Should persist NON_TX txStrategy for transactional=false scenarios")
    void testNonTxScenarios() {
        MongoDBSyncTargetSystem mongoDBSyncTargetSystem = new MongoDBSyncTargetSystem("mongodb", sharedMongoClient, "test");
        // Given-When-Then - Test NON_TX scenarios with AuditTestSupport
        AuditTestSupport.withTestKit(testKit)
                .GIVEN_Changes(
                        new CodeChangeTestDefinition(_001__NonTxTransactionalFalseChange.class, Collections.emptyList()),
                        new CodeChangeTestDefinition(_003__NonTxTargetSystemChange.class, Collections.emptyList())
                )
                .WHEN(() -> {
                    assertDoesNotThrow(() -> {
                        testKit.createBuilder()
                                .setAuditStore(MongoDBSyncAuditStore.from(mongoDBSyncTargetSystem))
                                .addTargetSystem(mongoDBSyncTargetSystem)
                                .addTargetSystem(new NonTransactionalTargetSystem("non-tx-system")) // Non-transactional target system
                                .build()
                                .run();
                    });
                })
                .THEN_VerifyAuditSequenceStrict(
                        // First change (NonTxTransactionalFalseChange) - STARTED & EXECUTED
                        STARTED("non-tx-transactional-false")
                                .withType(AuditEntry.ChangeType.STANDARD_CODE)
                                .withClass(_001__NonTxTransactionalFalseChange.class)
                                .withTxType(AuditTxType.NON_TX)
                                .withTargetSystemId("mongodb"),
                        APPLIED("non-tx-transactional-false")
                                .withType(AuditEntry.ChangeType.STANDARD_CODE)
                                .withClass(_001__NonTxTransactionalFalseChange.class)
                                .withTxType(AuditTxType.NON_TX)
                                .withTargetSystemId("mongodb"),

                        // Second change (NonTxTargetSystemChange) - STARTED & EXECUTED
                        STARTED("non-tx-target-system")
                                .withType(AuditEntry.ChangeType.STANDARD_CODE)
                                .withClass(_003__NonTxTargetSystemChange.class)
                                .withTxType(AuditTxType.NON_TX)
                                .withTargetSystemId("non-tx-system"),
                        APPLIED("non-tx-target-system")
                                .withType(AuditEntry.ChangeType.STANDARD_CODE)
                                .withClass(_003__NonTxTargetSystemChange.class)
                                .withTxType(AuditTxType.NON_TX)
                                .withTargetSystemId("non-tx-system")
                )
                .run();
    }

    @Test
    @DisplayName("Should persist TX_SHARED txStrategy when targetSystem not defined in change")
    void testTxSharedScenarios() {
        MongoDBSyncTargetSystem mongoDBSyncTargetSystem = new MongoDBSyncTargetSystem("mongodb", sharedMongoClient, "test");
        MongoDBSyncTargetSystem sharedTargetSystem = new MongoDBSyncTargetSystem("tx-shared-system", sharedMongoClient, "test"); // Same MongoClient as audit storage


        // Given-When-Then - Test TX_SHARED scenarios with AuditTestSupport
        AuditTestSupport.withTestKit(testKit)
                .GIVEN_Changes(
                        new CodeChangeTestDefinition(_002__TxSharedDefaultChange.class, Collections.emptyList())
                )
                .WHEN(() -> {
                    assertDoesNotThrow(() -> {
                        testKit.createBuilder()
                                .setAuditStore(MongoDBSyncAuditStore.from(mongoDBSyncTargetSystem))
                                .addTargetSystem(mongoDBSyncTargetSystem)
                                .addTargetSystem(sharedTargetSystem)
                                .build()
                                .run();
                    });
                })
                .THEN_VerifyAuditSequenceStrict(
                        STARTED("tx-shared-default")
                                .withType(AuditEntry.ChangeType.STANDARD_CODE)
                                .withClass(_002__TxSharedDefaultChange.class)
                                .withTxType(AuditTxType.TX_SEPARATE_NO_MARKER)
                                .withTargetSystemId("mongodb"),
                        APPLIED("tx-shared-default")
                                .withType(AuditEntry.ChangeType.STANDARD_CODE)
                                .withClass(_002__TxSharedDefaultChange.class)
                                .withTxType(AuditTxType.TX_SEPARATE_NO_MARKER)
                                .withTargetSystemId("mongodb")
                )
                .run();
    }

    @Test
    @DisplayName("Should persist TX_SEPARATE_NO_MARKER when targetSystem defined and different from auditStore")
    void testTxNoMarkerWhenSameMongoClientButDifferentTargetSystem() {
        MongoDBSyncTargetSystem mongoDBSyncTargetSystem = new MongoDBSyncTargetSystem("mongodb", sharedMongoClient, "test");
        MongoDBSyncTargetSystem sharedTargetSystem = new MongoDBSyncTargetSystem("mongo-system", sharedMongoClient, "test"); // Same MongoClient as audit storage

        // Given-When-Then - Test TX_SEPARATE_NO_MARKER scenarios with AuditTestSupport
        AuditTestSupport.withTestKit(testKit)
                .GIVEN_Changes(
                        new CodeChangeTestDefinition(_004__TxSeparateAndSameMongoClientChange.class, Collections.emptyList())
                )
                .WHEN(() -> {
                    assertDoesNotThrow(() -> {
                        testKit.createBuilder()
                                .setAuditStore(MongoDBSyncAuditStore.from(mongoDBSyncTargetSystem))
                                .addTargetSystem(mongoDBSyncTargetSystem)
                                .addTargetSystem(sharedTargetSystem)
                                .build()
                                .run();
                    });
                })
                .THEN_VerifyAuditSequenceStrict(
                        STARTED("tx-separate-no-marker")
                                .withType(AuditEntry.ChangeType.STANDARD_CODE)
                                .withClass(_004__TxSeparateAndSameMongoClientChange.class)
                                .withTxType(AuditTxType.TX_SEPARATE_NO_MARKER)
                                .withTargetSystemId("mongo-system"),
                        APPLIED("tx-separate-no-marker")
                                .withType(AuditEntry.ChangeType.STANDARD_CODE)
                                .withClass(_004__TxSeparateAndSameMongoClientChange.class)
                                .withTxType(AuditTxType.TX_SEPARATE_NO_MARKER)
                                .withTargetSystemId("mongo-system")
                )
                .run();
    }

    @Test
    @DisplayName("Should persist TX_SEPARATE_NO_MARKER txStrategy for different MongoClient scenario")
    void testTxSeparateNoMarkerScenario() {
        MongoDBSyncTargetSystem mongoDBSyncTargetSystem = new MongoDBSyncTargetSystem("mongodb", sharedMongoClient, "test");
        MongoDatabase separateDatabase = separateMongoClient.getDatabase("test");
        MongoDBSyncTargetSystem separateTargetSystem = new MongoDBSyncTargetSystem("tx-separate-system", separateMongoClient, "test"); // Different MongoClient from audit storage

        // Given-When-Then - Test TX_SEPARATE_NO_MARKER scenarios with AuditTestSupport
        AuditTestSupport.withTestKit(testKit)
                .GIVEN_Changes(
                        new CodeChangeTestDefinition(_005__TxSeparateChange.class, Collections.emptyList())
                )
                .WHEN(() -> {
                    assertDoesNotThrow(() -> {
                        testKit.createBuilder()
                                .setAuditStore(MongoDBSyncAuditStore.from(mongoDBSyncTargetSystem))
                                .addTargetSystem(mongoDBSyncTargetSystem)
                                .addTargetSystem(separateTargetSystem)
                                .build()
                                .run();
                    });
                })
                .THEN_VerifyAuditSequenceStrict(
                        STARTED("tx-separate-no-marker")
                                .withType(AuditEntry.ChangeType.STANDARD_CODE)
                                .withClass(_005__TxSeparateChange.class)
                                .withTxType(AuditTxType.TX_SEPARATE_NO_MARKER)
                                .withTargetSystemId("tx-separate-system"),
                        APPLIED("tx-separate-no-marker")
                                .withType(AuditEntry.ChangeType.STANDARD_CODE)
                                .withClass(_005__TxSeparateChange.class)
                                .withTxType(AuditTxType.TX_SEPARATE_NO_MARKER)
                                .withTargetSystemId("tx-separate-system")
                )
                .run();
    }

    @Test
    @DisplayName("Should persist correct targetSystemId for different target system configurations")
    void testTargetSystemIdVariations() {
        MongoDBSyncTargetSystem mongoDBSyncTargetSystem = new MongoDBSyncTargetSystem("mongodb", sharedMongoClient, "test");
        MongoDatabase separateDatabase = separateMongoClient.getDatabase("test");

        // Given-When-Then - Test multiple target system configurations with AuditTestSupport
        AuditTestSupport.withTestKit(testKit)
                .GIVEN_Changes(
                        new CodeChangeTestDefinition(_002__TxSharedDefaultChange.class, Collections.emptyList()),
                        new CodeChangeTestDefinition(_003__NonTxTargetSystemChange.class, Collections.emptyList()),
                        new CodeChangeTestDefinition(_005__TxSeparateChange.class, Collections.emptyList())
                )
                .WHEN(() -> {
                    assertDoesNotThrow(() -> {
                        testKit.createBuilder()
                                .setAuditStore(MongoDBSyncAuditStore.from(mongoDBSyncTargetSystem))
                                .addTargetSystem(mongoDBSyncTargetSystem)
                                .addTargetSystem(new NonTransactionalTargetSystem("non-tx-system"))
                                .addTargetSystem(new MongoDBSyncTargetSystem("tx-separate-system", separateMongoClient, "test"))
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
    @DisplayName("Should persist multiple changes with different txStrategy configurations correctly")
    void testMultipleChangesWithDifferentConfigurations() {
        MongoDBSyncTargetSystem mongoDBSyncTargetSystem = new MongoDBSyncTargetSystem("mongodb", sharedMongoClient, "test");


        AuditTestSupport.withTestKit(testKit)
                .GIVEN_Changes(
                        new CodeChangeTestDefinition(_001__NonTxTransactionalFalseChange.class, Collections.emptyList()),
                        new CodeChangeTestDefinition(_002__TxSharedDefaultChange.class, Collections.emptyList()),
                        new CodeChangeTestDefinition(_005__TxSeparateChange.class, Collections.emptyList())
                ).WHEN(() -> assertDoesNotThrow(() -> {
                    MongoDatabase separateDatabase = separateMongoClient.getDatabase("test");
                    testKit.createBuilder()
                            .setAuditStore(MongoDBSyncAuditStore.from(mongoDBSyncTargetSystem))
                            .addTargetSystem(mongoDBSyncTargetSystem)
                            .addTargetSystem(new MongoDBSyncTargetSystem("tx-separate-system", separateMongoClient, "test"))
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
