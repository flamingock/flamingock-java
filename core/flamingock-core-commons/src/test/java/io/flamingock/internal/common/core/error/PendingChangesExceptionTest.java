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
package io.flamingock.internal.common.core.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingChangesExceptionTest {

    @Test
    @DisplayName("Should return the pending count passed to the constructor")
    void shouldReturnPendingCount() {
        // Given
        int pendingCount = 5;

        // When
        PendingChangesException exception = new PendingChangesException(pendingCount);

        // Then
        assertEquals(pendingCount, exception.getPendingCount());
    }

    @Test
    @DisplayName("Should include the pending count in the exception message")
    void shouldIncludeCountInMessage() {
        // Given
        int pendingCount = 5;

        // When
        PendingChangesException exception = new PendingChangesException(pendingCount);

        // Then
        assertTrue(exception.getMessage().contains("5"),
                "Message should contain the pending count as a string");
    }

    @Test
    @DisplayName("Should be an instance of FlamingockException")
    void shouldExtendFlamingockException() {
        // Given / When
        PendingChangesException exception = new PendingChangesException(3);

        // Then
        assertInstanceOf(FlamingockException.class, exception);
    }

    @Test
    @DisplayName("Should work correctly with zero pending changes")
    void shouldWorkWithZeroPendingCount() {
        // Given / When
        PendingChangesException exception = new PendingChangesException(0);

        // Then
        assertEquals(0, exception.getPendingCount());
        assertTrue(exception.getMessage().contains("0"));
    }

    @Test
    @DisplayName("Should work correctly with a large pending count")
    void shouldWorkWithLargePendingCount() {
        // Given
        int largeCount = 999;

        // When
        PendingChangesException exception = new PendingChangesException(largeCount);

        // Then
        assertEquals(largeCount, exception.getPendingCount());
        assertTrue(exception.getMessage().contains("999"));
    }
}
