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
package io.flamingock.store.sql.internal;

/**
 * SQL names used by the relational Journal Event store.
 */
final class JournalEventConstants {

    static final String EVENT_ID = "event_id";
    static final String EVENT_TYPE = "event_type";
    static final String EVENT_VERSION = "event_version";
    static final String STREAM_ID = "stream_id";
    static final String STREAM_SEQUENCE = "stream_sequence";
    static final String OCCURRED_AT = "occurred_at";
    static final String ACKNOWLEDGED = "acknowledged";

    static final String PENDING_EVENTS_INDEX = "pending_events";
    static final String EVENT_ID_INDEX = "event_id";

    private JournalEventConstants() {
    }

    /**
     * Validates a configured SQL identifier before it is interpolated into DDL or DML.
     *
     * @param value     identifier to validate
     * @param fieldName configuration field containing the identifier
     */
    static void validateIdentifier(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        if (!value.matches("[A-Za-z][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException(fieldName + " must be a simple SQL identifier");
        }
    }

    /**
     * Ensures that two configured SQL resources cannot address the same table.
     */
    static void validateDistinct(String firstName,
                                 String firstField,
                                 String secondName,
                                 String secondField) {
        if (firstName.trim().equalsIgnoreCase(secondName.trim())) {
            throw new IllegalArgumentException(firstField + " and " + secondField + " must not be the same");
        }
    }
}
