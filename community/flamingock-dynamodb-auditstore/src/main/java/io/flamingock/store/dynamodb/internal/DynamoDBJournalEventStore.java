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
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * DynamoDB implementation of the local journal ({@code flamingockJournalEvents}).
 * <p>
 * Sibling of {@link DynamoDBAuditor}/{@link DynamoDBLockService}: it owns its own table and index setup.
 * <p>
 * The table has a base key of {@code (streamId, streamSequence)} with two GSIs:
 * <ul>
 *   <li>{@code PendingEventsIndex} — a sparse GSI over {@code (pendingStreamId, streamSequence)} that only
 *       contains unacknowledged events and serves the ordered unacknowledged batch scan;</li>
 *   <li>{@code EventIdIndex} — a deliberately non-unique GSI over {@code eventId} serving the
 *       acknowledgement lookup only. It MUST NOT enforce eventId uniqueness; only the
 *       {@code (streamId, streamSequence)} position guard restricts appends.</li>
 * </ul>
 * Reads and acknowledgements are exposed through {@link JournalEventStore}. The append
 * ({@link #write(TransactWriteItemsEnhancedRequest.Builder, JournalEvent)}) deliberately is not: it stages a
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
     * nothing happens (no table, no indexes). When it is on, {@code autoCreate} creates the table (ignoring
     * {@code ResourceInUseException} when it already exists) or asserts that it exists.
     *
     * @param autoCreate whether to create the table when missing
     */
    public void initialize(boolean autoCreate) {
        FeatureFlag.ifEnabled(Features.JOURNAL_EVENTS, () -> {
            if (autoCreate) {
                createTable();
            } else {
                assertTableExists();
            }
            table = dynamoDBUtil.getEnhancedClient().table(tableName, TableSchema.fromBean(JournalEventEntity.class));
            pendingEventsIndex = table.index(JournalEventFieldConstants.PENDING_EVENTS_INDEX);
            eventIdIndex = table.index(JournalEventFieldConstants.EVENT_ID_INDEX);
        });
    }

    /**
     * Stages a conditional append of the event on the caller's transaction builder. No server call happens
     * until the caller commits the transaction. If another event already occupies
     * {@code (streamId, streamSequence)}, the commit is cancelled and the failure surfaces as a
     * {@code DatabaseTransactionException} (mapped by {@code DynamoDBTxWrapper}).
     *
     * @param builder the shared {@code TransactWriteItemsEnhancedRequest} builder
     * @param event   the event to append
     */
    public void write(TransactWriteItemsEnhancedRequest.Builder builder, JournalEvent<AuditEntry> event) {
        if (table == null) {
            throw new IllegalStateException("DynamoDB journal store is not initialized");
        }
        builder.addPutItem(table, PutItemEnhancedRequest.builder(JournalEventEntity.class)
                .item(DynamoDBJournalEventMapper.toEntity(event))
                .conditionExpression(Expression.builder()
                        .expression("attribute_not_exists(" + JournalEventFieldConstants.KEY_STREAM_ID + ")")
                        .build())
                .build());
        logger.debug("Journal event staged for commit [eventId={} type={} stream={} sequence={}]",
                event.getEventId(), event.getEventType(), event.getStreamId(), event.getStreamSequence());
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
        List<JournalEventEntity> entities = new ArrayList<>();
        pendingEventsIndex.scan().forEach(page -> entities.addAll(page.items()));
        entities.sort(Comparator.comparing(JournalEventEntity::getPendingStreamId)
                .thenComparing(JournalEventEntity::getStreamSequence));
        return entities.stream()
                .limit(limit)
                .map(DynamoDBJournalEventMapper::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public long acknowledgeEvents(Collection<String> eventIds) {
        if (eventIdIndex == null || eventIds == null || eventIds.isEmpty()) {
            return 0L;
        }
        long acknowledged = 0L;
        for (String eventId : eventIds) {
            QueryConditional queryConditional = QueryConditional.keyEqualTo(Key.builder().partitionValue(eventId).build());
            for (Page<JournalEventEntity> page : eventIdIndex.query(queryConditional)) {
                for (JournalEventEntity entity : page.items()) {
                    if (removePendingAttribute(entity)) {
                        acknowledged++;
                    }
                }
            }
        }
        return acknowledged;
    }

    /**
     * Drops the {@code pendingStreamId} attribute, which removes the item from the sparse pending GSI.
     * Conditioned on the attribute existing so re-acknowledging an already acknowledged event is a no-op.
     *
     * @return {@code true} when the item was actually updated
     */
    private boolean removePendingAttribute(JournalEventEntity entity) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put(JournalEventFieldConstants.KEY_STREAM_ID, AttributeValue.builder().s(entity.getStreamId()).build());
        key.put(JournalEventFieldConstants.KEY_STREAM_SEQUENCE,
                AttributeValue.builder().n(String.valueOf(entity.getStreamSequence())).build());
        try {
            dynamoDBUtil.getDynamoDBClient().updateItem(UpdateItemRequest.builder()
                    .tableName(tableName)
                    .key(key)
                    .updateExpression("REMOVE " + JournalEventFieldConstants.KEY_PENDING_STREAM_ID)
                    .conditionExpression("attribute_exists(" + JournalEventFieldConstants.KEY_PENDING_STREAM_ID + ")")
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
                .attributeName(JournalEventFieldConstants.KEY_PENDING_STREAM_ID)
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
                                .attributeName(JournalEventFieldConstants.KEY_PENDING_STREAM_ID)
                                .keyType(KeyType.HASH)
                                .build(),
                        KeySchemaElement.builder()
                                .attributeName(JournalEventFieldConstants.KEY_STREAM_SEQUENCE)
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
                .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
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

    private void assertTableExists() {
        dynamoDBUtil.getDynamoDBClient().describeTable(DescribeTableRequest.builder().tableName(tableName).build());
    }
}
