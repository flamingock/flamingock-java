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
package io.flamingock.store.dynamodb.internal;

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.feature.Features;
import io.flamingock.internal.common.core.journal.JournalEvent;
import io.flamingock.internal.core.journal.JournalEventStore;
import io.flamingock.internal.util.FeatureFlag;
import io.flamingock.internal.util.Result;
import io.flamingock.internal.util.dynamodb.DynamoDBUtil;
import io.flamingock.internal.util.dynamodb.entities.journal.DynamoDBJournalEventMapper;
import io.flamingock.internal.util.dynamodb.entities.journal.JournalEventEntity;
import io.flamingock.internal.util.dynamodb.entities.journal.JournalEventFieldConstants;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndexDescription;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.TableDescription;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * DynamoDB implementation of the local journal ({@code flamingockJournalEvents}).
 * <p>
 * Sibling of {@link DynamoDBAuditRepository}/{@link DynamoDBLockService}: it owns its own table and index setup.
 * <p>
 * The table has a base key of {@code (streamId, streamSequence)} with two GSIs:
 * <ul>
 *   <li>{@code PendingEventsIndex} — a sparse GSI over a constant pending partition and an order key that only
 *       contains unacknowledged events and serves the ordered unacknowledged batch query;</li>
 *   <li>{@code EventIdIndex} — a deliberately non-unique GSI over {@code eventId}. It serves only the
 *       acknowledgement lookup; appends are guarded exclusively by the
 *       {@code (streamId, streamSequence)} position guard.</li>
 * </ul>
 * Reads and acknowledgements are exposed through {@link JournalEventStore}. The append
 * ({@link #contributeToTransaction(TransactWriteItemsEnhancedRequest.Builder, JournalEvent)}) deliberately is not: it stages a
 * conditional put on the shared transaction builder so the event lands in the same transaction as the audit
 * entry it mirrors, and a transaction-request builder has no place in a core interface. Only
 * {@link DynamoDBAuditPersistence} — which owns that transaction boundary — calls it.
 */
public class DynamoDBJournalEventStore implements JournalEventStore {

    private static final Logger logger = FlamingockLoggerFactory.getLogger("DynamoDBJournal");

    private final DynamoDBUtil dynamoDBUtil;
    private final String tableName;
    private final long readCapacityUnits;
    private final long writeCapacityUnits;

    private DynamoDbTable<JournalEventEntity> table;
    private DynamoDbIndex<JournalEventEntity> pendingEventsIndex;
    private DynamoDbIndex<JournalEventEntity> eventIdIndex;

    public DynamoDBJournalEventStore(DynamoDbClient client,
                                     String tableName,
                                     long readCapacityUnits,
                                     long writeCapacityUnits) {
        this.dynamoDBUtil = new DynamoDBUtil(client);
        this.tableName = tableName;
        this.readCapacityUnits = readCapacityUnits;
        this.writeCapacityUnits = writeCapacityUnits;
    }

    /**
     * Initializes the store, gated by the {@link Features#JOURNAL_EVENTS} feature flag: when the flag is off
     * nothing happens (no table, no indexes). When it is on, {@code autoCreate} creates and waits for the
     * configured table when needed; otherwise the manually configured table is checked for the required shape.
     *
     * @param autoCreate whether to create the table when missing
     */
    public synchronized void initialize(boolean autoCreate) {
        if (!isJournalEventsEnabled() || table != null) {
            return;
        }
        if (autoCreate) {
            createTable();
        }
        validateSchema();
        table = dynamoDBUtil.getEnhancedClient().table(tableName, TableSchema.fromBean(JournalEventEntity.class));
        pendingEventsIndex = table.index(JournalEventFieldConstants.PENDING_EVENTS_INDEX);
        eventIdIndex = table.index(JournalEventFieldConstants.EVENT_ID_INDEX);
    }

    /**
     * Stages a conditional event append on the caller's transaction builder. No server call happens until
     * the caller commits the transaction. If the {@code (streamId, streamSequence)} position is already
     * occupied, the complete transaction is cancelled and the failure surfaces as a
     * {@code DatabaseTransactionException} (mapped by {@code DynamoDBTxWrapper}).
     *
     * @param builder the shared {@code TransactWriteItemsEnhancedRequest} builder
     * @param event   the event to append
     */
    Result contributeToTransaction(TransactWriteItemsEnhancedRequest.Builder builder, JournalEvent<AuditEntry> event) {
        if (table == null) {
            throw new IllegalStateException("DynamoDB journal store is not initialized");
        }
        JournalEventEntity eventEntity = DynamoDBJournalEventMapper.toEntity(event);
        builder.addPutItem(table, PutItemEnhancedRequest.builder(JournalEventEntity.class)
                .item(eventEntity)
                .conditionExpression(Expression.builder()
                        .expression("attribute_not_exists(" + JournalEventFieldConstants.KEY_STREAM_ID + ")")
                        .build())
                .build());
        logger.debug("Journal event staged for commit [eventId={} type={} stream={} sequence={}]",
                event.getEventId(), event.getEventType(), event.getStreamId(), event.getStreamSequence());
        return Result.OK();
    }

    @Override
    public Optional<JournalEvent<AuditEntry>> getLastEventByStream(String streamId) {
        if (table == null) {
            return Optional.empty();
        }
        PageIterable<JournalEventEntity> pages = table.query(lastEventQuery(streamId));
        for (Page<JournalEventEntity> page : pages) {
            if (!page.items().isEmpty()) {
                return Optional.of(DynamoDBJournalEventMapper.fromEntity(page.items().get(0)));
            }
        }
        return Optional.empty();
    }

    static QueryEnhancedRequest lastEventQuery(String streamId) {
        QueryConditional queryConditional = QueryConditional.keyEqualTo(Key.builder().partitionValue(streamId).build());
        return QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .scanIndexForward(false)
                .limit(1)
                .consistentRead(true)
                .build();
    }

    @Override
    public List<JournalEvent<AuditEntry>> getUnacknowledgedEvents(int limit) {
        if (pendingEventsIndex == null) {
            return Collections.emptyList();
        }

        List<JournalEvent<AuditEntry>> events = new ArrayList<>();
        Iterator<Page<JournalEventEntity>> pages = pendingEventsIndex.query(pendingEventsQuery(limit)).iterator();
        if (!pages.hasNext()) {
            return events;
        }
        for (JournalEventEntity entity : pages.next().items()) {
            events.add(DynamoDBJournalEventMapper.fromEntity(entity));
        }
        return events;
    }

    static QueryEnhancedRequest pendingEventsQuery(int limit) {
        return QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(
                        Key.builder().partitionValue(JournalEventFieldConstants.PENDING_PARTITION_VALUE).build()))
                .scanIndexForward(true)
                .limit(limit)
                .build();
    }

    @Override
    public long acknowledgeEvents(Collection<String> eventIds) {
        if (eventIdIndex == null || eventIds == null || eventIds.isEmpty()) {
            return 0L;
        }
        long acknowledged = 0L;
        for (String eventId : new LinkedHashSet<>(eventIds)) {
            if (eventId == null || eventId.trim().isEmpty()) {
                continue;
            }
            QueryConditional queryConditional = QueryConditional.keyEqualTo(Key.builder().partitionValue(eventId).build());
            for (Page<JournalEventEntity> page : eventIdIndex.query(queryConditional)) {
                for (JournalEventEntity entity : page.items()) {
                    if (removePendingAttributes(entity)) {
                        acknowledged++;
                    }
                }
            }
        }
        return acknowledged;
    }

    /**
     * Drops both pending attributes, which removes the item from the sparse pending GSI. Conditioned on both
     * attributes existing so re-acknowledging an already acknowledged event is a no-op.
     *
     * @return {@code true} when the item was actually updated
     */
    private boolean removePendingAttributes(JournalEventEntity entity) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put(JournalEventFieldConstants.KEY_STREAM_ID, AttributeValue.builder().s(entity.getStreamId()).build());
        key.put(JournalEventFieldConstants.KEY_STREAM_SEQUENCE,
                AttributeValue.builder().n(String.valueOf(entity.getStreamSequence())).build());
        try {
            dynamoDBUtil.getDynamoDBClient().updateItem(UpdateItemRequest.builder()
                    .tableName(tableName)
                    .key(key)
                    .updateExpression("REMOVE " + JournalEventFieldConstants.KEY_PENDING_PARTITION_KEY + ", "
                            + JournalEventFieldConstants.KEY_PENDING_ORDER_KEY)
                    .conditionExpression("attribute_exists(" + JournalEventFieldConstants.KEY_PENDING_PARTITION_KEY
                            + ") AND attribute_exists(" + JournalEventFieldConstants.KEY_PENDING_ORDER_KEY + ")")
                    .build());
            return true;
        } catch (ConditionalCheckFailedException ex) {
            logger.debug("Journal event already acknowledged [eventId={} stream={} sequence={}]",
                    entity.getEventId(), entity.getStreamId(), entity.getStreamSequence());
            return false;
        }
    }

    private void createTable() {
        List<AttributeDefinition> attributeDefinitions = new ArrayList<>();
        attributeDefinitions.add(AttributeDefinition.builder()
                .attributeName(JournalEventFieldConstants.KEY_STREAM_ID)
                .attributeType(ScalarAttributeType.S)
                .build());
        attributeDefinitions.add(AttributeDefinition.builder()
                .attributeName(JournalEventFieldConstants.KEY_STREAM_SEQUENCE)
                .attributeType(ScalarAttributeType.N)
                .build());
        attributeDefinitions.add(AttributeDefinition.builder()
                .attributeName(JournalEventFieldConstants.KEY_PENDING_PARTITION_KEY)
                .attributeType(ScalarAttributeType.S)
                .build());
        attributeDefinitions.add(AttributeDefinition.builder()
                .attributeName(JournalEventFieldConstants.KEY_PENDING_ORDER_KEY)
                .attributeType(ScalarAttributeType.S)
                .build());
        attributeDefinitions.add(AttributeDefinition.builder()
                .attributeName(JournalEventFieldConstants.KEY_EVENT_ID)
                .attributeType(ScalarAttributeType.S)
                .build());

        List<GlobalSecondaryIndex> globalSecondaryIndexes = new ArrayList<>();
        globalSecondaryIndexes.add(GlobalSecondaryIndex.builder()
                .indexName(JournalEventFieldConstants.PENDING_EVENTS_INDEX)
                .keySchema(Arrays.asList(
                        KeySchemaElement.builder()
                                .attributeName(JournalEventFieldConstants.KEY_PENDING_PARTITION_KEY)
                                .keyType(KeyType.HASH)
                                .build(),
                        KeySchemaElement.builder()
                                .attributeName(JournalEventFieldConstants.KEY_PENDING_ORDER_KEY)
                                .keyType(KeyType.RANGE)
                                .build()))
                .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                .provisionedThroughput(dynamoDBUtil.getProvisionedThroughput(readCapacityUnits, writeCapacityUnits))
                .build());
        globalSecondaryIndexes.add(GlobalSecondaryIndex.builder()
                .indexName(JournalEventFieldConstants.EVENT_ID_INDEX)
                .keySchema(Collections.singletonList(KeySchemaElement.builder()
                        .attributeName(JournalEventFieldConstants.KEY_EVENT_ID)
                        .keyType(KeyType.HASH)
                        .build()))
                .projection(Projection.builder().projectionType(ProjectionType.KEYS_ONLY).build())
                .provisionedThroughput(dynamoDBUtil.getProvisionedThroughput(readCapacityUnits, writeCapacityUnits))
                .build());

        dynamoDBUtil.createTable(
                attributeDefinitions,
                dynamoDBUtil.getKeySchemas(JournalEventFieldConstants.KEY_STREAM_ID,
                        JournalEventFieldConstants.KEY_STREAM_SEQUENCE),
                dynamoDBUtil.getProvisionedThroughput(readCapacityUnits, writeCapacityUnits),
                tableName,
                Collections.emptyList(),
                globalSecondaryIndexes);
    }

    private void validateSchema() {
        TableDescription description;
        try {
            description = dynamoDBUtil.getDynamoDBClient().describeTable(
                    DescribeTableRequest.builder().tableName(tableName).build()).table();
        } catch (ResourceNotFoundException exception) {
            throw new IllegalStateException("DynamoDB journal table '" + tableName
                    + "' is missing or has an invalid schema", exception);
        }

        boolean baseKeysValid = hasKeySchema(description.keySchema(), JournalEventFieldConstants.KEY_STREAM_ID,
                KeyType.HASH.toString(), JournalEventFieldConstants.KEY_STREAM_SEQUENCE, KeyType.RANGE.toString());
        GlobalSecondaryIndexDescription pendingIndex = findIndex(description, JournalEventFieldConstants.PENDING_EVENTS_INDEX);
        GlobalSecondaryIndexDescription eventIdIndex = findIndex(description, JournalEventFieldConstants.EVENT_ID_INDEX);
        boolean indexesValid = pendingIndex != null
                && hasKeySchema(pendingIndex.keySchema(), JournalEventFieldConstants.KEY_PENDING_PARTITION_KEY,
                KeyType.HASH.toString(), JournalEventFieldConstants.KEY_PENDING_ORDER_KEY, KeyType.RANGE.toString())
                && pendingIndex.projection() != null
                && pendingIndex.projection().projectionType() == ProjectionType.ALL
                && eventIdIndex != null
                && hasKeySchema(eventIdIndex.keySchema(), JournalEventFieldConstants.KEY_EVENT_ID, KeyType.HASH.toString())
                && eventIdIndex.projection() != null
                && eventIdIndex.projection().projectionType() == ProjectionType.KEYS_ONLY;
        boolean attributesValid = hasAttribute(description, JournalEventFieldConstants.KEY_STREAM_ID, ScalarAttributeType.S)
                && hasAttribute(description, JournalEventFieldConstants.KEY_STREAM_SEQUENCE, ScalarAttributeType.N)
                && hasAttribute(description, JournalEventFieldConstants.KEY_PENDING_PARTITION_KEY, ScalarAttributeType.S)
                && hasAttribute(description, JournalEventFieldConstants.KEY_PENDING_ORDER_KEY, ScalarAttributeType.S)
                && hasAttribute(description, JournalEventFieldConstants.KEY_EVENT_ID, ScalarAttributeType.S);
        if (!baseKeysValid || !indexesValid || !attributesValid) {
            throw new IllegalStateException("DynamoDB journal table '" + tableName
                    + "' has an invalid key or index schema");
        }
    }

    private GlobalSecondaryIndexDescription findIndex(TableDescription description, String indexName) {
        if (description.globalSecondaryIndexes() == null) {
            return null;
        }
        for (GlobalSecondaryIndexDescription index : description.globalSecondaryIndexes()) {
            if (indexName.equals(index.indexName())) {
                return index;
            }
        }
        return null;
    }

    private boolean hasAttribute(TableDescription description, String name, ScalarAttributeType type) {
        if (description.attributeDefinitions() == null) {
            return false;
        }
        return description.attributeDefinitions().stream()
                .anyMatch(attribute -> name.equals(attribute.attributeName()) && type == attribute.attributeType());
    }

    private boolean hasKeySchema(List<KeySchemaElement> schema, String... expected) {
        if (schema == null || schema.size() * 2 != expected.length) {
            return false;
        }
        for (int i = 0; i < schema.size(); i++) {
            KeySchemaElement element = schema.get(i);
            if (!expected[2 * i].equals(element.attributeName())
                    || !expected[2 * i + 1].equals(element.keyType().toString())) {
                return false;
            }
        }
        return true;
    }

    private static boolean isJournalEventsEnabled() {
        try {
            return FeatureFlag.isEnabled(Features.JOURNAL_EVENTS, false);
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
