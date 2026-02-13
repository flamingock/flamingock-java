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
package io.flamingock.internal.core.task.loaded;

import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.ChangeTemplate;
import io.flamingock.api.template.AbstractChangeTemplate;
import io.flamingock.api.template.TemplateStep;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.common.core.template.ChangeTemplateManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SteppableTemplateLoadedTaskBuilderTest {

    private TemplateLoadedTaskBuilder builder;

    // Steppable test template implementation using the annotation
    @ChangeTemplate(multiStep = true)
    public static class TestSteppableTemplate extends AbstractChangeTemplate<Object, Object, Object> {

        public TestSteppableTemplate() {
            super();
        }

        @Apply
        public void apply() {
            // Test implementation - iterates through steps
        }
    }

    @BeforeEach
    void setUp() {
        builder = TemplateLoadedTaskBuilder.getInstance();
    }

    @Test
    @DisplayName("Should build with steps when steps are provided")
    void shouldBuildWithStepsWhenStepsProvided() {
        // Given
        try (MockedStatic<ChangeTemplateManager> mockedTemplateManager = mockStatic(ChangeTemplateManager.class)) {
            mockedTemplateManager.when(() -> ChangeTemplateManager.getTemplate("test-steppable-template"))
                    .thenReturn(Optional.of(TestSteppableTemplate.class));

            List<Map<String, Object>> rawSteps = Arrays.asList(
                    createStepMap("apply1", "rollback1"),
                    createStepMap("apply2", "rollback2")
            );

            builder.setId("test-id")
                    .setFileName("test-file.yml")
                    .setTemplateName("test-steppable-template")
                    .setTransactional(true)
                    .setSteps(rawSteps);
            builder.setProfiles(Arrays.asList("test"));

            // When
            AbstractTemplateLoadedChange<?, ?, ?> result = builder.build();

            // Then
            assertInstanceOf(SteppableTemplateLoadedChange.class, result);
            SteppableTemplateLoadedChange<?, ?, ?> steppableResult = (SteppableTemplateLoadedChange<?, ?, ?>) result;
            assertNotNull(result);
            assertEquals("test-id", result.getId());
            assertEquals("test-file.yml", result.getFileName());
            // Steps are now converted to List<TemplateStep> at load time
            List<? extends TemplateStep<?, ?>> steps = steppableResult.getSteps();
            assertNotNull(steps);
            assertEquals(2, steps.size());
            assertInstanceOf(TemplateStep.class, steps.get(0));
        }
    }

    @Test
    @DisplayName("Should build with orderInContent when orderInContent is present for steppable template")
    void shouldBuildWithOrderInContentForSteppableTemplate() {
        // Given
        try (MockedStatic<ChangeTemplateManager> mockedTemplateManager = mockStatic(ChangeTemplateManager.class)) {
            mockedTemplateManager.when(() -> ChangeTemplateManager.getTemplate("test-steppable-template"))
                    .thenReturn(Optional.of(TestSteppableTemplate.class));

            List<Map<String, Object>> rawSteps = Collections.singletonList(
                    createStepMap("apply1", "rollback1")
            );

            builder.setId("test-id")
                    .setOrder("001")
                    .setFileName("test-file.yml")
                    .setTemplateName("test-steppable-template")
                    .setRunAlways(false)
                    .setTransactional(true)
                    .setSystem(false)
                    .setConfiguration(new Object())
                    .setSteps(rawSteps);
            builder.setProfiles(Arrays.asList("test"));

            // When
            AbstractTemplateLoadedChange<?, ?, ?> result = builder.build();

            // Then
            assertInstanceOf(SteppableTemplateLoadedChange.class, result);
            assertEquals("001", result.getOrder().orElse(null));
            assertEquals("test-id", result.getId());
            assertEquals("test-file.yml", result.getFileName());
        }
    }

    @Test
    @DisplayName("Should build with order from fileName for steppable template")
    void shouldBuildWithOrderFromFileNameForSteppableTemplate() {
        // Given
        try (MockedStatic<ChangeTemplateManager> mockedTemplateManager = mockStatic(ChangeTemplateManager.class)) {
            mockedTemplateManager.when(() -> ChangeTemplateManager.getTemplate("test-steppable-template"))
                    .thenReturn(Optional.of(TestSteppableTemplate.class));

            List<Map<String, Object>> rawSteps = Collections.singletonList(
                    createStepMap("apply1", "rollback1")
            );

            builder.setId("test-id")
                    .setOrder("0002")
                    .setFileName("_0002__test-file.yml")
                    .setTemplateName("test-steppable-template")
                    .setRunAlways(false)
                    .setTransactional(true)
                    .setSystem(false)
                    .setConfiguration(new Object())
                    .setSteps(rawSteps);
            builder.setProfiles(Arrays.asList("test"));

            // When
            AbstractTemplateLoadedChange<?, ?, ?> result = builder.build();

            // Then
            assertInstanceOf(SteppableTemplateLoadedChange.class, result);
            assertEquals("0002", result.getOrder().orElse(null));
            assertEquals("test-id", result.getId());
            assertEquals("_0002__test-file.yml", result.getFileName());
        }
    }

    @Test
    @DisplayName("Should build with empty steps list")
    void shouldBuildWithEmptyStepsList() {
        // Given
        try (MockedStatic<ChangeTemplateManager> mockedTemplateManager = mockStatic(ChangeTemplateManager.class)) {
            mockedTemplateManager.when(() -> ChangeTemplateManager.getTemplate("test-steppable-template"))
                    .thenReturn(Optional.of(TestSteppableTemplate.class));

            List<Map<String, Object>> emptySteps = Collections.emptyList();

            builder.setId("test-id")
                    .setFileName("test-file.yml")
                    .setTemplateName("test-steppable-template")
                    .setTransactional(true)
                    .setSteps(emptySteps);
            builder.setProfiles(Arrays.asList("test"));

            // When
            AbstractTemplateLoadedChange<?, ?, ?> result = builder.build();

            // Then
            assertInstanceOf(SteppableTemplateLoadedChange.class, result);
            SteppableTemplateLoadedChange<?, ?, ?> steppableResult = (SteppableTemplateLoadedChange<?, ?, ?>) result;
            assertNotNull(result);
            assertEquals("test-id", result.getId());
            List<? extends TemplateStep<?, ?>> steps = steppableResult.getSteps();
            assertNotNull(steps);
            assertTrue(steps.isEmpty());
        }
    }

    @Test
    @DisplayName("Should build with multiple steps preserving order")
    void shouldBuildWithMultipleSteps() {
        // Given
        try (MockedStatic<ChangeTemplateManager> mockedTemplateManager = mockStatic(ChangeTemplateManager.class)) {
            mockedTemplateManager.when(() -> ChangeTemplateManager.getTemplate("test-steppable-template"))
                    .thenReturn(Optional.of(TestSteppableTemplate.class));

            List<Map<String, Object>> rawSteps = Arrays.asList(
                    createStepMap("createCollection", "dropCollection"),
                    createStepMap("insertDocument", "deleteDocument"),
                    createStepMap("createIndex", "dropIndex")
            );

            builder.setId("multi-step-change")
                    .setOrder("003")
                    .setFileName("_003__multi-step.yml")
                    .setTemplateName("test-steppable-template")
                    .setTransactional(true)
                    .setSteps(rawSteps);
            builder.setProfiles(Arrays.asList("test"));

            // When
            AbstractTemplateLoadedChange<?, ?, ?> result = builder.build();

            // Then
            assertInstanceOf(SteppableTemplateLoadedChange.class, result);
            SteppableTemplateLoadedChange<?, ?, ?> steppableResult = (SteppableTemplateLoadedChange<?, ?, ?>) result;
            assertNotNull(result);
            assertEquals("multi-step-change", result.getId());
            // Steps are now converted to List<TemplateStep> at load time
            List<? extends TemplateStep<?, ?>> steps = steppableResult.getSteps();
            assertNotNull(steps);
            assertEquals(3, steps.size());
            // Verify steps are preserved in order - payloads are now typed objects
            assertEquals("createCollection", steps.get(0).getApplyPayload());
            assertEquals("insertDocument", steps.get(1).getApplyPayload());
            assertEquals("createIndex", steps.get(2).getApplyPayload());
        }
    }

    @Test
    @DisplayName("Should throw exception when steppable template is not found")
    void shouldThrowExceptionWhenSteppableTemplateNotFound() {
        // Given
        try (MockedStatic<ChangeTemplateManager> mockedTemplateManager = mockStatic(ChangeTemplateManager.class)) {
            mockedTemplateManager.when(() -> ChangeTemplateManager.getTemplate("unknown-steppable-template"))
                    .thenReturn(Optional.empty());

            List<Map<String, Object>> rawSteps = Collections.singletonList(
                    createStepMap("apply1", "rollback1")
            );

            builder.setId("test-id")
                    .setOrder("001")
                    .setFileName("test-file.yml")
                    .setTemplateName("unknown-steppable-template")
                    .setSteps(rawSteps);

            // When & Then
            FlamingockException exception = assertThrows(FlamingockException.class, () -> builder.build());

            assertTrue(exception.getMessage().contains("Template[unknown-steppable-template] not found"));
        }
    }

    @Test
    @DisplayName("Should build with steps having only apply (no rollback)")
    void shouldBuildWithStepsHavingOnlyApply() {
        // Given
        try (MockedStatic<ChangeTemplateManager> mockedTemplateManager = mockStatic(ChangeTemplateManager.class)) {
            mockedTemplateManager.when(() -> ChangeTemplateManager.getTemplate("test-steppable-template"))
                    .thenReturn(Optional.of(TestSteppableTemplate.class));

            List<Map<String, Object>> rawSteps = Arrays.asList(
                    createStepMapApplyOnly("apply1"),
                    createStepMapApplyOnly("apply2")
            );

            builder.setId("test-id")
                    .setFileName("test-file.yml")
                    .setTemplateName("test-steppable-template")
                    .setTransactional(true)
                    .setSteps(rawSteps);
            builder.setProfiles(Arrays.asList("test"));

            // When
            AbstractTemplateLoadedChange<?, ?, ?> result = builder.build();

            // Then
            assertInstanceOf(SteppableTemplateLoadedChange.class, result);
            SteppableTemplateLoadedChange<?, ?, ?> steppableResult = (SteppableTemplateLoadedChange<?, ?, ?>) result;
            assertNotNull(result);
            // Steps are now converted to List<TemplateStep> at load time
            List<? extends TemplateStep<?, ?>> steps = steppableResult.getSteps();
            assertEquals(2, steps.size());
            assertNull(steps.get(0).getRollbackPayload());
            assertNull(steps.get(1).getRollbackPayload());
        }
    }

    @Test
    @DisplayName("Should build steppable template with null steps")
    void shouldBuildWithNullSteps() {
        // Given
        try (MockedStatic<ChangeTemplateManager> mockedTemplateManager = mockStatic(ChangeTemplateManager.class)) {
            mockedTemplateManager.when(() -> ChangeTemplateManager.getTemplate("test-steppable-template"))
                    .thenReturn(Optional.of(TestSteppableTemplate.class));

            builder.setId("test-id")
                    .setFileName("test-file.yml")
                    .setTemplateName("test-steppable-template")
                    .setTransactional(true)
                    .setSteps(null);
            builder.setProfiles(Arrays.asList("test"));

            // When
            AbstractTemplateLoadedChange<?, ?, ?> result = builder.build();

            // Then
            assertInstanceOf(SteppableTemplateLoadedChange.class, result);
            SteppableTemplateLoadedChange<?, ?, ?> steppableResult = (SteppableTemplateLoadedChange<?, ?, ?>) result;
            assertNotNull(result);
            assertEquals("test-id", result.getId());
            // Null steps remain null after conversion
            assertNull(steppableResult.getSteps());
        }
    }

    private Map<String, Object> createStepMap(Object apply, Object rollback) {
        Map<String, Object> step = new HashMap<>();
        step.put("apply", apply);
        step.put("rollback", rollback);
        return step;
    }

    private Map<String, Object> createStepMapApplyOnly(Object apply) {
        Map<String, Object> step = new HashMap<>();
        step.put("apply", apply);
        return step;
    }
}
