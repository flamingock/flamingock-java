/*
 * Copyright 2025 Flamingock (https://www.flamingock.io)
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
package io.flamingock.internal.core.task.executable;

import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.ChangeTemplate;
import io.flamingock.api.annotations.Rollback;
import io.flamingock.api.template.AbstractChangeTemplate;
import io.flamingock.api.template.TemplateStep;
import io.flamingock.internal.common.core.error.ChangeExecutionException;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.common.core.recovery.action.ChangeAction;
import io.flamingock.internal.core.runtime.ExecutionRuntime;
import io.flamingock.internal.core.task.loaded.SteppableTemplateLoadedChange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SteppableTemplateExecutableTaskTest {

    private ExecutionRuntime mockRuntime;
    private SteppableTemplateLoadedChange<Void, String, String> mockDescriptor;
    private Method applyMethod;
    private Method rollbackMethod;

    // Track payloads for verification
    private static List<String> appliedPayloads;
    private static List<String> rolledBackPayloads;
    private static boolean shouldFailOnApply;
    private static int failAtStep;
    private static boolean shouldFailOnRollback;

    /**
     * Test template that tracks apply and rollback invocations.
     */
    @ChangeTemplate(multiStep = true)
    public static class TestSteppableTemplate extends AbstractChangeTemplate<Void, String, String> {

        public TestSteppableTemplate() {
            super();
        }

        @Apply
        public void apply() {
            if (shouldFailOnApply && appliedPayloads.size() == failAtStep) {
                throw new RuntimeException("Simulated apply failure at step " + failAtStep);
            }
            appliedPayloads.add(applyPayload);
        }

        @Rollback
        public void rollback() {
            if (shouldFailOnRollback) {
                throw new RuntimeException("Simulated rollback failure");
            }
            rolledBackPayloads.add(rollbackPayload);
        }
    }

    /**
     * Test template without rollback method.
     */
    @ChangeTemplate(multiStep = true)
    public static class TestTemplateWithoutRollback extends AbstractChangeTemplate<Void, String, String> {

        public TestTemplateWithoutRollback() {
            super();
        }

        @Apply
        public void apply() {
            if (shouldFailOnApply && appliedPayloads.size() == failAtStep) {
                throw new RuntimeException("Simulated apply failure at step " + failAtStep);
            }
            appliedPayloads.add(applyPayload);
        }
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws NoSuchMethodException {
        // Reset static tracking lists
        appliedPayloads = new ArrayList<>();
        rolledBackPayloads = new ArrayList<>();
        shouldFailOnApply = false;
        failAtStep = -1;
        shouldFailOnRollback = false;

        mockRuntime = mock(ExecutionRuntime.class);
        mockDescriptor = mock(SteppableTemplateLoadedChange.class);

        when(mockDescriptor.getId()).thenReturn("test-change-id");
        when(mockDescriptor.isTransactional()).thenReturn(true);
        when(mockDescriptor.getConfiguration()).thenReturn(null);
        when(mockDescriptor.getTemplateClass()).thenReturn((Class) TestSteppableTemplate.class);

        applyMethod = TestSteppableTemplate.class.getMethod("apply");
        rollbackMethod = TestSteppableTemplate.class.getMethod("rollback");

        // Setup mock to return real template instance and invoke real methods
        Constructor<?> constructor = TestSteppableTemplate.class.getConstructor();
        doReturn(constructor).when(mockDescriptor).getConstructor();

        when(mockRuntime.getInstance(any(Constructor.class))).thenAnswer(invocation -> {
            Constructor<?> ctor = invocation.getArgument(0);
            return ctor.newInstance();
        });

        doAnswer((InvocationOnMock invocation) -> {
            Object instance = invocation.getArgument(0);
            Method method = invocation.getArgument(1);
            try {
                return method.invoke(instance);
            } catch (InvocationTargetException e) {
                // Mimic ExecutionRuntime behavior - wrap in FlamingockException
                throw FlamingockException.toFlamingockException(e);
            }
        }).when(mockRuntime).executeMethodWithInjectedDependencies(any(), any(Method.class));
    }

    @Test
    @DisplayName("Should apply all steps in sequence (step 0, 1, 2)")
    void shouldApplyAllStepsInOrder() {
        // Given
        List<TemplateStep<String, String>> steps = Arrays.asList(
                new TemplateStep<>("apply-0", "rollback-0"),
                new TemplateStep<>("apply-1", "rollback-1"),
                new TemplateStep<>("apply-2", "rollback-2")
        );
        when(mockDescriptor.getSteps()).thenReturn(steps);

        SteppableTemplateExecutableTask<Void, String, String> task = new SteppableTemplateExecutableTask<>(
                "test-stage",
                mockDescriptor,
                ChangeAction.APPLY,
                applyMethod,
                rollbackMethod
        );

        // When
        task.apply(mockRuntime);

        // Then
        assertEquals(3, appliedPayloads.size());
        assertEquals("apply-0", appliedPayloads.get(0));
        assertEquals("apply-1", appliedPayloads.get(1));
        assertEquals("apply-2", appliedPayloads.get(2));
    }

    @Test
    @DisplayName("Should rollback from failed step in reverse order")
    void shouldRollbackFromFailedStepInReverseOrder() {
        // Given
        shouldFailOnApply = true;
        failAtStep = 2; // Fail at step index 2 (third step)

        List<TemplateStep<String, String>> steps = Arrays.asList(
                new TemplateStep<>("apply-0", "rollback-0"),
                new TemplateStep<>("apply-1", "rollback-1"),
                new TemplateStep<>("apply-2", "rollback-2")
        );
        when(mockDescriptor.getSteps()).thenReturn(steps);

        SteppableTemplateExecutableTask<Void, String, String> task = new SteppableTemplateExecutableTask<>(
                "test-stage",
                mockDescriptor,
                ChangeAction.APPLY,
                applyMethod,
                rollbackMethod
        );

        // When - apply fails at step 2
        assertThrows(ChangeExecutionException.class, () -> task.apply(mockRuntime));

        // Then - verify steps 0 and 1 were applied (step 2 failed before completing)
        assertEquals(2, appliedPayloads.size());
        assertEquals("apply-0", appliedPayloads.get(0));
        assertEquals("apply-1", appliedPayloads.get(1));

        // When - rollback from stepIndex=2 (the failed step) down to 0
        task.rollback(mockRuntime);

        // Then - should rollback in reverse order (2, 1, 0)
        // Note: step 2's rollback is attempted even though apply failed, because stepIndex was incremented before apply
        assertEquals(3, rolledBackPayloads.size());
        assertEquals("rollback-2", rolledBackPayloads.get(0));
        assertEquals("rollback-1", rolledBackPayloads.get(1));
        assertEquals("rollback-0", rolledBackPayloads.get(2));
    }

    @Test
    @DisplayName("Should set correct apply payload for each step during apply")
    void shouldSetCorrectPayloadDuringApply() {
        // Given
        List<TemplateStep<String, String>> steps = Arrays.asList(
                new TemplateStep<>("payload-A", "rollback-A"),
                new TemplateStep<>("payload-B", "rollback-B")
        );
        when(mockDescriptor.getSteps()).thenReturn(steps);

        SteppableTemplateExecutableTask<Void, String, String> task = new SteppableTemplateExecutableTask<>(
                "test-stage",
                mockDescriptor,
                ChangeAction.APPLY,
                applyMethod,
                rollbackMethod
        );

        // When
        task.apply(mockRuntime);

        // Then - payloads should be set correctly for each step
        assertEquals(2, appliedPayloads.size());
        assertEquals("payload-A", appliedPayloads.get(0));
        assertEquals("payload-B", appliedPayloads.get(1));
    }

    @Test
    @DisplayName("Should set correct rollback payload for each step during rollback")
    void shouldSetCorrectPayloadDuringRollback() {
        // Given - simulate failure at step 2 so we can rollback steps 0, 1, and 2
        shouldFailOnApply = true;
        failAtStep = 2;

        List<TemplateStep<String, String>> steps = Arrays.asList(
                new TemplateStep<>("apply-0", "rollback-payload-X"),
                new TemplateStep<>("apply-1", "rollback-payload-Y"),
                new TemplateStep<>("apply-2", "rollback-payload-Z")
        );
        when(mockDescriptor.getSteps()).thenReturn(steps);

        SteppableTemplateExecutableTask<Void, String, String> task = new SteppableTemplateExecutableTask<>(
                "test-stage",
                mockDescriptor,
                ChangeAction.APPLY,
                applyMethod,
                rollbackMethod
        );

        // When - apply fails at step 2
        assertThrows(ChangeExecutionException.class, () -> task.apply(mockRuntime));
        task.rollback(mockRuntime);

        // Then - rollback payloads should be set correctly (reverse order: 2, 1, 0)
        // stepIndex is 2 when failure occurs, so rollback starts from step 2
        assertEquals(3, rolledBackPayloads.size());
        assertEquals("rollback-payload-Z", rolledBackPayloads.get(0)); // step 2
        assertEquals("rollback-payload-Y", rolledBackPayloads.get(1)); // step 1
        assertEquals("rollback-payload-X", rolledBackPayloads.get(2)); // step 0
    }

    @Test
    @DisplayName("Should skip steps without rollback payload during rollback")
    void shouldSkipStepsWithoutRollbackPayload() {
        // Given - step 1 has no rollback payload, and we have 4 steps to allow failAtStep=3
        shouldFailOnApply = true;
        failAtStep = 3; // Fail at step index 3 (fourth step)

        List<TemplateStep<String, String>> steps = Arrays.asList(
                new TemplateStep<>("apply-0", "rollback-0"),
                new TemplateStep<>("apply-1", null), // No rollback payload - should be skipped
                new TemplateStep<>("apply-2", "rollback-2"),
                new TemplateStep<>("apply-3", "rollback-3")
        );
        when(mockDescriptor.getSteps()).thenReturn(steps);

        SteppableTemplateExecutableTask<Void, String, String> task = new SteppableTemplateExecutableTask<>(
                "test-stage",
                mockDescriptor,
                ChangeAction.APPLY,
                applyMethod,
                rollbackMethod
        );

        // When - apply fails at step 3 (after steps 0, 1, 2 succeed)
        assertThrows(ChangeExecutionException.class, () -> task.apply(mockRuntime));

        // Verify 3 steps were applied
        assertEquals(3, appliedPayloads.size());

        task.rollback(mockRuntime);

        // Then - should rollback steps 3, 2, 0 (step 1 skipped due to null rollback payload)
        // stepIndex=3 when failure occurs, rollback: 3, 2, 1(skipped), 0
        assertEquals(3, rolledBackPayloads.size());
        assertEquals("rollback-3", rolledBackPayloads.get(0)); // step 3
        assertEquals("rollback-2", rolledBackPayloads.get(1)); // step 2
        assertEquals("rollback-0", rolledBackPayloads.get(2)); // step 0 (step 1 skipped)
    }

    @Test
    @DisplayName("Should handle empty steps list without error")
    void shouldHandleEmptyStepsList() {
        // Given
        List<TemplateStep<String, String>> emptySteps = Collections.emptyList();
        when(mockDescriptor.getSteps()).thenReturn(emptySteps);

        SteppableTemplateExecutableTask<Void, String, String> task = new SteppableTemplateExecutableTask<>(
                "test-stage",
                mockDescriptor,
                ChangeAction.APPLY,
                applyMethod,
                rollbackMethod
        );

        // When & Then - should not throw
        assertDoesNotThrow(() -> task.apply(mockRuntime));
        assertEquals(0, appliedPayloads.size());

        // Rollback should also work with empty list
        assertDoesNotThrow(() -> task.rollback(mockRuntime));
        assertEquals(0, rolledBackPayloads.size());
    }

    @Test
    @DisplayName("Should not rollback when rollbackMethod is null")
    @SuppressWarnings("unchecked")
    void shouldNotRollbackWhenRollbackMethodIsNull() throws Exception {
        // Given - use template without rollback method
        doReturn(TestTemplateWithoutRollback.class).when(mockDescriptor).getTemplateClass();
        Constructor<?> constructor = TestTemplateWithoutRollback.class.getConstructor();
        doReturn(constructor).when(mockDescriptor).getConstructor();

        Method applyOnlyMethod = TestTemplateWithoutRollback.class.getMethod("apply");

        shouldFailOnApply = true;
        failAtStep = 1;

        List<TemplateStep<String, String>> steps = Arrays.asList(
                new TemplateStep<>("apply-0", "rollback-0"),
                new TemplateStep<>("apply-1", "rollback-1")
        );
        when(mockDescriptor.getSteps()).thenReturn(steps);

        SteppableTemplateExecutableTask<Void, String, String> task = new SteppableTemplateExecutableTask<>(
                "test-stage",
                mockDescriptor,
                ChangeAction.APPLY,
                applyOnlyMethod,
                null // No rollback method
        );

        // When
        assertThrows(ChangeExecutionException.class, () -> task.apply(mockRuntime));
        task.rollback(mockRuntime);

        // Then - no rollback should have happened
        assertEquals(0, rolledBackPayloads.size());
    }

    @Test
    @DisplayName("Should throw ChangeExecutionException when apply fails")
    void shouldThrowChangeExecutionExceptionOnApplyFailure() {
        // Given
        shouldFailOnApply = true;
        failAtStep = 1;

        List<TemplateStep<String, String>> steps = Arrays.asList(
                new TemplateStep<>("apply-0", "rollback-0"),
                new TemplateStep<>("apply-1", "rollback-1")
        );
        when(mockDescriptor.getSteps()).thenReturn(steps);

        SteppableTemplateExecutableTask<Void, String, String> task = new SteppableTemplateExecutableTask<>(
                "test-stage",
                mockDescriptor,
                ChangeAction.APPLY,
                applyMethod,
                rollbackMethod
        );

        // When & Then
        ChangeExecutionException exception = assertThrows(
                ChangeExecutionException.class,
                () -> task.apply(mockRuntime)
        );

        assertEquals("test-change-id", exception.getChangeId());
        assertTrue(exception.getMessage().contains("Simulated apply failure"));
    }

    @Test
    @DisplayName("Should throw ChangeExecutionException when rollback fails")
    void shouldThrowChangeExecutionExceptionOnRollbackFailure() {
        // Given
        shouldFailOnApply = true;
        failAtStep = 1;
        shouldFailOnRollback = true;

        List<TemplateStep<String, String>> steps = Arrays.asList(
                new TemplateStep<>("apply-0", "rollback-0"),
                new TemplateStep<>("apply-1", "rollback-1")
        );
        when(mockDescriptor.getSteps()).thenReturn(steps);

        SteppableTemplateExecutableTask<Void, String, String> task = new SteppableTemplateExecutableTask<>(
                "test-stage",
                mockDescriptor,
                ChangeAction.APPLY,
                applyMethod,
                rollbackMethod
        );

        // When - apply fails
        assertThrows(ChangeExecutionException.class, () -> task.apply(mockRuntime));

        // When & Then - rollback also fails
        ChangeExecutionException exception = assertThrows(
                ChangeExecutionException.class,
                () -> task.rollback(mockRuntime)
        );

        assertEquals("test-change-id", exception.getChangeId());
        assertTrue(exception.getMessage().contains("Simulated rollback failure"));
    }

    @Test
    @DisplayName("Should maintain stepIndex across apply and rollback")
    void shouldMaintainStepIndexAcrossApplyAndRollback() {
        // Given - fail at step 2 to verify stepIndex state
        shouldFailOnApply = true;
        failAtStep = 2;

        List<TemplateStep<String, String>> steps = Arrays.asList(
                new TemplateStep<>("apply-0", "rollback-0"),
                new TemplateStep<>("apply-1", "rollback-1"),
                new TemplateStep<>("apply-2", "rollback-2"),
                new TemplateStep<>("apply-3", "rollback-3")
        );
        when(mockDescriptor.getSteps()).thenReturn(steps);

        SteppableTemplateExecutableTask<Void, String, String> task = new SteppableTemplateExecutableTask<>(
                "test-stage",
                mockDescriptor,
                ChangeAction.APPLY,
                applyMethod,
                rollbackMethod
        );

        // When - apply fails at step 2
        assertThrows(ChangeExecutionException.class, () -> task.apply(mockRuntime));

        // Verify - only steps 0 and 1 were applied (step 2 failed before completion)
        assertEquals(2, appliedPayloads.size());

        // When - rollback
        task.rollback(mockRuntime);

        // Then - steps 2, 1, 0 should be rolled back
        // stepIndex=2 when failure occurs (incremented before execute), so rollback starts from 2
        assertEquals(3, rolledBackPayloads.size());
        assertEquals("rollback-2", rolledBackPayloads.get(0));
        assertEquals("rollback-1", rolledBackPayloads.get(1));
        assertEquals("rollback-0", rolledBackPayloads.get(2));
    }
}
