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
package io.flamingock.community.dynamodb;

import io.flamingock.community.dynamodb.changes.audit.*;
import io.flamingock.community.dynamodb.driver.DynamoDBAuditStore;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import io.flamingock.common.test.pipeline.CodeChangeUnitTestDefinition;
import io.flamingock.core.kit.audit.AuditTestHelper;
import io.flamingock.dynamodb.kit.DynamoDBTestKit;
import io.flamingock.core.kit.audit.AuditTestSupport;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.internal.core.builder.FlamingockFactory;
import io.flamingock.internal.core.targets.DefaultTargetSystem;
import io.flamingock.targetsystem.dynamodb.DynamoDBTargetSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static io.flamingock.core.kit.audit.AuditEntryExpectation.APPLIED;
import static io.flamingock.core.kit.audit.AuditEntryExpectation.STARTED;
import static io.flamingock.core.kit.audit.AuditEntryExpectation.auditEntry;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@Testcontainers
class DynamoDBAuditPersistenceE2ETest {

    private static final Logger logger = LoggerFactory.getLogger(DynamoDBAuditPersistenceE2ETest.class);

    @Container
    static final GenericContainer<?> dynamoDBContainer = DynamoDBTestContainer.createContainer();

    private DynamoDBTestKit testKit;
    private DynamoDbClient sharedDynamoDbClient;
    private DynamoDbClient separateDynamoDbClient;
    private AuditTestHelper auditTestHelper;

    @BeforeEach
    void setUp() {
        logger.info("Setting up DynamoDB clients for container...");
        
        // Create shared DynamoDbClient for audit storage and TX_SHARED scenarios
        sharedDynamoDbClient = DynamoDBTestContainer.createClient(dynamoDBContainer);
        
        // Create separate DynamoDbClient for TX_SEPARATE_NO_MARKER scenarios
        // Note: In TestContainers, both clients connect to the same container instance
        separateDynamoDbClient = DynamoDBTestContainer.createClient(dynamoDBContainer);

        
        // Initialize test kit with DynamoDB persistence using the same client as the driver
        testKit = DynamoDBTestKit.create(sharedDynamoDbClient, new DynamoDBAuditStore(sharedDynamoDbClient));
        auditTestHelper = testKit.getAuditHelper();

    }

    @AfterEach
    void tearDown() {
        testKit.cleanUp();
    }

    @Test
    @DisplayName("Should persist all audit entry fields correctly in DynamoDB")
    void testCompleteAuditEntryPersistenceInDynamoDB() {
        // Given
        String changeId = "non-tx-transactional-false";
        LocalDateTime testStart = LocalDateTime.now();
        LocalDateTime testEnd = testStart.plusMinutes(5); // Allow enough time for test execution

        // When-Then - Complete audit verification within AuditTestSupport framework
        AuditTestSupport.withTestKit(testKit)
                
                .GIVEN_ChangeUnits(
                        new CodeChangeUnitTestDefinition(NonTxTransactionalFalseChange.class, Collections.singletonList(DynamoDbClient.class))
                )
                .WHEN(() -> {
                    assertDoesNotThrow(() -> {
                        FlamingockFactory.getCommunityBuilder()
                                .setAuditStore(new DynamoDBAuditStore(sharedDynamoDbClient))
                                .addTargetSystem(new DynamoDBTargetSystem("dynamodb", sharedDynamoDbClient))
                                .build()
                                .run();
                    });
                })
                .THEN_VerifyAuditSequenceStrict(
                        auditEntry()
                                .withTaskId(changeId)
                                .withState(AuditEntry.Status.STARTED)
                                .withType(AuditEntry.ExecutionType.EXECUTION)
                                .withClassName("io.flamingock.community.dynamodb.changes.audit.NonTxTransactionalFalseChange")
                                .withMethodName("execution")
                                .withTxType(AuditTxType.NON_TX)
                                .withTargetSystemId("dynamodb")
                                .withSystemChange(false),
                        auditEntry()
                                .withTaskId(changeId)
                                .withState(AuditEntry.Status.APPLIED)
                                .withType(AuditEntry.ExecutionType.EXECUTION)
                                .withClassName("io.flamingock.community.dynamodb.changes.audit.NonTxTransactionalFalseChange")
                                .withMethodName("execution")
                                .withTxType(AuditTxType.NON_TX)
                                .withTargetSystemId("dynamodb")
                                .withSystemChange(false)
                )
                .run();
    }

