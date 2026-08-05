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
package io.flamingock.importer.mongock.dynamodb;

import io.flamingock.api.annotations.EnableFlamingock;
import io.flamingock.api.annotations.Stage;
import io.flamingock.common.test.mongock.MongockChangeEntry;
import io.flamingock.common.test.mongock.MongockChangeState;
import io.flamingock.common.test.mongock.MongockChangeType;
import io.flamingock.core.kit.TestKit;
import io.flamingock.dynamodb.kit.DynamoDBTableFactory;
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
import io.flamingock.store.dynamodb.DynamoDBAuditStore;
import io.flamingock.support.mongock.annotations.MongockSupport;
import io.flamingock.targetsystem.dynamodb.DynamoDBTargetSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static io.flamingock.internal.common.core.metadata.Constants.DEFAULT_MONGOCK_ORIGIN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@MongockSupport(targetSystem = "dynamodb-target-system")
@EnableFlamingock(stages = {@Stage(location = "io.flamingock.importer.mongock.dynamodb.changes")})
class DynamoDBImporterJournalTest {

    private static final String LEGACY_STAGE_ID = "flamingock-legacy-stage";
    private static final String IMPORTED_BEFORE_CHANGE_ID = "create-users-table_before";
    private static final String IMPORTED_CHANGE_ID = "create-users-table";
    private static final Instant HISTORICAL_TIMESTAMP = Instant.parse("2025-06-19T05:43:57.132Z");

    @Container
    static final GenericContainer<?> dynamoDBContainer = new GenericContainer<>("amazon/dynamodb-local:latest")
            .withExposedPorts(8000);

    private DynamoDbClient client;
    private DynamoDBTargetSystem targetSystem;
    private DynamoDBMongockTestHelper mongockTestHelper;
    private TestKit testKit;

