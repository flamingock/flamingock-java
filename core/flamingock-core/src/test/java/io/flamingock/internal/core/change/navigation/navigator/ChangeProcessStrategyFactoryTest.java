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
package io.flamingock.internal.core.change.navigation.navigator;

import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.common.core.targets.OperationType;
import io.flamingock.internal.core.change.executable.ExecutableChange;
import io.flamingock.internal.core.change.navigation.navigator.strategy.NonTxChangeProcessStrategy;
import io.flamingock.internal.core.change.navigation.navigator.strategy.SharedTxChangeProcessStrategy;
import io.flamingock.internal.core.change.navigation.navigator.strategy.SimpleTxChangeProcessStrategy;
import io.flamingock.internal.core.external.targets.operations.TransactionalTargetSystemOps;
import io.flamingock.internal.core.operation.result.ChangeResultBuilder;
import io.flamingock.internal.core.pipeline.execution.ExecutionContext;
import io.flamingock.internal.core.runtime.proxy.LockGuardProxyFactory;
import io.flamingock.internal.util.TimeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChangeProcessStrategyFactoryTest {

    @Test
    @DisplayName("Should use a transaction for a transactional change and a transaction-capable target")
    void transactionalChangeAndTransactionalTargetUseTransaction() {
        assertInstanceOf(SimpleTxChangeProcessStrategy.class,
                strategy(true, OperationType.TX_NON_SYNC));
    }

    @Test
    @DisplayName("Should not use a transaction for a non-transactional change and a transaction-capable target")
    void nonTransactionalChangeAndTransactionalTargetDoNotUseTransaction() {
        assertInstanceOf(NonTxChangeProcessStrategy.class,
                strategy(false, OperationType.TX_NON_SYNC));
    }

    @Test
    @DisplayName("Should not use a transaction for a transactional change when the target does not support transactions")
    void transactionalChangeAndUnsupportedTargetDoNotUseTransaction() {
        assertInstanceOf(NonTxChangeProcessStrategy.class,
                strategy(true, OperationType.NON_TX));
    }

    @Test
    @DisplayName("Should not use a transaction for a non-transactional change when the target does not support transactions")
    void nonTransactionalChangeAndUnsupportedTargetDoNotUseTransaction() {
        assertInstanceOf(NonTxChangeProcessStrategy.class,
                strategy(false, OperationType.NON_TX));
    }

    @Test
    @DisplayName("Should preserve the shared transaction strategy for a transaction-capable target")
    void sharedTransactionalTargetKeepsSharedStrategy() {
        assertInstanceOf(SharedTxChangeProcessStrategy.class,
                strategy(true, OperationType.TX_AUDIT_STORE_SHARED));
    }

    private static ChangeProcessStrategy strategy(boolean transactional, OperationType operationType) {
        ExecutableChange change = mock(ExecutableChange.class);
        when(change.isTransactional()).thenReturn(transactional);
        when(change.getId()).thenReturn("change");

        TransactionalTargetSystemOps target = mock(TransactionalTargetSystemOps.class);
        when(target.getOperationType()).thenReturn(operationType);
        when(target.getId()).thenReturn("target");

        return ChangeProcessStrategyFactory.getStrategy(
                change,
                target,
                mock(AuditStoreStepOperations.class),
                mock(ContextResolver.class),
                mock(ExecutionContext.class),
                mock(ChangeResultBuilder.class),
                mock(LockGuardProxyFactory.class),
                mock(TimeService.class));
    }
}