    @Test
    @DisplayName("Should persist NON_TX txType for transactional=false scenarios")
    void testNonTxScenarios() {
        // Given-When-Then - Test NON_TX scenarios
        AuditTestSupport.withTestKit(testKit)
                
                .GIVEN_ChangeUnits(
                        new CodeChangeUnitTestDefinition(NonTxTransactionalFalseChange.class, Collections.singletonList(DynamoDbClient.class)),
                        new CodeChangeUnitTestDefinition(NonTxTargetSystemChange.class, Collections.singletonList(DynamoDbClient.class))
                )
                .WHEN(() -> {
                    assertDoesNotThrow(() -> {
                        FlamingockFactory.getCommunityBuilder()
                                .setAuditStore(new DynamoDBAuditStore(sharedDynamoDbClient))
                                .addTargetSystem(new DynamoDBTargetSystem("dynamodb", sharedDynamoDbClient))
                                .addTargetSystem(new DefaultTargetSystem("non-tx-system")) // Non-transactional target system
                                .addDependency(sharedDynamoDbClient)
                                .build()
                                .run();
                    });
                })
                .THEN_VerifyAuditSequenceStrict(
                        // First change (NonTxTransactionalFalseChange) - STARTED & EXECUTED
                        STARTED("non-tx-transactional-false")
                                .withClassName("io.flamingock.community.dynamodb.changes.audit.NonTxTransactionalFalseChange")
                                .withMethodName("execution")
                                .withTxType(AuditTxType.NON_TX)
                                .withTargetSystemId("dynamodb"),
                        APPLIED("non-tx-transactional-false")
                                .withClassName("io.flamingock.community.dynamodb.changes.audit.NonTxTransactionalFalseChange")
                                .withMethodName("execution")
                                .withTxType(AuditTxType.NON_TX)
                                .withTargetSystemId("dynamodb"),

                        // Second change (NonTxTargetSystemChange) - STARTED & EXECUTED
                        STARTED("non-tx-target-system")
                                .withClassName("io.flamingock.community.dynamodb.changes.audit.NonTxTargetSystemChange")
                                .withMethodName("execution")
                                .withTxType(AuditTxType.NON_TX)
                                .withTargetSystemId("non-tx-system"),
                        APPLIED("non-tx-target-system")
                                .withClassName("io.flamingock.community.dynamodb.changes.audit.NonTxTargetSystemChange")
                                .withMethodName("execution")
                                .withTxType(AuditTxType.NON_TX)
                                .withTargetSystemId("non-tx-system")
                )
                .run();
    }

    @Test
    @DisplayName("Should persist NON_TX txType for transactional=false scenarios without dependency injection")
    void testNonTxScenariosWithoutDependencies() {
        // Given-When-Then - Test NON_TX scenarios
        AuditTestSupport.withTestKit(testKit)

                .GIVEN_ChangeUnits(
                        new CodeChangeUnitTestDefinition(NonTxTransactionalFalseChange.class, Collections.singletonList(DynamoDbClient.class)),
                        new CodeChangeUnitTestDefinition(NonTxTargetSystemChangeNoDependencies.class, Collections.singletonList(DynamoDbClient.class))
                )
                .WHEN(() -> {
                    assertDoesNotThrow(() -> {
                        FlamingockFactory.getCommunityBuilder()
                                .setAuditStore(new DynamoDBAuditStore(sharedDynamoDbClient))
                                .addTargetSystem(new DynamoDBTargetSystem("dynamodb", sharedDynamoDbClient))
                                .addTargetSystem(new DefaultTargetSystem("non-tx-system")) // Non-transactional target system
                                .build()
                                .run();
                    });
                })
                .THEN_VerifyAuditSequenceStrict(
                        // First change (NonTxTransactionalFalseChange) - STARTED & EXECUTED
                        STARTED("non-tx-transactional-false")
                                .withClassName("io.flamingock.community.dynamodb.changes.audit.NonTxTransactionalFalseChange")
                                .withMethodName("execution")
                                .withTxType(AuditTxType.NON_TX)
                                .withTargetSystemId("dynamodb"),
                        APPLIED("non-tx-transactional-false")
                                .withClassName("io.flamingock.community.dynamodb.changes.audit.NonTxTransactionalFalseChange")
                                .withMethodName("execution")
                                .withTxType(AuditTxType.NON_TX)
                                .withTargetSystemId("dynamodb"),

                        // Second change (NonTxTargetSystemChange) - STARTED & EXECUTED
                        STARTED("non-tx-target-system")
                                .withClassName("io.flamingock.community.dynamodb.changes.audit.NonTxTargetSystemChangeNoDependencies")
                                .withMethodName("execution")
                                .withTxType(AuditTxType.NON_TX)
                                .withTargetSystemId("non-tx-system"),
                        APPLIED("non-tx-target-system")
                                .withClassName("io.flamingock.community.dynamodb.changes.audit.NonTxTargetSystemChangeNoDependencies")
                                .withMethodName("execution")
                                .withTxType(AuditTxType.NON_TX)
                                .withTargetSystemId("non-tx-system")
                )
                .run();
    }

