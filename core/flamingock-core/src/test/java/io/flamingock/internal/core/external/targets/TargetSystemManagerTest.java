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
package io.flamingock.internal.core.external.targets;

import io.flamingock.internal.core.runtime.ExecutionRuntime;
import io.flamingock.internal.core.transaction.TransactionWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TargetSystemManagerTest {

    @Test
    @DisplayName("Should return empty list when no target systems are registered")
    void shouldReturnEmptyListWhenNoTargetSystemsRegistered() {
        TargetSystemManager manager = new TargetSystemManager();
        assertTrue(manager.getTransactionalTargetSystems().isEmpty());
    }

    @Test
    @DisplayName("Should return empty list when only non-transactional target systems are registered")
    void shouldReturnEmptyListWhenOnlyNonTransactionalTargetSystems() {
        TargetSystemManager manager = new TargetSystemManager();
        manager.add(new StubNonTransactionalTargetSystem("non-tx-1"));
        assertTrue(manager.getTransactionalTargetSystems().isEmpty());
    }

    @Test
    @DisplayName("Should return only transactional target systems from mixed registrations")
    void shouldReturnTransactionalTargetSystems() {
        TargetSystemManager manager = new TargetSystemManager();
        manager.add(new StubNonTransactionalTargetSystem("non-tx-1"));
        manager.add(new StubTransactionalTargetSystem("tx-1"));

        List<TransactionalTargetSystem<?>> result = manager.getTransactionalTargetSystems();

        assertEquals(1, result.size());
        assertEquals("tx-1", result.get(0).getId());
    }

    @Test
    @DisplayName("Should return all transactional target systems when multiple are registered")
    void shouldReturnAllTransactionalTargetSystems() {
        TargetSystemManager manager = new TargetSystemManager();
        manager.add(new StubTransactionalTargetSystem("tx-1"));
        manager.add(new StubTransactionalTargetSystem("tx-2"));
        manager.add(new StubNonTransactionalTargetSystem("non-tx-1"));

        List<TransactionalTargetSystem<?>> result = manager.getTransactionalTargetSystems();

        assertEquals(2, result.size());
    }

    private static class StubNonTransactionalTargetSystem extends AbstractTargetSystem<StubNonTransactionalTargetSystem> {
        StubNonTransactionalTargetSystem(String id) { super(id); }
        @Override protected StubNonTransactionalTargetSystem getSelf() { return this; }
        @Override protected void enhanceExecutionRuntime(ExecutionRuntime rt, boolean tx) {}
    }

    private static class StubTransactionalTargetSystem extends TransactionalTargetSystem<StubTransactionalTargetSystem> {
        StubTransactionalTargetSystem(String id) { super(id); }
        @Override protected StubTransactionalTargetSystem getSelf() { return this; }
        @Override public TransactionWrapper getTxWrapper() { return null; }
        @Override protected void enhanceExecutionRuntime(ExecutionRuntime rt, boolean tx) {}
        @Override public void initialize(io.flamingock.internal.common.core.context.ContextResolver ctx) {}
    }
}
