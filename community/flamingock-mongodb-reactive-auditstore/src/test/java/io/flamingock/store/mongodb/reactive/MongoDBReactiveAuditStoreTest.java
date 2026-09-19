/*
 * Copyright 2026 Flamingock (https://www.flamingock.io)
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
package io.flamingock.store.mongodb.reactive;

import com.mongodb.reactivestreams.client.ClientSession;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoDatabase;
import io.flamingock.common.test.pipeline.CodeChangeTestDefinition;
import io.flamingock.store.mongodb.reactive.changes._001__create_client_collection_happy;
import io.flamingock.store.mongodb.reactive.changes._002__insert_federico_happy_non_transactional;
import io.flamingock.store.mongodb.reactive.changes._002__insert_federico_happy_transactional;
import io.flamingock.store.mongodb.reactive.changes._003__insert_jorge_failed_transactional_non_rollback;
import io.flamingock.store.mongodb.reactive.changes._003__insert_jorge_happy_transactional;
import io.flamingock.core.kit.TestKit;
import io.flamingock.core.kit.audit.AuditTestHelper;
import io.flamingock.core.kit.audit.AuditTestSupport;
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.common.core.feature.Features;
import io.flamingock.internal.core.builder.FlamingockEdition;
import io.flamingock.internal.core.configuration.community.CommunityConfigurable;
import io.flamingock.internal.core.configuration.community.CommunityConfiguration;
import io.flamingock.internal.core.operation.OperationException;
import io.flamingock.internal.util.FeatureFlag;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.mongodb.reactive.kit.MongoDBReactiveTestKit;
import io.flamingock.targetsystem.mongodb.reactive.MongoDBReactiveTargetSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static io.flamingock.core.kit.audit.AuditEntryExpectation.APPLIED;
import static io.flamingock.core.kit.audit.AuditEntryExpectation.FAILED;
import static io.flamingock.core.kit.audit.AuditEntryExpectation.ROLLED_BACK;
import static io.flamingock.internal.util.constants.CommunityPersistenceConstants.DEFAULT_AUDIT_STORE_NAME;
import static io.flamingock.internal.util.constants.CommunityPersistenceConstants.DEFAULT_LOCK_STORE_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Testcontainers
class MongoDBReactiveAuditStoreTest {

    private static final String DB_NAME = "test";

    private static final String CLIENTS_COLLECTION = "clientCollection";

    private static final String CUSTOM_AUDIT_REPOSITORY_NAME = "testFlamingockAudit";
    private static final String CUSTOM_LOCK_REPOSITORY_NAME = "testFlamingockLock";

    private static MongoClient mongoClient;

    private static MongoDatabase database;

    @Deprecated
    private static MongoDBTestHelper mongoDBTestHelper;


    @Container
    public static final MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:6"))
            .withReuse(true);
    private TestKit testKit;
    private AuditTestHelper auditHelper;


    @BeforeEach
    void setupEach() {
        mongoClient = MongoClients.create(mongoDBContainer.getConnectionString());
        database = mongoClient.getDatabase("test");
        testKit = MongoDBReactiveTestKit.create(MongoDBReactiveAuditStore.from(new MongoDBReactiveTargetSystem("mongodb", mongoClient, "test")), mongoClient, database);
        auditHelper = testKit.getAuditHelper();

        mongoDBTestHelper = new MongoDBTestHelper(database);

    }

    @AfterEach
    void tearDown() {
        FeatureFlag.remove(Features.JOURNAL_EVENTS);
        ReactiveMongoTestHelper.complete(database.drop()); // Clean between tests
        mongoClient.close();
    }

    @Test
    @DisplayName("When standalone runs the AuditStore with DEFAULT repository names related collections should exists")
    void happyPathWithDefaultRepositoryNames() {
        MongoDBReactiveTargetSystem mongoDBSyncTargetSystem = new MongoDBReactiveTargetSystem("mongodb", mongoClient, "test");
        //Given-When-Then
        AuditTestSupport.withTestKit(testKit)
                .GIVEN_Changes(
                        new CodeChangeTestDefinition(_001__create_client_collection_happy.class, Collections.singletonList(MongoDatabase.class)),
                        new CodeChangeTestDefinition(_002__insert_federico_happy_transactional.class, Arrays.asList(MongoDatabase.class, ClientSession.class)),
                        new CodeChangeTestDefinition(_003__insert_jorge_happy_transactional.class, Arrays.asList(MongoDatabase.class, ClientSession.class))
                )
                .WHEN(() -> testKit.createBuilder()
                        .setAuditStore(MongoDBReactiveAuditStore.from(mongoDBSyncTargetSystem))
                        .addTargetSystem(mongoDBSyncTargetSystem)
                        .build()
                        .run())
                .THEN_VerifyAuditFinalStateSequence(
                        APPLIED("create-client-collection"),
                        APPLIED("insert-federico-document"),
                        APPLIED("insert-jorge-document")
                )
                .run();

        assertTrue(mongoDBTestHelper.collectionExists(DEFAULT_AUDIT_STORE_NAME));
        assertTrue(mongoDBTestHelper.collectionExists(DEFAULT_LOCK_STORE_NAME));
        assertFalse(mongoDBTestHelper.collectionExists(CUSTOM_AUDIT_REPOSITORY_NAME));
        assertFalse(mongoDBTestHelper.collectionExists(CUSTOM_LOCK_REPOSITORY_NAME));
    }

    @Test
    @DisplayName("When standalone runs the AuditStore with transactions enabled should persist the audit logs and the user's collection updated")
    void happyPathWithTransaction() {
        MongoDBReactiveTargetSystem mongoDBSyncTargetSystem = new MongoDBReactiveTargetSystem("mongodb", mongoClient, "test");
        //Given-When-Then
        AuditTestSupport.withTestKit(testKit)
                .GIVEN_Changes(
                        new CodeChangeTestDefinition(_001__create_client_collection_happy.class, Collections.singletonList(MongoDatabase.class)),
                        new CodeChangeTestDefinition(_002__insert_federico_happy_transactional.class, Arrays.asList(MongoDatabase.class, ClientSession.class)),
                        new CodeChangeTestDefinition(_003__insert_jorge_happy_transactional.class, Arrays.asList(MongoDatabase.class, ClientSession.class))
                )
                .WHEN(() -> testKit.createBuilder()
                        .setAuditStore(MongoDBReactiveAuditStore.from(mongoDBSyncTargetSystem))
                        .addTargetSystem(mongoDBSyncTargetSystem)
                        .build()
                        .run())
                .THEN_VerifyAuditFinalStateSequence(
                        APPLIED("create-client-collection"),
                        APPLIED("insert-federico-document"),
                        APPLIED("insert-jorge-document")
                )
                .run();

        //Checking clients collection
        Set<String> clients = ReactiveMongoTestHelper.collect(database.getCollection(CLIENTS_COLLECTION)
                .find())
                .stream()
                .map(document -> document.getString("name"))
                .collect(Collectors.toCollection(HashSet::new));
        assertEquals(2, clients.size());
        assertTrue(clients.contains("Federico"));
        assertTrue(clients.contains("Jorge"));
    }

    @Test
    @DisplayName("Should use the non-transactional path for default transactional changes when target transactions are disabled")
    void defaultTransactionalChangeUsesNonTransactionalPathWhenTargetDisablesTransactions() {
        MongoDBReactiveTargetSystem targetSystem = spy(
                new MongoDBReactiveTargetSystem("mongodb", mongoClient, DB_NAME)
                        .withTransactionsSupported(false));

        AuditTestSupport.withTestKit(testKit)
                .GIVEN_Changes(
                        new CodeChangeTestDefinition(_001__create_client_collection_happy.class,
                                Collections.singletonList(MongoDatabase.class)),
                        new CodeChangeTestDefinition(_002__insert_federico_happy_non_transactional.class,
                                Collections.singletonList(MongoDatabase.class))
                )
                .WHEN(() -> testKit.createBuilder()
                        .setAuditStore(MongoDBReactiveAuditStore.from(targetSystem))
                        .addTargetSystem(targetSystem)
                        .build()
                        .run())
                .THEN_VerifyAuditFinalStateSequence(
                        APPLIED("create-client-collection"),
                        APPLIED("insert-federico-document")
                )
                .run();

        verify(targetSystem, never()).getTxWrapper();
        assertEquals(1L, ReactiveMongoTestHelper.first(
                database.getCollection(CLIENTS_COLLECTION).countDocuments()));
    }

    @Test
    @DisplayName("Should initialize Journal Events without a transaction wrapper when target transactions are disabled")
    void journalEnabledWithDisabledTransactionsInitializesWithoutTransactionWrapper() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        MongoDBReactiveTargetSystem targetSystem = spy(
                new MongoDBReactiveTargetSystem("mongodb", mongoClient, DB_NAME)
                        .withTransactionsSupported(false));
        ContextResolver context = mock(ContextResolver.class);
        when(context.getDependencyValue(FlamingockEdition.class))
                .thenReturn(Optional.of(FlamingockEdition.COMMUNITY));
        when(context.getRequiredDependencyValue(RunnerId.class)).thenReturn(RunnerId.generate());
        when(context.getRequiredDependencyValue(CommunityConfigurable.class))
                .thenReturn(new CommunityConfiguration());
        targetSystem.initialize(context);

        MongoDBReactiveAuditStore auditStore = MongoDBReactiveAuditStore.from(targetSystem);
        auditStore.initialize(context);
        auditStore.getPersistenceFactory().get("stage");

        verify(targetSystem, never()).getTxWrapper();
    }

    @Test
    @DisplayName("When standalone runs the AuditStore with transactions enabled and execution fails should persist only the applied audit logs")
    void failedWithTransaction() {
        MongoDBReactiveTargetSystem mongoDBSyncTargetSystem = new MongoDBReactiveTargetSystem("mongodb", mongoClient, "test");
        //Given-When-Then
        AuditTestSupport.withTestKit(testKit)
                .GIVEN_Changes(
                        new CodeChangeTestDefinition(_001__create_client_collection_happy.class, Collections.singletonList(MongoDatabase.class)),
                        new CodeChangeTestDefinition(_002__insert_federico_happy_non_transactional.class, Collections.singletonList(MongoDatabase.class)),
                        new CodeChangeTestDefinition(_003__insert_jorge_failed_transactional_non_rollback.class, Arrays.asList(MongoDatabase.class, ClientSession.class))
                )
                .WHEN(() -> assertThrows(OperationException.class, () -> {
                    testKit.createBuilder()
                            .setAuditStore(MongoDBReactiveAuditStore.from(mongoDBSyncTargetSystem))
                            .addTargetSystem(mongoDBSyncTargetSystem)
                            .build()
                            .run();
                }))
                .THEN_VerifyAuditFinalStateSequence(
                        APPLIED("create-client-collection"),
                        APPLIED("insert-federico-document"),
                        FAILED("insert-jorge-document"),
                        ROLLED_BACK("insert-jorge-document")
                )
                .run();

        //Checking clients collection
        Set<String> clients = ReactiveMongoTestHelper.collect(database.getCollection(CLIENTS_COLLECTION)
                .find())
                .stream()
                .map(document -> document.getString("name"))
                .collect(Collectors.toCollection(HashSet::new));
        assertEquals(1, clients.size());
        assertTrue(clients.contains("Federico"));
    }


}
