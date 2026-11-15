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

import io.flamingock.internal.common.core.error.FlamingockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * There is little behaviour in an exception class, so these cover only what something else depends on:
 * the position the retry reads back, the cause a diagnosis needs, and the message an operator sees.
 */
class DuplicateStreamSequenceExceptionTest {

    @Test
    @DisplayName("the message names both halves of the position")
    void messageNamesTheWholePosition() {
        DuplicateStreamSequenceException exception =
                new DuplicateStreamSequenceException("stage-one", 42L, new IllegalStateException("taken"));

        String message = exception.getMessage();
        // Both halves, because a sequence on its own identifies nothing: every stream has a position 1.
        // This message is all an operator gets from a log line.
        assertTrue(message.contains("stage-one"), message);
        assertTrue(message.contains("42"), message);
    }

    @Test
    @DisplayName("the backend's own exception is preserved as the cause")
    void preservesTheBackendCause() {
        IllegalStateException backendFailure = new IllegalStateException("E11000 duplicate key");

        DuplicateStreamSequenceException exception =
                new DuplicateStreamSequenceException("stage-one", 42L, backendFailure);

        // The one that matters. Swallowing it would make a position collision and a database rejecting
        // the write for an unrelated reason look identical after the fact.
        assertSame(backendFailure, exception.getCause());
    }

    @Test
    @DisplayName("the position is readable, which is what a retry needs")
    void exposesThePosition() {
        DuplicateStreamSequenceException exception =
                new DuplicateStreamSequenceException("stage-one", 42L, new IllegalStateException());

        assertEquals("stage-one", exception.getStreamId());
        assertEquals(42L, exception.getStreamSequence());
    }

    @Test
    @DisplayName("a store with no exception to wrap still reports the position")
    void supportsDetectionWithoutABackendException() {
        // For backends that report a rejected conditional write as a value rather than by throwing.
        DuplicateStreamSequenceException exception =
                new DuplicateStreamSequenceException("stage-one", 42L);

        assertNull(exception.getCause());
        assertEquals("stage-one", exception.getStreamId());
        assertEquals(42L, exception.getStreamSequence());
    }

    @Test
    @DisplayName("it is a FlamingockException, so existing handlers see it")
    void isAFlamingockException() {
        // Relevant if it ever escapes un-retried: it surfaces as a Flamingock error rather than a bare
        // runtime exception.
        assertTrue(new DuplicateStreamSequenceException("stage-one", 1L) instanceof FlamingockException);
    }
}
