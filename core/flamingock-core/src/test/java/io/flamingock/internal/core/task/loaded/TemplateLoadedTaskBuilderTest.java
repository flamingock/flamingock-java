package io.flamingock.internal.core.task.loaded;

import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.common.core.template.ChangeTemplateManager;
import io.flamingock.api.template.ChangeTemplate;
import io.flamingock.api.annotations.Execution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TemplateLoadedTaskBuilderTest {

    private TemplateLoadedTaskBuilder builder;

    // Simple test template implementation
    public static class TestChangeTemplate implements ChangeTemplate<Object, Object, Object> {
        
        @Override
        public void setChangeId(String changeId) {}
        
        @Override
        public void setTransactional(boolean isTransactional) {}
        
        @Override
        public void setConfiguration(Object configuration) {}
        
        @Override
        public void setExecution(Object execution) {}
        
        @Override
        public void setRollback(Object rollback) {}
        
        @Override
        public Class<Object> getConfigurationClass() { return Object.class; }
        
        @Override
        public Class<Object> getExecutionClass() { return Object.class; }
        
        @Override
        public Class<Object> getRollbackClass() { return Object.class; }
        
        @Override
        public Collection<Class<?>> getReflectiveClasses() { return Collections.emptyList(); }
        
        @Execution
        public void execute(Object config, Object execution, Object context) {
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
                    .setOrderInContent("001")
                    .setFileName("test-file.yml")
                    .setTemplateName("test-template")
                    .setRunAlways(false)
                    .setTransactional(true)
                    .setSystem(false)
                    .setConfiguration(new Object())
                    .setExecution(new Object())
                    .setRollback(new Object());
            builder.setProfiles(Arrays.asList("test"));

            // When
            TemplateLoadedChangeUnit result = builder.build();

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
                    .setOrderInContent(null)
                    .setFileName("_002_test-file.yml")
                    .setTemplateName("test-template")
                    .setRunAlways(false)
                    .setTransactional(true)
                    .setSystem(false)
                    .setConfiguration(new Object())
                    .setExecution(new Object())
                    .setRollback(new Object());
            builder.setProfiles(Arrays.asList("test"));

            // When
            TemplateLoadedChangeUnit result = builder.build();

            // Then
            assertEquals("002", result.getOrder().orElse(null));
            assertEquals("test-id", result.getId());
            assertEquals("_002_test-file.yml", result.getFileName());
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
                    .setOrderInContent("003")
                    .setFileName("_003_test-file.yml")
                    .setTemplateName("test-template")
                    .setRunAlways(false);
            builder.setProfiles(Arrays.asList("test"));
            builder.setTransactional(true)
                    .setSystem(false)
                    .setConfiguration(new Object())
                    .setExecution(new Object())
                    .setRollback(new Object());

            // When
            TemplateLoadedChangeUnit result = builder.build();

            // Then
            assertEquals("003", result.getOrder().orElse(null));
            assertEquals("test-id", result.getId());
            assertEquals("_003_test-file.yml", result.getFileName());
        }
    }

    @Test
    @DisplayName("Should throw exception when orderInContent does not match order in fileName")
    void shouldThrowExceptionWhenOrderInContentDoesNotMatchOrderInFileName() {
        // Given
        try (MockedStatic<ChangeTemplateManager> mockedTemplateManager = mockStatic(ChangeTemplateManager.class)) {
            mockedTemplateManager.when(() -> ChangeTemplateManager.getTemplate("test-template"))
                    .thenReturn(Optional.of(TestChangeTemplate.class));

            builder.setId("test-id")
                    .setOrderInContent("001")
                    .setFileName("_002_test-file.yml")
                    .setTemplateName("test-template")
                    .setRunAlways(false);
            builder.setProfiles(Collections.singletonList("test"));
            builder.setTransactional(true)
                    .setSystem(false)
                    .setConfiguration(new Object())
                    .setExecution(new Object())
                    .setRollback(new Object());

            // When & Then
            FlamingockException exception = assertThrows(FlamingockException.class, () -> builder.build());


            assertEquals("ChangeUnit[test-id] Order mismatch: value in template order field='001' does not match order in fileName='002'",
                    exception.getMessage());
        }
    }

    @Test
    @DisplayName("Should throw exception when both orderInContent and order in fileName are missing")
    void shouldThrowExceptionWhenBothOrderInContentAndOrderInFileNameAreMissing() {
        // Given
        try (MockedStatic<ChangeTemplateManager> mockedTemplateManager = mockStatic(ChangeTemplateManager.class)) {
            mockedTemplateManager.when(() -> ChangeTemplateManager.getTemplate("test-template"))
                    .thenReturn(Optional.of(TestChangeTemplate.class));

            builder.setId("test-id")
                    .setOrderInContent(null)
                    .setFileName("test-file.yml")
                    .setTemplateName("test-template")
                    .setRunAlways(false);
            builder.setProfiles(Arrays.asList("test"));
            builder.setTransactional(true)
                    .setSystem(false)
                    .setConfiguration(new Object())
                    .setExecution(new Object())
                    .setRollback(new Object());

            // When & Then
            FlamingockException exception = assertThrows(FlamingockException.class, () -> builder.build());

            assertEquals("ChangeUnit[test-id] Order is required: order must be present in the template order field or in the fileName(e.g. _0001_test-id.yaml). If present in both, they must have the same value.",
                    exception.getMessage());
        }
    }

    @Test
    @DisplayName("Should build with order from fileName when orderInContent is empty string")
    void shouldBuildWithOrderFromFileNameWhenOrderInContentIsEmptyString() {
        // Given
        try (MockedStatic<ChangeTemplateManager> mockedTemplateManager = mockStatic(ChangeTemplateManager.class)) {
            mockedTemplateManager.when(() -> ChangeTemplateManager.getTemplate("test-template"))
                    .thenReturn(Optional.of(TestChangeTemplate.class));

            builder.setId("test-id")
                    .setOrderInContent("")
                    .setFileName("_004_test-file.yml")
                    .setTemplateName("test-template")
                    .setRunAlways(false);
            builder.setProfiles(Arrays.asList("test"));
            builder.setTransactional(true)
                    .setSystem(false)
                    .setConfiguration(new Object())
                    .setExecution(new Object())
                    .setRollback(new Object());

            // When
            TemplateLoadedChangeUnit result = builder.build();

            // Then
            assertEquals("004", result.getOrder().orElse(null));
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
                    .setOrderInContent("001")
                    .setFileName("test-file.yml")
                    .setTemplateName("unknown-template");

            // When & Then
            FlamingockException exception = assertThrows(FlamingockException.class, () -> builder.build());

            assertTrue(exception.getMessage().contains("Template[unknown-template] not found"));
        }
    }
}