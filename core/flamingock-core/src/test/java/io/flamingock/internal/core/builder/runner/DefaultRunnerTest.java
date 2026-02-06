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
package io.flamingock.internal.core.builder.runner;

import io.flamingock.internal.core.operation.AuditListArgs;
import io.flamingock.internal.core.operation.AuditListResult;
import io.flamingock.internal.core.operation.Operation;
import io.flamingock.internal.core.operation.RunnableOperation;
import io.flamingock.internal.util.id.RunnerId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DefaultRunnerTest {

    @Mock
    private Operation<AuditListArgs, AuditListResult> operation;

    @Mock
    private Runnable finalizer;

    private RunnerId runnerId;
    private AuditListArgs args;
    private RunnableOperation<AuditListArgs, AuditListResult> runnableOperation;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        runnerId = RunnerId.generate("test-service");
        args = new AuditListArgs();
        runnableOperation = new RunnableOperation<>(operation, args);
    }

    @Test
    @DisplayName("Should execute operation when run is called")
    void shouldExecuteOperationWhenRunIsCalled() {
        // Given
        when(operation.execute(args)).thenReturn(new AuditListResult(Collections.emptyList()));
        DefaultRunner runner = new DefaultRunner(runnerId, runnableOperation, finalizer);

        // When
        runner.run();

        // Then
        verify(operation, times(1)).execute(args);
    }

    @Test
    @DisplayName("Should call finalizer after successful execution")
    void shouldCallFinalizerAfterSuccessfulExecution() {
        // Given
        when(operation.execute(args)).thenReturn(new AuditListResult(Collections.emptyList()));
        DefaultRunner runner = new DefaultRunner(runnerId, runnableOperation, finalizer);

        // When
        runner.run();

        // Then
        verify(finalizer, times(1)).run();
    }

    @Test
    @DisplayName("Should call finalizer even when operation throws exception")
    void shouldCallFinalizerEvenWhenOperationThrowsException() {
        // Given
        when(operation.execute(args)).thenThrow(new RuntimeException("Test exception"));
        DefaultRunner runner = new DefaultRunner(runnerId, runnableOperation, finalizer);

        // When & Then
        assertThrows(RuntimeException.class, runner::run);
        verify(finalizer, times(1)).run();
    }

    @Test
    @DisplayName("Should rethrow exception from operation")
    void shouldRethrowExceptionFromOperation() {
        // Given
        RuntimeException expectedException = new RuntimeException("Operation failed");
        when(operation.execute(args)).thenThrow(expectedException);
        DefaultRunner runner = new DefaultRunner(runnerId, runnableOperation, finalizer);

        // When & Then
        RuntimeException actualException = assertThrows(RuntimeException.class, runner::run);
        assertEquals("Operation failed", actualException.getMessage());
    }

    @Test
    @DisplayName("Should pass correct args to operation")
    void shouldPassCorrectArgsToOperation() {
        // Given
        when(operation.execute(any())).thenReturn(new AuditListResult(Collections.emptyList()));
        DefaultRunner runner = new DefaultRunner(runnerId, runnableOperation, finalizer);

        // When
        runner.run();

        // Then
        verify(operation).execute(args);
    }
}
