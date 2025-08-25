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
package io.flamingock.importer.dynamodb;

import io.flamingock.api.annotations.EnableFlamingock;
import io.flamingock.api.annotations.Stage;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.core.builder.FlamingockFactory;
import io.flamingock.internal.core.runner.Runner;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

import java.net.URI;
import java.time.Instant;
import java.util.*;

import static io.flamingock.api.StageType.LEGACY;
import static io.flamingock.api.StageType.SYSTEM;
import static io.flamingock.internal.core.community.Constants.DEFAULT_AUDIT_STORE_NAME;
import static org.junit.jupiter.api.Assertions.*;

@EnableFlamingock(
        stages = {
                @Stage(location = "io.flamingock.importer.dynamodb.system", type = SYSTEM),
                @Stage(location = "io.flamingock.importer.dynamodb.legacy", type = LEGACY),
                @Stage(location = "io.flamingock.importer.dynamodb.dynamodb")
        }
)
@Disabled("Investigate error. It doesn't happen in test for driver")
@Testcontainers
public class DynamoDbImporterTest {

    @Container
    public static final GenericContainer<?> dynamoDBContainer = new GenericContainer<>("amazon/dynamodb-local:latest")
            .withExposedPorts(8000);

    public static final String MONGOCK_CHANGE_LOGS = "mongockChangeLogs";


    private static DynamoDbClient client;
    private DynamoDbTestHelper mongockChangeLogsHelper;

    @BeforeAll
    static void beforeAll() {
        dynamoDBContainer.start();

        String endpoint = String.format("http://%s:%d",
                dynamoDBContainer.getHost(),
                dynamoDBContainer.getMappedPort(8000));
        client = DynamoDbClient.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.US_EAST_1)
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create("dummy", "dummy")
                        )
                )
                .build();
    }

    @BeforeEach
    void setUp() {
        mongockChangeLogsHelper = new DynamoDbTestHelper(client, MONGOCK_CHANGE_LOGS);
        mongockChangeLogsHelper.ensureTableExists();
        mongockChangeLogsHelper.resetTable();

        new DynamoDbTestHelper(client, DEFAULT_AUDIT_STORE_NAME).ensureTableExists();
        new DynamoDbTestHelper(client, DEFAULT_AUDIT_STORE_NAME).resetTable();
    }

    @Test
    void testImportDynamoDbChangeLogs() {
        List<DynamoDbChangeEntry> entries = Arrays.asList(
                new DynamoDbChangeEntry(
                        "exec-1",
                        "client-initializer",
                        "author1",
                        String.valueOf(Instant.now().toEpochMilli()),
                        "EXECUTED",
                        "EXECUTION",
                        "io.flamingock.changelog.Class1",
                        "method1",
                        new HashMap<String, String>() {{ put("meta1", "value1"); }}.toString(),
                        123L,
                        "host1",
                        null,
                        true
                ),
                new DynamoDbChangeEntry(
                        "exec-1",
                        "client-updater",
                        "author1",
                        String.valueOf(Instant.now().toEpochMilli()),
                        "EXECUTED",
                        "EXECUTION",
                        "io.flamingock.changelog.Class2",
                        "method1",
                        new HashMap<String, String>() {{ put("meta1", "value1"); }}.toString(),
                        123L,
                        "host1",
                        null,
                        true
                )
        );

        mongockChangeLogsHelper.insertChangeEntries(entries);

        Runner flamingock = FlamingockFactory.getCommunityBuilder()
                .addDependency(client)
                .setRelaxTargetSystemValidation(true)
                .build();

        flamingock.run();

        List<AuditEntry> auditLog = new DynamoDbTestHelper(client, DEFAULT_AUDIT_STORE_NAME).getAuditEntriesSorted();
        assertEquals(7, auditLog.size());

        AuditEntry entry1 = auditLog.stream()
                .filter(e -> "change-1".equals(e.getTaskId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Entry with changeId 'change-1' not found"));


        assertEquals("change-1", entry1.getTaskId());
        assertEquals("author1", entry1.getAuthor());
        assertEquals("exec-1", entry1.getExecutionId());
        assertTrue(entry1.getSystemChange());

        ScanResponse scanResponse = client.scan(
                ScanRequest.builder().tableName(DEFAULT_AUDIT_STORE_NAME).build()
        );
        assertTrue(scanResponse.count() > 0, "Audit table should not be empty");
    }

    @Test
    void failIfEmptyOrigin() {
        Runner flamingock = FlamingockFactory.getCommunityBuilder()
                .addDependency(client)
                .setRelaxTargetSystemValidation(true)
                .build();

        Assertions.assertThrows(FlamingockException.class, flamingock::run);
    }
}