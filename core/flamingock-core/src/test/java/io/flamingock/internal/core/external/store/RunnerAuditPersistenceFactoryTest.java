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
package io.flamingock.internal.core.external.store;

import io.flamingock.internal.common.core.audit.AuditPersistence;
import io.flamingock.internal.common.core.audit.AuditPersistenceFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class RunnerAuditPersistenceFactoryTest {
    @Test
    void lazilyCachesEachRawStageAndIsolatesFactories() {
        List<String> calls = new ArrayList<>();
        AuditPersistenceFactory<AuditPersistence> delegate = stage -> {
            calls.add(stage);
            return mock(AuditPersistence.class);
        };
        RunnerAuditPersistenceFactory first = new RunnerAuditPersistenceFactory(delegate);
        RunnerAuditPersistenceFactory second = new RunnerAuditPersistenceFactory(delegate);
        assertTrue(calls.isEmpty());
        AuditPersistence a = first.get("a");
        AuditPersistence b = first.get("b");
        AuditPersistence legacy = first.get(null);
        AuditPersistence empty = first.get("");
        assertSame(a, first.get("a"));
        assertSame(b, first.get("b"));
        assertSame(legacy, first.get(null));
        assertSame(empty, first.get(""));
        assertNotSame(a, b);
        assertNotSame(legacy, empty);
        assertNotSame(a, second.get("a"));
        assertEquals(Arrays.asList("a", "b", null, "", "a"), calls);
    }
}
