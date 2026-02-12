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

import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.common.core.template.ChangeTemplateManager;
import io.flamingock.api.template.AbstractSimpleTemplate;
import io.flamingock.api.annotations.Apply;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SimpleTemplateLoadedTaskBuilderTest {

    private TemplateLoadedTaskBuilder builder;

    // Simple test template implementation using the abstract class
    public static class TestChangeTemplate extends AbstractSimpleTemplate<Object, Object, Object> {

        public TestChangeTemplate() {
            super();
        }

        @Apply
        public void apply(Object config, Object execution, Object context) {
            // Test implementation
        }
    }

    @BeforeEach
    void setUp() {
        builder = TemplateLoadedTaskBuilder.getInstance();
    }

    @Test
    @DisplayName("Should build with orderInContent when orderInContent is present and no order in fileName")
    void shouldBuildWithOrderInContentWhenOrderInContentPresentAndNoOrderInFileName() {
        // Given
        try (MockedStatic<ChangeTemplateManager> mockedTemplateManager = mockStatic(ChangeTemplateManager.class)) {
            mockedTemplateManager.when(() -> ChangeTemplateManager.getTemplate("test-template"))
                    .thenReturn(Optional.of(TestChangeTemplate.class));

            builder.setId("test-id")
                    .setOrder("001")
                    .setFileName("test-file.yml")
                    .setTemplateName("test-template")
                    .setRunAlways(false)
                    .setTransactional(true)
                    .setSystem(false)
                    .setConfiguration(new Object())
                    .setApply(new Object())
                    .setRollback(new Object());
            builder.setProfiles(Arrays.asList("test"));

            // When
            TemplateLoadedChange result = builder.build();

            // Then
            assertEquals("001", result.getOrder().orElse(null));
            assertEquals("test-id", result.getId());
            assertEquals("test-file.yml", result.getFileName());
        }
    }

    @Test
    @DisplayName("Should build with order from fileName when orderInContent is null and order in fileName is present")
    void shouldBuildWithOrderFromFileNameWhenOrderInContentIsNullAndOrderInFileNameIsPresent() {
        // Given
        try (MockedStatic<ChangeTemplateManager> mockedTemplateManager = mockStatic(ChangeTemplateManager.class)) {
            mockedTemplateManager.when(() -> ChangeTemplateManager.getTemplate("test-template"))
                    .thenReturn(Optional.of(TestChangeTemplate.class));

            builder.setId("test-id")
                    .setOrder("0002")
                    .setFileName("_0002__test-file.yml")
                    .setTemplateName("test-template")
                    .setRunAlways(false)
                    .setTransactional(true)
                    .setSystem(false)
                    .setConfiguration(new Object())
                    .setApply(new Object())
                    .setRollback(new Object());
            builder.setProfiles(Arrays.asList("test"));

            // When
            TemplateLoadedChange result = builder.build();

            // Then
            assertEquals("0002", result.getOrder().orElse(null));
            assertEquals("test-id", result.getId());
            assertEquals("_0002__test-file.yml", result.getFileName());
        }
    }

    @Test
    @DisplayName("Should build with orderInContent when orderInContent matches order in fileName")
    void shouldBuildWithOrderInContentWhenOrderInContentMatchesOrderInFileName() {
        // Given
        try (MockedStatic<ChangeTemplateManager> mockedTemplateManager = mockStatic(ChangeTemplateManager.class)) {
            mockedTemplateManager.when(() -> ChangeTemplateManager.getTemplate("test-template"))
                    .thenReturn(Optional.of(TestChangeTemplate.class));

            builder.setId("test-id")
                    .setOrder("003")
                    .setFileName("_003__test-file.yml")
                    .setTemplateName("test-template")
                    .setRunAlways(false);
            builder.setProfiles(Arrays.asList("test"));
            builder.setTransactional(true)
                    .setSystem(false)
                    .setConfiguration(new Object())
                    .setApply(new Object())
                    .setRollback(new Object());

            // When
            TemplateLoadedChange result = builder.build();

            // Then
            assertEquals("003", result.getOrder().orElse(null));
            assertEquals("test-id", result.getId());
            assertEquals("_003__test-file.yml", result.getFileName());
        }
    }

    @Test
    @DisplayName("Should throw exception when template is not found")
    void shouldThrowExceptionWhenTemplateIsNotFound() {
        // Given
        try (MockedStatic<ChangeTemplateManager> mockedTemplateManager = mockStatic(ChangeTemplateManager.class)) {
            mockedTemplateManager.when(() -> ChangeTemplateManager.getTemplate("unknown-template"))
                    .thenReturn(Optional.empty());

            builder.setId("test-id")
                    .setOrder("001")
                    .setFileName("test-file.yml")
                    .setTemplateName("unknown-template");

            // When & Then
            FlamingockException exception = assertThrows(FlamingockException.class, () -> builder.build());

            assertTrue(exception.getMessage().contains("Template[unknown-template] not found"));
        }
    }
}
