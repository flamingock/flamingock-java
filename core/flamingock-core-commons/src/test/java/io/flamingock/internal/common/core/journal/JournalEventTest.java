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
package io.flamingock.internal.common.core.journal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertThrows;

class JournalEventTest {

    @Test
    @DisplayName("rejects null and blank idempotency keys")
    void rejectsNullAndBlankIdempotencyKeys() {
        assertThrows(IllegalArgumentException.class, () -> event(null));
        assertThrows(IllegalArgumentException.class, () -> event("   "));
    }

    private static JournalEvent<String> event(String idempotencyKey) {
        return new JournalEvent<>(
                "event-1", idempotencyKey, JournalEventType.CHANGE_STATE, "stream-1", 1L,
                Instant.parse("2026-09-18T00:00:00Z"), "payload");
    }
}
