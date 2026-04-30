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
package io.flamingock.internal.core.external.store.lock;

import io.flamingock.internal.util.id.RunnerId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Pins the {@link LockKey} equality contract that's inherited from {@code Id<T>}: equal when
 * the runtime classes match exactly and the underlying values are equal, not equal otherwise.
 * Catches accidental cross-type equality (e.g. {@code LockKey} vs {@code RunnerId}) that would
 * silently break {@code HashSet}/{@code Map} keying if it ever drifted.
 */
class LockKeyTest {

    @Test
    @DisplayName("equals: same value, same type → equal")
    void equalsSameValueSameType() {
        assertEquals(LockKey.fromString("svc"), LockKey.fromString("svc"));
    }

    @Test
    @DisplayName("equals: different value, same type → not equal")
    void equalsDifferentValueSameType() {
        assertNotEquals(LockKey.fromString("svc-a"), LockKey.fromString("svc-b"));
    }

    @Test
    @DisplayName("equals: same value, different StringId subclass → not equal (rejects cross-type)")
    void equalsRejectsCrossStringIdSubclass() {
        LockKey lockKey = LockKey.fromString("shared-value");
        RunnerId runnerId = RunnerId.fromString("shared-value");
        assertNotEquals(lockKey, runnerId);
        assertNotEquals(runnerId, lockKey);
    }

    @Test
    @DisplayName("hashCode: equal LockKeys hash equally")
    void hashCodeMatchesEquals() {
        assertEquals(LockKey.fromString("svc").hashCode(), LockKey.fromString("svc").hashCode());
    }
}
