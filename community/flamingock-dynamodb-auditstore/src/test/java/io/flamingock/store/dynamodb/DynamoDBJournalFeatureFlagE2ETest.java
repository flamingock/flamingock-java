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

import io.flamingock.common.test.pipeline.CodeChangeTestDefinition;
import io.flamingock.core.kit.TestKit;
import io.flamingock.core.kit.audit.AuditEntryExpectation;
import io.flamingock.core.kit.audit.AuditTestSupport;
import io.flamingock.dynamodb.kit.DynamoDBTestKit;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.feature.Features;
import io.flamingock.internal.common.core.journal.JournalEvent;
import io.flamingock.internal.util.FeatureFlag;
import io.flamingock.internal.util.constants.CommunityPersistenceConstants;
import io.flamingock.internal.util.dynamodb.DynamoDBUtil;
import io.flamingock.internal.util.dynamodb.entities.AuditEntryEntity;
import io.flamingock.internal.util.dynamodb.entities.journal.DynamoDBJournalEventMapper;
import io.flamingock.internal.util.dynamodb.entities.journal.JournalEventEntity;
import io.flamingock.store.dynamodb.changes.audit._001__NonTxTransactionalFalseChange;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static io.flamingock.core.kit.audit.AuditEntryExpectation.APPLIED;
import static io.flamingock.core.kit.audit.AuditEntryExpectation.STARTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage for the {@link Features#JOURNAL_EVENTS} gate through a complete runner execution.
 */
@Testcontainers
class DynamoDBJournalFeatureFlagE2ETest {

    private static final String JOURNAL_TABLE = "flamingockJournalEvents";
    private static final String DEFAULT_STAGE_NAME = "default-stage-name";

    @Container
    static final GenericContainer<?> dynamoDBContainer = DynamoDBTestContainer.createContainer();

    private DynamoDbClient client;
    private TestKit testKit;

    @BeforeEach
    void setUp() {
        client = DynamoDBTestContainer.createClient(dynamoDBContainer);
        testKit = DynamoDBTestKit.create(
                client,
                DynamoDBAuditStore.from(new DynamoDBTargetSystem("dynamodb", client)));
    }

    @AfterEach
    void tearDown() {
        FeatureFlag.remove(Features.JOURNAL_EVENTS);
        testKit.cleanUp();
    }

    @Test
    @DisplayName("journal disabled: the audit log retains every state transition")
    void journalDisabledRetainsHistoricalAuditEntries() {
        runPipeline(
                STARTED("non-tx-transactional-false"),
                APPLIED("non-tx-transactional-false"));

        assertEquals(Arrays.asList(AuditEntry.Status.APPLIED.name(), AuditEntry.Status.STARTED.name()), storedAuditRecords().stream()
                .map(AuditEntryEntity::getState)
                .sorted()
                .collect(Collectors.toList()));
    }

    @Test
    @DisplayName("journal enabled: an audit-only installation transparently creates the journal")
    void journalEnabledSplitsCurrentStateFromHistory() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);

        runPipeline(APPLIED("non-tx-transactional-false"));

        assertTrue(client.listTables().tableNames().contains(CommunityPersistenceConstants.DEFAULT_AUDIT_STORE_NAME),
                "the existing audit log must remain available");
        assertTrue(client.listTables().tableNames().contains(JOURNAL_TABLE));
        List<AuditEntryEntity> auditRecords = storedAuditRecords();
        assertEquals(1, auditRecords.size(), "the audit table must retain only the current state when journal is enabled");
        assertEquals(AuditEntry.Status.APPLIED.name(), auditRecords.get(0).getState());
        List<JournalEvent<AuditEntry>> events = storedEvents();

        assertEquals(2, events.size(), "one event must be stored for each audit state transition");
        assertTrue(events.stream().allMatch(event -> DEFAULT_STAGE_NAME.equals(event.getStreamId())),
                "journal events must use the pipeline stage as their stream");
        assertEquals(Arrays.asList(1L, 2L), events.stream()
                .map(JournalEvent::getStreamSequence)
                .sorted()
                .collect(Collectors.toList()),
                "journal stream sequences must be contiguous from one");
        assertEquals(Arrays.asList(AuditEntry.Status.STARTED, AuditEntry.Status.APPLIED), events.stream()
                .map(event -> event.getData().getState())
                .sorted()
                .collect(Collectors.toList()),
                "the journal must retain both audit state transitions");
    }

    private void runPipeline(AuditEntryExpectation... expectedAudits) {
        DynamoDBTargetSystem targetSystem = new DynamoDBTargetSystem("dynamodb", client);
        AuditTestSupport.withTestKit(testKit)
                .GIVEN_Changes(new CodeChangeTestDefinition(
                        _001__NonTxTransactionalFalseChange.class,
                        Collections.singletonList(DynamoDbClient.class)))
                .WHEN(() -> testKit.createBuilder()
                        .setAuditStore(DynamoDBAuditStore.from(targetSystem))
                        .addTargetSystem(targetSystem)
                        .build()
                        .run())
                .THEN_VerifyAuditSequenceStrict(expectedAudits)
                .run();
    }

    private List<JournalEvent<AuditEntry>> storedEvents() {
        return new DynamoDBUtil(client)
                .getEnhancedClient()
                .table(JOURNAL_TABLE, TableSchema.fromBean(JournalEventEntity.class))
                .scan(ScanEnhancedRequest.builder().consistentRead(true).build())
                .items()
                .stream()
                .filter(entity -> !DynamoDBJournalEventMapper.isReservation(entity))
                .map(DynamoDBJournalEventMapper::fromEntity)
                .collect(Collectors.toList());
    }

    private List<AuditEntryEntity> storedAuditRecords() {
        return new DynamoDBUtil(client)
                .getEnhancedClient()
                .table(CommunityPersistenceConstants.DEFAULT_AUDIT_STORE_NAME, TableSchema.fromBean(AuditEntryEntity.class))
                .scan(ScanEnhancedRequest.builder().consistentRead(true).build())
                .items()
                .stream()
                .collect(Collectors.toList());
    }
}
