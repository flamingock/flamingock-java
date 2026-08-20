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

/**
 * Attribute and index names of the DynamoDB journal events table
 * ({@code flamingockJournalEvents}).
 * <p>
 * The base key is {@code (streamId, streamSequence)}; the sparse pending GSI
 * ({@link #PENDING_EVENTS_INDEX}) carries only items with a {@code pendingPartitionKey} and
 * {@code pendingOrderKey},
 * and the non-unique eventId GSI ({@link #EVENT_ID_INDEX}) serves acknowledgement
 * lookups only. Event identity is enforced transactionally by a reserved item in this table.
 */
public final class JournalEventFieldConstants {

    public static final String DEFAULT_JOURNAL_REPOSITORY_NAME = "flamingockJournalEvents";

    public static final String KEY_STREAM_ID = "streamId";
    public static final String KEY_STREAM_SEQUENCE = "streamSequence";
    public static final String KEY_PENDING_PARTITION_KEY = "pendingPartitionKey";
    public static final String KEY_PENDING_ORDER_KEY = "pendingOrderKey";
    public static final String KEY_EVENT_ID = "eventId";

    public static final String PENDING_PARTITION_VALUE = "pending";

    public static final String PENDING_EVENTS_INDEX = "PendingEventsIndex";
    public static final String EVENT_ID_INDEX = "EventIdIndex";

    private JournalEventFieldConstants() {
    }
}