    @Test
    @DisplayName("Should persist TX_SHARED txType for default and explicit same DynamoDbClient scenarios")
    void testTxSharedScenarios() {
        // Given-When-Then - Test TX_SHARED scenarios  
        AuditTestSupport.withTestKit(testKit)
                
                .GIVEN_ChangeUnits(
                        new CodeChangeUnitTestDefinition(TxSharedDefaultChange.class, Arrays.asList(DynamoDbClient.class, TransactWriteItemsEnhancedRequest.Builder.class))
                )
                .WHEN(() -> {
                    assertDoesNotThrow(() -> {
                        DynamoDBTargetSystem sharedTargetSystem = new DynamoDBTargetSystem("tx-shared-system", sharedDynamoDbClient);

                        FlamingockFactory.getCommunityBuilder()
                                .setAuditStore(new DynamoDBAuditStore(sharedDynamoDbClient))
                                .addTargetSystem(new DynamoDBTargetSystem("dynamodb", sharedDynamoDbClient))
                                .addTargetSystem(sharedTargetSystem)
                                .build()
                                .run();
                    });
                })
                .THEN_VerifyAuditSequenceStrict(
                        STARTED("tx-shared-default")
                                .withClassName("io.flamingock.community.dynamodb.changes.audit.TxSharedDefaultChange")
                                .withMethodName("execution")
                                .withTxType(AuditTxType.TX_SEPARATE_NO_MARKER)
                                .withTargetSystemId("dynamodb"),
                        APPLIED("tx-shared-default")
                                .withClassName("io.flamingock.community.dynamodb.changes.audit.TxSharedDefaultChange")
                                .withMethodName("execution")
                                .withTxType(AuditTxType.TX_SEPARATE_NO_MARKER)
                                .withTargetSystemId("dynamodb")
                )
                .run();

    }

    @Test
    @DisplayName("Should persist TX_SEPARATE_NO_MARKER when targetSystem defined and different from auditStore")
    void testTxNoMarkerWhenSameMongoClientButDifferentTargetSystem() {
        // Given-When-Then - Test TX_SEPARATE_NO_MARKER scenarios with AuditTestSupport
        AuditTestSupport.withTestKit(testKit)
                
                .GIVEN_ChangeUnits(
                        new CodeChangeUnitTestDefinition(TxSeparateAndSameMongoClientChange.class, Arrays.asList(DynamoDbClient.class, TransactWriteItemsEnhancedRequest.Builder.class))
                )
                .WHEN(() -> {
                    assertDoesNotThrow(() -> {
                        FlamingockFactory.getCommunityBuilder()
                                .setAuditStore(new DynamoDBAuditStore(sharedDynamoDbClient))
                                .addTargetSystem(new DynamoDBTargetSystem("dynamodb", sharedDynamoDbClient))
                                .addTargetSystem(new DynamoDBTargetSystem("mongo-system", separateDynamoDbClient))
                                .build()
                                .run();
                    });
                })
                .THEN_VerifyAuditSequenceStrict(
                        STARTED("tx-separate-no-marker")
                                .withClassName("io.flamingock.community.dynamodb.changes.audit.TxSeparateAndSameMongoClientChange")
                                .withMethodName("execution")
                                .withTxType(AuditTxType.TX_SEPARATE_NO_MARKER)
                                .withTargetSystemId("mongo-system"),
                        APPLIED("tx-separate-no-marker")
                                .withClassName("io.flamingock.community.dynamodb.changes.audit.TxSeparateAndSameMongoClientChange")
                                .withMethodName("execution")
                                .withTxType(AuditTxType.TX_SEPARATE_NO_MARKER)
                                .withTargetSystemId("mongo-system")
                )
                .run();
    }


    @Test
    @DisplayName("Should persist TX_SEPARATE_NO_MARKER txType for different DynamoDbClient scenario")
    void testTxSeparateNoMarkerScenario() {
        DynamoDBTargetSystem separateTargetSystem = new DynamoDBTargetSystem("mongo-system", separateDynamoDbClient); // Different DynamoDbClient from audit storage

        // Given-When-Then - Test TX_SEPARATE_NO_MARKER scenarios with AuditTestSupport
        AuditTestSupport.withTestKit(testKit)
                
                .GIVEN_ChangeUnits(
                        new CodeChangeUnitTestDefinition(TxSeparateAndSameMongoClientChange.class, Arrays.asList(DynamoDbClient.class, TransactWriteItemsEnhancedRequest.Builder.class))
                )
                .WHEN(() -> {
                    assertDoesNotThrow(() -> {
                        FlamingockFactory.getCommunityBuilder()
                                .setAuditStore(new DynamoDBAuditStore(sharedDynamoDbClient))
                                .addTargetSystem(new DynamoDBTargetSystem("dynamodb", sharedDynamoDbClient))
                                .addTargetSystem(separateTargetSystem)
                                .build()
                                .run();
                    });
                })
                .THEN_VerifyAuditSequenceStrict(
                        STARTED("tx-separate-no-marker")
                                .withClassName("io.flamingock.community.dynamodb.changes.audit.TxSeparateAndSameMongoClientChange")
                                .withMethodName("execution")
                                .withTxType(AuditTxType.TX_SEPARATE_NO_MARKER)
                                .withTargetSystemId("mongo-system"),
                        APPLIED("tx-separate-no-marker")
                                .withClassName("io.flamingock.community.dynamodb.changes.audit.TxSeparateAndSameMongoClientChange")
                                .withMethodName("execution")
                                .withTxType(AuditTxType.TX_SEPARATE_NO_MARKER)
                                .withTargetSystemId("mongo-system")
                )
                .run();
    }

