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
package io.flamingock.targetsystem.dynamodb;

import io.flamingock.dynamodb.kit.DynamoDBTestContainer;
import io.flamingock.internal.common.core.targets.TargetSystemAuditMarkType;
import io.flamingock.internal.core.external.targets.mark.TargetSystemAuditMark;
import io.flamingock.internal.core.transaction.TransactionManager;
import io.flamingock.internal.util.dynamodb.DynamoDBUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Direct write-verification for {@link DynamoDBTargetSystemAuditMarker}. The existing
 * {@code DynamoDBCloudTargetSystemTest} only asserts the end-state marker count is zero, which is
 * also satisfied when {@code mark()} is a no-op. This test exercises {@code mark()},
 * {@code listAll()} and {@code clearMark()} through their production code paths, proving the
 * write actually persists.
 *
 * <p>DynamoDB's marker {@code mark()} adds a {@code PutItem} to a
 * {@link TransactWriteItemsEnhancedRequest.Builder} obtained from the {@link TransactionManager}.
 * The test orchestrates the surrounding transaction itself: register a fresh builder under the
 * changeId, invoke the marker, then execute the transaction via the enhanced client so the
 * {@code PutItem} actually lands.
 */
@Testcontainers
public class DynamoDBTargetSystemAuditMarkerTest {

    private static final String MARKER_TABLE = "flamingockAuditMarkerTest";

    @Container
    static GenericContainer<?> dynamoContainer = DynamoDBTestContainer.createContainer();

    private DynamoDbClient client;
    private DynamoDbEnhancedClient enhancedClient;
    private TransactionManager<TransactWriteItemsEnhancedRequest.Builder> txManager;
    private DynamoDBTargetSystemAuditMarker marker;

    @BeforeEach
    void beforeEach() {
        client = DynamoDBTestContainer.createClient(dynamoContainer);
        enhancedClient = new DynamoDBUtil(client).getEnhancedClient();

        txManager = new TransactionManager<>(TransactWriteItemsEnhancedRequest::builder);

        marker = DynamoDBTargetSystemAuditMarker.builder(client, txManager)
                .setTableName(MARKER_TABLE)
                .withAutoCreate(true)
                .build();
    }

    @AfterEach
    void afterEach() {
        try {
            client.deleteTable(b -> b.tableName(MARKER_TABLE));
        } catch (Exception ignored) {
            // table may not exist if build failed
        }
        client.close();
    }

    @Test
    @DisplayName("mark() persists each mark and listAll() returns them with the right contents")
    void markPersistsAndIsReadableViaListAll() {
        String changeId1 = "change-1";
        String changeId2 = "change-2";

        markInTransaction(changeId1, TargetSystemAuditMarkType.APPLIED);
        markInTransaction(changeId2, TargetSystemAuditMarkType.ROLLED_BACK);

        Set<TargetSystemAuditMark> marks = marker.listAll();
        Assertions.assertEquals(2, marks.size());

        Map<String, TargetSystemAuditMarkType> byId = marks.stream()
                .collect(Collectors.toMap(TargetSystemAuditMark::getChangeId,
                        TargetSystemAuditMark::getOperation));
        Assertions.assertEquals(TargetSystemAuditMarkType.APPLIED, byId.get(changeId1));
        Assertions.assertEquals(TargetSystemAuditMarkType.ROLLED_BACK, byId.get(changeId2));
    }

    @Test
    @DisplayName("clearMark() removes only the targeted mark")
    void clearMarkRemovesOnlyTheTargetedMark() {
        String changeId1 = "change-1";
        String changeId2 = "change-2";
        markInTransaction(changeId1, TargetSystemAuditMarkType.APPLIED);
        markInTransaction(changeId2, TargetSystemAuditMarkType.APPLIED);

        marker.clearMark(changeId1);

        Set<TargetSystemAuditMark> marks = marker.listAll();
        Assertions.assertEquals(1, marks.size());
        Assertions.assertEquals(changeId2, marks.iterator().next().getChangeId());
    }

    /**
     * Wraps a single mark() call in a real DynamoDB transaction: open the session with a fresh
     * request builder, let the marker queue its PutItem onto it, then execute the transaction so
     * the write actually persists.
     */
    private void markInTransaction(String changeId, TargetSystemAuditMarkType operation) {
        TransactWriteItemsEnhancedRequest.Builder requestBuilder =
                txManager.startSession(changeId);
        marker.mark(new TargetSystemAuditMark(changeId, operation));
        enhancedClient.transactWriteItems(requestBuilder.build());
        txManager.closeSession(changeId);
    }
}
