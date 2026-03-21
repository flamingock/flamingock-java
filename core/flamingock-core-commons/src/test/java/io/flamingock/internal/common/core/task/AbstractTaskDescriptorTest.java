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
package io.flamingock.internal.common.core.task;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AbstractTaskDescriptor transactional nullable support")
class AbstractTaskDescriptorTest {

    private static AbstractTaskDescriptor createDescriptor(Boolean transactional) {
        return new AbstractTaskDescriptor(
                "test-id", "001", "author", "source", "sourceFile",
                false, transactional, false,
                null, null, false
        ) {};
    }

    @Test
    @DisplayName("getTransactionalFlag() returns empty when field is null")
    void getTransactionalReturnsEmptyWhenNull() {
        AbstractTaskDescriptor descriptor = createDescriptor(null);
        assertEquals(Optional.empty(), descriptor.getTransactionalFlag());
    }

    @Test
    @DisplayName("getTransactionalFlag() returns Optional.of(true) when field is true")
    void getTransactionalReturnsPresentWhenTrue() {
        AbstractTaskDescriptor descriptor = createDescriptor(true);
        assertEquals(Optional.of(true), descriptor.getTransactionalFlag());
    }

    @Test
    @DisplayName("getTransactionalFlag() returns Optional.of(false) when field is false")
    void getTransactionalReturnsPresentWhenFalse() {
        AbstractTaskDescriptor descriptor = createDescriptor(false);
        assertEquals(Optional.of(false), descriptor.getTransactionalFlag());
    }

    @Test
    @DisplayName("setTransactionalFlag() accepts null")
    void setTransactionalFlagAcceptsNull() {
        AbstractTaskDescriptor descriptor = createDescriptor(true);
        descriptor.setTransactionalFlag(null);
        assertEquals(Optional.empty(), descriptor.getTransactionalFlag());
    }

    @Test
    @DisplayName("toString() handles null transactional without throwing")
    void toStringHandlesNullTransactional() {
        AbstractTaskDescriptor descriptor = createDescriptor(null);
        assertDoesNotThrow(() -> descriptor.toString());
        assertTrue(descriptor.toString().contains("transactionalFlag=null"));
    }
}