    @Test
    @DisplayName("Should persist correct targetSystemId for different target system configurations")
    void testTargetSystemIdVariations() {
        // Given-When-Then - Test multiple target system configurations with AuditTestSupport
        AuditTestSupport.withTestKit(testKit)
                
                .GIVEN_ChangeUnits(
                        new CodeChangeUnitTestDefinition(TxSharedDefaultChange.class, Arrays.asList(DynamoDbClient.class, TransactWriteItemsEnhancedRequest.Builder.class)),
                        new CodeChangeUnitTestDefinition(NonTxTargetSystemChange.class, Collections.singletonList(DynamoDbClient.class)),
                        new CodeChangeUnitTestDefinition(TxSeparateAndSameMongoClientChange.class, Arrays.asList(DynamoDbClient.class, TransactWriteItemsEnhancedRequest.Builder.class))
                )
                .WHEN(() -> {
                    assertDoesNotThrow(() -> {
                        FlamingockFactory.getCommunityBuilder()
                                .setAuditStore(new DynamoDBAuditStore(sharedDynamoDbClient))
                                .addTargetSystem(new DynamoDBTargetSystem("dynamodb", sharedDynamoDbClient))
                                .addTargetSystem(new DefaultTargetSystem("non-tx-system"))
                                .addTargetSystem(new DynamoDBTargetSystem("mongo-system", separateDynamoDbClient))
                                .addDependency(sharedDynamoDbClient)
                                .build()
                                .run();
                    });
                })
                .THEN_VerifyAuditSequenceStrict(
                        // TxSharedDefaultChange - STARTED & EXECUTED with default target system
                        STARTED("tx-shared-default").withTargetSystemId("dynamodb"),
                        APPLIED("tx-shared-default").withTargetSystemId("dynamodb"),

                        // NonTxTargetSystemChange - STARTED & EXECUTED with custom target system
                        STARTED("non-tx-target-system").withTargetSystemId("non-tx-system"),
                        APPLIED("non-tx-target-system").withTargetSystemId("non-tx-system"),

                        // TxSeparateChange - STARTED & EXECUTED with separate target system
                        STARTED("tx-separate-no-marker").withTargetSystemId("mongo-system"),
                        APPLIED("tx-separate-no-marker").withTargetSystemId("mongo-system")
                )
                .run();
    }


    @Test
    @DisplayName("Should persist multiple changes with different txType configurations correctly")
    void testMultipleChangesWithDifferentConfigurations() {
        // Given-When-Then - Test comprehensive txType scenarios with AuditTestSupport
        AuditTestSupport.withTestKit(testKit)
                
                .GIVEN_ChangeUnits(
                        new CodeChangeUnitTestDefinition(NonTxTransactionalFalseChange.class, Collections.singletonList(DynamoDbClient.class)),
                        new CodeChangeUnitTestDefinition(TxSharedDefaultChange.class, Arrays.asList(DynamoDbClient.class, TransactWriteItemsEnhancedRequest.Builder.class)),
                        new CodeChangeUnitTestDefinition(TxSeparateAndSameMongoClientChange.class, Arrays.asList(DynamoDbClient.class, TransactWriteItemsEnhancedRequest.Builder.class))
                )
                .WHEN(() -> {
                    assertDoesNotThrow(() -> {
                        FlamingockFactory.getCommunityBuilder()
                                .setAuditStore(new DynamoDBAuditStore(sharedDynamoDbClient))
                                .addTargetSystem(new DynamoDBTargetSystem("dynamodb", sharedDynamoDbClient))
                                .addTargetSystem(new DynamoDBTargetSystem("mongo-system",separateDynamoDbClient))
                                .build()
                                .run();
                    });
                })
                .THEN_VerifyAuditSequenceStrict(
                        // NonTxTransactionalFalseChange - STARTED & EXECUTED
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
