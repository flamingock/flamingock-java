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
package io.flamingock.internal.common.couchbase.journal;

/**
 * JSON field names for the local journal collection ({@code flamingockJournalEvents}).
 * <p>
 * Declared locally because the shared {@code AuditEntryFieldConstants}/{@code CommunityPersistenceConstants}
 * live in the external {@code flamingock-general-util} artifact and cannot be extended from this repository.
 */
public final class JournalEventFieldConstants {

    public static final String KEY_EVENT_ID = "eventId";
    public static final String KEY_EVENT_TYPE = "eventType";
    public static final String KEY_EVENT_VERSION = "eventVersion";
    public static final String KEY_STREAM_ID = "streamId";
    public static final String KEY_STREAM_SEQUENCE = "streamSequence";
    public static final String KEY_OCCURRED_AT = "occurredAt";
    public static final String KEY_DATA = "data";
    public static final String KEY_ACKNOWLEDGED = "acknowledged";

    private JournalEventFieldConstants() {
    }
}
