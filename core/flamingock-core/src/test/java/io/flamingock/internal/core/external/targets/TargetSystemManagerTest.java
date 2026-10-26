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

import io.flamingock.internal.common.core.context.RuntimeContext;
import io.flamingock.internal.common.core.targets.OperationType;
import io.flamingock.internal.common.core.external.ExecutionWrapper;
import io.flamingock.internal.common.core.pipeline.PipelineHelper;
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

    @Test
    @DisplayName("Should expose non-transactional operations when the concrete target does not support transactions")
    void shouldExposeNonTransactionalOperationsWhenTransactionsAreUnsupported() {
        TargetSystemManager manager = new TargetSystemManager();
        manager.add(new StubTransactionalTargetSystem("tx-disabled", false));

        assertEquals(OperationType.NON_TX, manager.getTargetSystem("tx-disabled").getOperationType());
    }

    @Test
    @DisplayName("Should preserve transactional operations by default")
    void shouldPreserveTransactionalOperationsByDefault() {
        TargetSystemManager manager = new TargetSystemManager();
        manager.add(new StubTransactionalTargetSystem("tx-default"));

        assertEquals(OperationType.TX_NON_SYNC, manager.getTargetSystem("tx-default").getOperationType());
    }

    // -----------------------------------------------------------------------------------------------
    // The reserved audit-store alias
    // -----------------------------------------------------------------------------------------------

    @Test
    @DisplayName("The reserved alias resolves to the audit store's target system")
    void aliasResolvesToTheAuditStoreTargetSystem() {
        TargetSystemManager manager = new TargetSystemManager();
        StubTransactionalTargetSystem auditStoreSystem = new StubTransactionalTargetSystem("audit-db");
        manager.add(auditStoreSystem);
        manager.registerAuditStoreTargetSystem(auditStoreSystem);

        assertEquals("audit-db",
                manager.getTargetSystem(PipelineHelper.AUDIT_STORE_TARGET_SYSTEM).getId());
    }

    @Test
    @DisplayName("The alias does not duplicate the target system")
    void aliasDoesNotDuplicateTheTargetSystem() {
        TargetSystemManager manager = new TargetSystemManager();
        StubTransactionalTargetSystem auditStoreSystem = new StubTransactionalTargetSystem("audit-db");
        manager.add(auditStoreSystem);
        manager.registerAuditStoreTargetSystem(auditStoreSystem);

        // The reason the alias is an indirection rather than a second map entry: initialize() and this
        // method both iterate the map's values, so a duplicate would be initialized twice and counted
        // twice - and the duplicate count reaches CloudAuditStoreImpl as duplicate audit markers.
        assertEquals(1, manager.getTransactionalTargetSystems().size());
    }

    @Test
    @DisplayName("The alias registers the target system when the user did not")
    void aliasRegistersTheTargetSystemWhenTheUserDidNot() {
        TargetSystemManager manager = new TargetSystemManager();
        // An audit store built with from(targetSystem) does not require that system to be added to the
        // builder, so this is the ordinary case, not an edge one.
        manager.registerAuditStoreTargetSystem(new StubTransactionalTargetSystem("audit-db"));

        assertEquals("audit-db",
                manager.getTargetSystem(PipelineHelper.AUDIT_STORE_TARGET_SYSTEM).getId());
        assertEquals(1, manager.getTransactionalTargetSystems().size());
    }

    @Test
    @DisplayName("The alias does not override an instance the user already registered")
    void aliasDoesNotOverrideAUserRegisteredInstance() {
        TargetSystemManager manager = new TargetSystemManager();
        StubTransactionalTargetSystem userRegistered = new StubTransactionalTargetSystem("shared-id");
        StubTransactionalTargetSystem auditStoreBuiltFrom = new StubTransactionalTargetSystem("shared-id");
        manager.add(userRegistered);
        manager.registerAuditStoreTargetSystem(auditStoreBuiltFrom);

        // Overwriting would silently change what the user's own changes execute against, so theirs wins
        // and the mismatch is logged instead.
        assertSame(userRegistered, manager.getTransactionalTargetSystems().get(0));
        assertEquals(1, manager.getTransactionalTargetSystems().size());
    }

    @Test
    @DisplayName("A null external system fails with a message naming the audit store")
    void aliasRejectsANullExternalSystem() {
        TargetSystemManager manager = new TargetSystemManager();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> manager.registerAuditStoreTargetSystem(null));

        // A bare "Target system null not allowed" says nothing about where the null came from. Test
        // doubles of an audit store return null here, so this message is the one someone will actually
        // read when they hit it.
        assertTrue(exception.getMessage().contains("audit store"), exception.getMessage());
        assertTrue(exception.getMessage().contains("getExternalSystem"), exception.getMessage());
    }

    @Test
    @DisplayName("The alias rejects an external system that is not a target system")
    void aliasRejectsANonTargetSystem() {
        TargetSystemManager manager = new TargetSystemManager();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> manager.registerAuditStoreTargetSystem(() -> "not-a-target-system"));

        assertTrue(exception.getMessage().contains("AbstractTargetSystem"), exception.getMessage());
    }

    @Test
    @DisplayName("The alias cannot be registered once the manager is initialized")
    void aliasCannotBeRegisteredAfterInitialize() {
        TargetSystemManager manager = new TargetSystemManager();
        manager.initialize(new io.flamingock.internal.core.context.SimpleContext());

        // Why the builder registers the alias before initialize(): registering later would also leave an
        // otherwise-unregistered system uninitialized.
        assertThrows(IllegalArgumentException.class,
                () -> manager.registerAuditStoreTargetSystem(new StubTransactionalTargetSystem("audit-db")));
    }

    @Test
    @DisplayName("An unknown id still fails, alias or not")
    void unknownIdStillThrows() {
        TargetSystemManager manager = new TargetSystemManager();
        manager.registerAuditStoreTargetSystem(new StubTransactionalTargetSystem("audit-db"));

        assertThrows(RuntimeException.class, () -> manager.getTargetSystem("nope"));
    }

    private static class StubNonTransactionalTargetSystem extends AbstractTargetSystem<StubNonTransactionalTargetSystem> {
        StubNonTransactionalTargetSystem(String id) { super(id); }
        @Override protected StubNonTransactionalTargetSystem getSelf() { return this; }
        @Override protected void enhanceExecutionRuntime(RuntimeContext rt, boolean tx) {}
    }

    private static class StubTransactionalTargetSystem extends TransactionalTargetSystem<StubTransactionalTargetSystem> {
        private final boolean transactionsSupported;
        StubTransactionalTargetSystem(String id) { this(id, true); }
        StubTransactionalTargetSystem(String id, boolean transactionsSupported) {
            super(id);
            this.transactionsSupported = transactionsSupported;
        }
        @Override protected StubTransactionalTargetSystem getSelf() { return this; }
        @Override public boolean supportsTransactions() { return transactionsSupported; }
        @Override public ExecutionWrapper getTxWrapper() { return null; }
        @Override protected void enhanceExecutionRuntime(RuntimeContext rt, boolean tx) {}
        @Override public void initialize(io.flamingock.internal.common.core.context.ContextResolver ctx) {}
    }
}
