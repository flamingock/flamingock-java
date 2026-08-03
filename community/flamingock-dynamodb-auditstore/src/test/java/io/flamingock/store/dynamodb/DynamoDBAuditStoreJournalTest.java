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
package io.flamingock.store.dynamodb;

import io.flamingock.core.kit.audit.AuditEntryTestFactory;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.common.core.feature.Features;
import io.flamingock.internal.common.core.journal.JournalEvent;
import io.flamingock.internal.common.core.pipeline.PipelineHelper;
import io.flamingock.internal.core.configuration.community.CommunityConfiguration;
import io.flamingock.internal.core.context.SimpleContext;
import io.flamingock.internal.core.external.store.audit.community.CommunityAuditPersistence;
import io.flamingock.internal.util.FeatureFlag;
import io.flamingock.internal.util.dynamodb.DynamoDBUtil;
import io.flamingock.internal.util.dynamodb.entities.journal.DynamoDBJournalEventMapper;
import io.flamingock.internal.util.dynamodb.entities.journal.JournalEventEntity;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.targetsystem.dynamodb.DynamoDBTargetSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class DynamoDBAuditStoreJournalTest {

    private static final String STAGE_ONE = "stage-one";
    private static final String STAGE_TWO = "stage-two";

    @Container
    static final GenericContainer<?> dynamoDBContainer = DynamoDBTestContainer.createContainer();

    private DynamoDbClient client;
    private String auditTableName;
    private String lockTableName;
    private String journalTableName;

    @BeforeEach
    void setUp() {
        client = DynamoDBTestContainer.createClient(dynamoDBContainer);
        auditTableName = tableName("storeAudit");
        lockTableName = tableName("storeLock");
        journalTableName = tableName("storeJournal");
    }

    @AfterEach
    void tearDown() {
        FeatureFlag.remove(Features.JOURNAL_EVENTS);
        if (client != null) {
            client.listTables().tableNames().forEach(tableName -> client.deleteTable(
                    software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest.builder()
                            .tableName(tableName)
                            .build()));
            client.close();
        }
    }

    @Test
    @DisplayName("journal-enabled stores create independent persistence streams for each stage")
    void journalEnabledStoreCreatesPerStagePersistenceStreams() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        SimpleContext context = newContext();
        DynamoDBAuditStore auditStore = initializeStore(context);

        CommunityAuditPersistence stageOnePersistence = auditStore.getPersistenceFactory().get(STAGE_ONE);
        CommunityAuditPersistence stageTwoPersistence = auditStore.getPersistenceFactory().get(STAGE_TWO);
        stageOnePersistence.writeEntry(auditEntry("change-one"));
        stageTwoPersistence.writeEntry(auditEntry("change-two"));

        List<JournalEvent<AuditEntry>> events = storedEvents();
        assertEquals(2, events.size());
        assertTrue(events.stream().anyMatch(event -> STAGE_ONE.equals(event.getStreamId())
                && event.getStreamSequence() == 1L
                && "change-one".equals(event.getData().getChangeId())));
        assertTrue(events.stream().anyMatch(event -> STAGE_TWO.equals(event.getStreamId())
                && event.getStreamSequence() == 1L
                && "change-two".equals(event.getData().getChangeId())));
    }

    @Test
    @DisplayName("journal-disabled stores keep the legacy factory path and do not create the journal table")
    void journalDisabledStoreFactoryKeepsLegacyPath() {
        SimpleContext context = newContext();
        DynamoDBAuditStore auditStore = initializeStore(context);

        CommunityAuditPersistence persistence = auditStore.getPersistenceFactory().get(STAGE_ONE);
        persistence.writeEntry(auditEntry("legacy-change"));

        assertEquals(1, persistence.getAuditHistory().size());
        assertTrue(client.listTables().tableNames().contains(auditTableName));
        assertFalse(client.listTables().tableNames().contains(journalTableName),
                "flag OFF must not initialize the journal table through the persistence factory");
    }

    @Test
    @DisplayName("journal-enabled deprecated getPersistence remains writable on the default stream")
    void journalEnabledDeprecatedPersistenceRemainsWritable() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        SimpleContext context = newContext();
        DynamoDBAuditStore auditStore = initializeStore(context);

        CommunityAuditPersistence persistence = auditStore.getPersistence();
        persistence.writeEntry(auditEntry("deprecated-direct-write"));

        assertEquals(1, persistence.getAuditHistory().size());
        List<JournalEvent<AuditEntry>> events = storedEvents();
        assertEquals(1, events.size());
        assertEquals(PipelineHelper.LEGACY_STAGE_ID, events.get(0).getStreamId());
        assertEquals(1L, events.get(0).getStreamSequence());
    }

    @Test
    @DisplayName("journal-disabled deprecated getPersistence keeps the append-only path")
    void journalDisabledDeprecatedPersistenceKeepsAppendPath() {
        SimpleContext context = newContext();
        DynamoDBAuditStore auditStore = initializeStore(context);

        CommunityAuditPersistence persistence = auditStore.getPersistence();
        persistence.writeEntry(auditEntry("deprecated-direct-write"));

        assertEquals(1, persistence.getAuditHistory().size());
        assertFalse(client.listTables().tableNames().contains(journalTableName));
    }

    @Test
    @DisplayName("journal repository cannot reuse the audit or lock table name")
    void journalRepositoryNameMustBeUnique() {
        SimpleContext context = newContext();
        DynamoDBTargetSystem targetSystem = initializedTargetSystem(context);
        DynamoDBAuditStore auditStore = DynamoDBAuditStore.from(targetSystem)
                .withAuditRepositoryName(auditTableName)
                .withLockRepositoryName(lockTableName)
                .withJournalRepositoryName(auditTableName);

        assertThrows(FlamingockException.class, () -> auditStore.initialize(context));
    }

    private List<JournalEvent<AuditEntry>> storedEvents() {
        if (!client.listTables().tableNames().contains(journalTableName)) {
            return new ArrayList<>();
        }
        return new DynamoDBUtil(client)
                .getEnhancedClient()
                .table(journalTableName, TableSchema.fromBean(JournalEventEntity.class))
                .scan(ScanEnhancedRequest.builder().consistentRead(true).build())
                .items()
                .stream()
                .map(DynamoDBJournalEventMapper::fromEntity)
                .collect(Collectors.toList());
    }

    private DynamoDBAuditStore initializeStore(SimpleContext context) {
        DynamoDBAuditStore auditStore = DynamoDBAuditStore.from(initializedTargetSystem(context))
                .withAuditRepositoryName(auditTableName)
                .withLockRepositoryName(lockTableName)
                .withJournalRepositoryName(journalTableName);
        auditStore.initialize(context);
        return auditStore;
    }

    private DynamoDBTargetSystem initializedTargetSystem(SimpleContext context) {
        DynamoDBTargetSystem targetSystem = new DynamoDBTargetSystem("dynamodb", client);
        targetSystem.initialize(context);
        return targetSystem;
    }

    private SimpleContext newContext() {
        SimpleContext context = new SimpleContext();
        context.addDependency(RunnerId.generate());
        context.addDependency(new CommunityConfiguration());
        return context;
    }

    private static AuditEntry auditEntry(String changeId) {
        return AuditEntryTestFactory.createTestAuditEntry(
                changeId,
                AuditEntry.Status.APPLIED,
                AuditTxType.NON_TX,
                (Class<?>) null);
    }

    private static String tableName(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }
}
