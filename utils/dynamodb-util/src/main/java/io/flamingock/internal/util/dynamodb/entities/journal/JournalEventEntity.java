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
package io.flamingock.internal.util.dynamodb.entities.journal;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/**
 * DynamoDB persistence representation of a {@code JournalEvent} carrying an {@code AuditEntry} payload.
 * <p>
 * Base key: {@code (streamId, streamSequence)}. {@code pendingPartitionKey} and {@code pendingOrderKey} are
 * sparse attributes: they are only present while the event has not been acknowledged, which is what makes
 * the event visible in {@code PendingEventsIndex}. The payload is the JSON serialization of the embedded
 * {@code AuditEntryEntity}
 * ({@code AuditEntry} itself has no no-arg constructor, so it cannot be a {@code @DynamoDbBean}).
 */
@DynamoDbBean
public class JournalEventEntity {

    private String streamId;
    private Long streamSequence;
    private String pendingPartitionKey;
    private String pendingOrderKey;
    private String eventId;
    private String eventType;
    private String occurredAt;
    private Integer eventVersion;
    private String payload;

    public JournalEventEntity() {
    }

    @DynamoDbPartitionKey
    public String getStreamId() {
        return streamId;
    }

    public void setStreamId(String streamId) {
        this.streamId = streamId;
    }

    @DynamoDbSortKey
    public Long getStreamSequence() {
        return streamSequence;
    }

    public void setStreamSequence(Long streamSequence) {
        this.streamSequence = streamSequence;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = JournalEventFieldConstants.PENDING_EVENTS_INDEX)
    public String getPendingPartitionKey() {
        return pendingPartitionKey;
    }

    public void setPendingPartitionKey(String pendingPartitionKey) {
        this.pendingPartitionKey = pendingPartitionKey;
    }

    @DynamoDbSecondarySortKey(indexNames = JournalEventFieldConstants.PENDING_EVENTS_INDEX)
    public String getPendingOrderKey() {
        return pendingOrderKey;
    }

    public void setPendingOrderKey(String pendingOrderKey) {
        this.pendingOrderKey = pendingOrderKey;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = JournalEventFieldConstants.EVENT_ID_INDEX)
    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(String occurredAt) {
        this.occurredAt = occurredAt;
    }

    public Integer getEventVersion() {
        return eventVersion;
    }

    public void setEventVersion(Integer eventVersion) {
        this.eventVersion = eventVersion;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }
}