    @BeforeEach
    void setUp() {
        String endpoint = String.format("http://%s:%d",
                dynamoDBContainer.getHost(),
                dynamoDBContainer.getMappedPort(8000));
        client = DynamoDbClient.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.US_EAST_1)
                .httpClient(UrlConnectionHttpClient.builder().build())
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("dummy", "dummy")))
                .build();

        DynamoDBTableFactory.createMongockTable(client, DEFAULT_MONGOCK_ORIGIN);
        mongockTestHelper = new DynamoDBMongockTestHelper(client, DEFAULT_MONGOCK_ORIGIN);
        targetSystem = new DynamoDBTargetSystem("dynamodb-target-system", client);
        testKit = DynamoDBTestKit.create(
                client,
                DynamoDBAuditStore.from(targetSystem));
    }

    @AfterEach
    void tearDown() {
        FeatureFlag.remove(Features.JOURNAL_EVENTS);
        mongockTestHelper.reset();
        testKit.cleanUp();
        client.close();
    }

    @Test
    @DisplayName("journal-enabled imports retain current audit records and publish full payloads on the legacy stream")
    void journalEnabledImportKeepsCurrentAuditRecordsAndUsesLegacyStream() {
        FeatureFlag.enable(Features.JOURNAL_EVENTS);
        mongockTestHelper.write(new MongockChangeEntry(
                "legacy-execution-before",
                IMPORTED_BEFORE_CHANGE_ID,
                "mongock",
                Date.from(Instant.parse("2025-06-19T05:43:57.094Z")),
                MongockChangeState.EXECUTED,
                MongockChangeType.BEFORE_EXECUTION,
                "io.mongock.examples.mongodb.standalone.mondogb.sync.migration.initializer.ClientInitializerChangeUnit",
                "beforeExecution",
                "legacy-before-metadata",
                25L,
                "legacy-host",
                null,
                false,
                null));
        mongockTestHelper.write(new MongockChangeEntry(
                "legacy-execution",
                IMPORTED_CHANGE_ID,
                "mongock",
                Date.from(HISTORICAL_TIMESTAMP),
                MongockChangeState.EXECUTED,
                MongockChangeType.EXECUTION,
                "io.mongock.examples.mongodb.standalone.mondogb.sync.migration.initializer.ClientInitializerChangeUnit",
                "apply",
                "legacy-metadata",
                23L,
                "legacy-host",
                null,
                false,
                null));

        Instant runStartedAt = Instant.now();
        testKit.createBuilder()
                .addTargetSystem(targetSystem)
                .build()
                .run();
        Instant runFinishedAt = Instant.now();

        List<AuditEntryEntity> importedAuditRecords = storedAuditRecords().stream()
                .filter(entry -> Arrays.asList(IMPORTED_BEFORE_CHANGE_ID, IMPORTED_CHANGE_ID)
                        .contains(entry.getChangeId()))
                .collect(Collectors.toList());
        assertEquals(2, importedAuditRecords.size());
        assertTrue(importedAuditRecords.stream().anyMatch(entry ->
                IMPORTED_BEFORE_CHANGE_ID.equals(entry.getPartitionKey())));
        assertTrue(importedAuditRecords.stream().anyMatch(entry ->
                IMPORTED_CHANGE_ID.equals(entry.getPartitionKey())),
                "flag ON must use the changeId-only current-state key for imported audit entries");

        List<JournalEvent<AuditEntry>> importedEvents = storedEvents().stream()
                .filter(event -> Arrays.asList(IMPORTED_BEFORE_CHANGE_ID, IMPORTED_CHANGE_ID)
                        .contains(event.getData().getChangeId()))
                .collect(Collectors.toList());
        assertEquals(2, importedEvents.size());
        assertTrue(importedEvents.stream().allMatch(event -> LEGACY_STAGE_ID.equals(event.getStreamId())));
        assertTrue(importedEvents.stream().allMatch(event ->
                !event.getData().getStageId().equals(event.getStreamId())));
        assertEquals(Arrays.asList(1L, 2L), importedEvents.stream()
                .map(JournalEvent::getStreamSequence)
                .sorted()
                .collect(Collectors.toList()));

        JournalEvent<AuditEntry> importedEvent = importedEvents.stream()
                .filter(event -> IMPORTED_CHANGE_ID.equals(event.getData().getChangeId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected the imported execution event"));
        AuditEntry importedEntry = importedEvent.getData();
        assertEquals(LocalDateTime.ofInstant(HISTORICAL_TIMESTAMP, ZoneId.systemDefault()),
                importedEntry.getCreatedAt());
        assertEquals("legacy-execution", importedEntry.getExecutionId());
        assertEquals("legacy-metadata", importedEntry.getMetadata());
        assertEquals(23L, importedEntry.getExecutionMillis());
        assertEquals("legacy-host", importedEntry.getExecutionHostname());
        assertTrue(!importedEvent.getOccurredAt().isBefore(runStartedAt));
        assertTrue(!importedEvent.getOccurredAt().isAfter(runFinishedAt));
    }

    private List<AuditEntryEntity> storedAuditRecords() {
        return new DynamoDBUtil(client)
                .getEnhancedClient()
                .table(CommunityPersistenceConstants.DEFAULT_AUDIT_STORE_NAME,
                        TableSchema.fromBean(AuditEntryEntity.class))
                .scan(ScanEnhancedRequest.builder().consistentRead(true).build())
                .items()
                .stream()
                .collect(Collectors.toList());
    }

    private List<JournalEvent<AuditEntry>> storedEvents() {
        return new DynamoDBUtil(client)
                .getEnhancedClient()
                .table("flamingockJournalEvents", TableSchema.fromBean(JournalEventEntity.class))
                .scan(ScanEnhancedRequest.builder().consistentRead(true).build())
                .items()
                .stream()
                .map(DynamoDBJournalEventMapper::fromEntity)
                .collect(Collectors.toList());
    }
}
