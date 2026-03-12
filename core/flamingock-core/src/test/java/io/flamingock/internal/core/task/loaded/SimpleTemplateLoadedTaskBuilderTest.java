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
import io.flamingock.internal.common.core.error.validation.ValidationError;
import io.flamingock.internal.common.core.template.ChangeTemplateDefinition;
import io.flamingock.internal.common.core.template.ChangeTemplateManager;
import io.flamingock.api.annotations.ChangeTemplate;
import io.flamingock.api.annotations.RollbackTemplate;
import io.flamingock.api.template.AbstractChangeTemplate;
import io.flamingock.api.template.TemplatePayload;
import io.flamingock.api.template.TemplatePayloadInfo;
import io.flamingock.api.template.TemplatePayloadValidationError;
import io.flamingock.api.template.TemplateValidationContext;
import io.flamingock.api.template.wrappers.TemplateString;
import io.flamingock.api.template.wrappers.TemplateVoid;
import io.flamingock.api.annotations.ApplyTemplate;
import io.flamingock.internal.core.pipeline.loaded.stage.StageValidationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SimpleTemplateLoadedTaskBuilderTest {

    private TemplateLoadedTaskBuilder builder;

    // Simple test template implementation using the annotation
    @ChangeTemplate(name = "test-change-template")
    public static class TestChangeTemplate extends AbstractChangeTemplate<TemplateVoid, TemplateString, TemplateString> {

        public TestChangeTemplate() {
            super();
        }

        @ApplyTemplate
        public void apply(Object config, Object execution, Object context) {
            // Test implementation
        }

        @RollbackTemplate
        public void rollback() {
        }
    }

    // Payload that explicitly claims supportsTransactions=false
    public static class NonTxPayload implements TemplatePayload {
        private String value;

        public NonTxPayload() {}

        public NonTxPayload(String value) { this.value = value; }

        public String getValue() { return value; }

        public void setValue(String value) { this.value = value; }

        @Override
        public List<TemplatePayloadValidationError> validate(TemplateValidationContext context) {
            return Collections.emptyList();
        }

        @Override
        public TemplatePayloadInfo getInfo() {
            TemplatePayloadInfo info = new TemplatePayloadInfo();
            info.setSupportsTransactions(false);
            return info;
        }
    }

    // Payload that explicitly claims supportsTransactions=true
    public static class TxPayload implements TemplatePayload {
        private String value;

        public TxPayload() {}

        public TxPayload(String value) { this.value = value; }

        public String getValue() { return value; }

        public void setValue(String value) { this.value = value; }

        @Override
        public List<TemplatePayloadValidationError> validate(TemplateValidationContext context) {
            return Collections.emptyList();
        }

        @Override
        public TemplatePayloadInfo getInfo() {
            TemplatePayloadInfo info = new TemplatePayloadInfo();
            info.setSupportsTransactions(true);
            return info;
        }
    }

    @ChangeTemplate(name = "test-nontx-template")
    public static class NonTxTemplate extends AbstractChangeTemplate<TemplateVoid, NonTxPayload, TemplateString> {
        public NonTxTemplate() { super(); }

        @ApplyTemplate
        public void apply() {}

        @RollbackTemplate
        public void rollback() {}
    }

    @ChangeTemplate(name = "test-tx-template")
    public static class TxTemplate extends AbstractChangeTemplate<TemplateVoid, TxPayload, TemplateString> {
        public TxTemplate() { super(); }

        @ApplyTemplate
        public void apply() {}

        @RollbackTemplate
        public void rollback() {}
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
                    .thenReturn(Optional.of(new ChangeTemplateDefinition("test-change-template", TestChangeTemplate.class, false, true)));

            builder.setId("test-id")
                    .setOrder("001")
                    .setFileName("test-file.yml")
                    .setTemplateName("test-template")
                    .setRunAlways(false)
                    .setTransactionalFlag(true)
                    .setSystem(false)
                    .setConfiguration(null)
                    .setApplyPayload("applyPayload")
                    .setRollbackPayload("rollbackPayload");
            builder.setProfiles(Arrays.asList("test"));

            // When
            AbstractTemplateLoadedChange<?, ?, ?> result = builder.build();

            // Then
            assertInstanceOf(SimpleTemplateLoadedChange.class, result);
            assertEquals("001", result.getOrder().orElse(null));
            assertEquals("test-id", result.getId());
            assertEquals("test-file.yml", result.getFileName());
            // Verify typed payloads are stored
            SimpleTemplateLoadedChange<?, ?, ?> simpleResult = (SimpleTemplateLoadedChange<?, ?, ?>) result;
            assertNotNull(simpleResult.getApplyPayload());
            assertNotNull(simpleResult.getRollbackPayload());
            assertTrue(simpleResult.hasRollbackPayload());
        }
    }

    @Test
    @DisplayName("Should build with order from fileName when orderInContent is null and order in fileName is present")
    void shouldBuildWithOrderFromFileNameWhenOrderInContentIsNullAndOrderInFileNameIsPresent() {
        // Given
        try (MockedStatic<ChangeTemplateManager> mockedTemplateManager = mockStatic(ChangeTemplateManager.class)) {
            mockedTemplateManager.when(() -> ChangeTemplateManager.getTemplate("test-template"))
                    .thenReturn(Optional.of(new ChangeTemplateDefinition("test-change-template", TestChangeTemplate.class, false, true)));

            builder.setId("test-id")
                    .setOrder("0002")
                    .setFileName("_0002__test-file.yml")
                    .setTemplateName("test-template")
                    .setRunAlways(false)
                    .setTransactionalFlag(true)
                    .setSystem(false)
                    .setConfiguration(null)
                    .setApplyPayload("applyPayload")
                    .setRollbackPayload("rollbackPayload");
            builder.setProfiles(Arrays.asList("test"));

            // When
            AbstractTemplateLoadedChange<?, ?, ?> result = builder.build();

            // Then
            assertInstanceOf(SimpleTemplateLoadedChange.class, result);
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
                    .thenReturn(Optional.of(new ChangeTemplateDefinition("test-change-template", TestChangeTemplate.class, false, true)));

            builder.setId("test-id")
                    .setOrder("003")
                    .setFileName("_003__test-file.yml")
                    .setTemplateName("test-template")
                    .setRunAlways(false);
            builder.setProfiles(Arrays.asList("test"));
            builder.setTransactionalFlag(true)
                    .setSystem(false)
                    .setConfiguration(null)
                    .setApplyPayload("applyPayload")
                    .setRollbackPayload("rollbackPayload");

            // When
            AbstractTemplateLoadedChange<?, ?, ?> result = builder.build();

            // Then
            assertInstanceOf(SimpleTemplateLoadedChange.class, result);
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

    @Nested
    @DisplayName("Transactional nullable support tests")
    class TransactionalNullableTests {

        @Test
        @DisplayName("Should default to true when transactional is null")
        void shouldDefaultToTrueWhenTransactionalIsNull() {
            try (MockedStatic<ChangeTemplateManager> mockedTemplateManager = mockStatic(ChangeTemplateManager.class)) {
                mockedTemplateManager.when(() -> ChangeTemplateManager.getTemplate("test-template"))
                        .thenReturn(Optional.of(new ChangeTemplateDefinition("test-change-template", TestChangeTemplate.class, false, true)));

                builder.setId("test-id")
                        .setOrder("001")
                        .setFileName("test-file.yml")
                        .setTemplateName("test-template")
                        .setRunAlways(false)
                        .setTransactionalFlag(null)
                        .setSystem(false)
                        .setApplyPayload("applyPayload")
                        .setRollbackPayload("rollbackPayload");
                builder.setProfiles(Arrays.asList("test"));

                AbstractTemplateLoadedChange<?, ?, ?> result = builder.build();

                assertTrue(result.isTransactional());
            }
        }

        @Test
        @DisplayName("Should preserve explicit true when set")
        void shouldPreserveExplicitTrueWhenSet() {
            try (MockedStatic<ChangeTemplateManager> mockedTemplateManager = mockStatic(ChangeTemplateManager.class)) {
                mockedTemplateManager.when(() -> ChangeTemplateManager.getTemplate("test-template"))
                        .thenReturn(Optional.of(new ChangeTemplateDefinition("test-change-template", TestChangeTemplate.class, false, true)));

                builder.setId("test-id")
                        .setOrder("001")
                        .setFileName("test-file.yml")
                        .setTemplateName("test-template")
                        .setTransactionalFlag(true)
                        .setApplyPayload("applyPayload")
                        .setRollbackPayload("rollbackPayload");
                builder.setProfiles(Arrays.asList("test"));

                AbstractTemplateLoadedChange<?, ?, ?> result = builder.build();

                assertTrue(result.isTransactional());
            }
        }

        @Test
        @DisplayName("Should preserve explicit false when set")
        void shouldPreserveExplicitFalseWhenSet() {
            try (MockedStatic<ChangeTemplateManager> mockedTemplateManager = mockStatic(ChangeTemplateManager.class)) {
                mockedTemplateManager.when(() -> ChangeTemplateManager.getTemplate("test-template"))
                        .thenReturn(Optional.of(new ChangeTemplateDefinition("test-change-template", TestChangeTemplate.class, false, true)));

                builder.setId("test-id")
                        .setOrder("001")
                        .setFileName("test-file.yml")
                        .setTemplateName("test-template")
                        .setTransactionalFlag(false)
                        .setApplyPayload("applyPayload")
                        .setRollbackPayload("rollbackPayload");
                builder.setProfiles(Arrays.asList("test"));

                AbstractTemplateLoadedChange<?, ?, ?> result = builder.build();

                assertFalse(result.isTransactional());
            }
        }
    }

    @Nested
    @DisplayName("Payload validation tests")
    class ValidationTests {

        private final StageValidationContext validationContext = StageValidationContext.builder()
                .setSorted(StageValidationContext.SortType.UNSORTED)
                .build();

        @Test
        @DisplayName("Should report error when apply payload is null")
        void shouldReportErrorWhenApplyPayloadIsNull() {
            try (MockedStatic<ChangeTemplateManager> mockedTemplateManager = mockStatic(ChangeTemplateManager.class)) {
                mockedTemplateManager.when(() -> ChangeTemplateManager.getTemplate("test-template"))
                        .thenReturn(Optional.of(new ChangeTemplateDefinition("test-change-template", TestChangeTemplate.class, false, true)));

                builder.setId("test-id")
                        .setFileName("test-file.yml")
                        .setTemplateName("test-template")
                        .setTransactionalFlag(true)
                        .setApplyPayload(null)
                        .setRollbackPayload("rollbackPayload");
                builder.setProfiles(Arrays.asList("test"));

                AbstractTemplateLoadedChange<?, ?, ?> result = builder.build();
                List<ValidationError> errors = result.getValidationErrors(validationContext);

                assertTrue(errors.stream().anyMatch(e -> e.getMessage().contains("requires 'apply' payload")),
                        "Expected validation error about missing apply payload, but got: " + errors);
            }
        }

        @Test
        @DisplayName("Should report error when rollback payload is null and required")
        void shouldReportErrorWhenRollbackPayloadIsNullAndRequired() {
            try (MockedStatic<ChangeTemplateManager> mockedTemplateManager = mockStatic(ChangeTemplateManager.class)) {
                mockedTemplateManager.when(() -> ChangeTemplateManager.getTemplate("test-template"))
                        .thenReturn(Optional.of(new ChangeTemplateDefinition("test-change-template", TestChangeTemplate.class, false, true)));

                builder.setId("test-id")
                        .setFileName("test-file.yml")
                        .setTemplateName("test-template")
                        .setTransactionalFlag(true)
                        .setApplyPayload("applyPayload")
                        .setRollbackPayload(null);
                builder.setProfiles(Arrays.asList("test"));

                AbstractTemplateLoadedChange<?, ?, ?> result = builder.build();
                List<ValidationError> errors = result.getValidationErrors(validationContext);

                assertTrue(errors.stream().anyMatch(e -> e.getMessage().contains("requires 'rollback' payload")),
                        "Expected validation error about missing rollback payload, but got: " + errors);
            }
        }

        @Test
        @DisplayName("Should not report error when rollback payload is null and not required")
        void shouldNotReportErrorWhenRollbackPayloadIsNullAndNotRequired() {
            try (MockedStatic<ChangeTemplateManager> mockedTemplateManager = mockStatic(ChangeTemplateManager.class)) {
                mockedTemplateManager.when(() -> ChangeTemplateManager.getTemplate("test-template"))
                        .thenReturn(Optional.of(new ChangeTemplateDefinition("test-change-template", TestChangeTemplate.class, false, false)));

                builder.setId("test-id")
                        .setFileName("test-file.yml")
                        .setTemplateName("test-template")
                        .setTransactionalFlag(true)
                        .setApplyPayload("applyPayload")
                        .setRollbackPayload(null);
                builder.setProfiles(Arrays.asList("test"));

                AbstractTemplateLoadedChange<?, ?, ?> result = builder.build();
                List<ValidationError> errors = result.getValidationErrors(validationContext);

                assertFalse(errors.stream().anyMatch(e -> e.getMessage().contains("rollback")),
                        "Expected no rollback-related errors, but got: " + errors);
            }
        }
    }

    @Nested
    @DisplayName("Transactional inference from payload tests")
    class TransactionalInferenceTests {

        @Test
        @DisplayName("Should infer false when payload does not support transactions")
        void shouldInferFalseWhenPayloadDoesNotSupportTx() {
            try (MockedStatic<ChangeTemplateManager> mockedTemplateManager = mockStatic(ChangeTemplateManager.class)) {
                mockedTemplateManager.when(() -> ChangeTemplateManager.getTemplate("test-nontx-template"))
                        .thenReturn(Optional.of(new ChangeTemplateDefinition("test-nontx-template", NonTxTemplate.class, false, false)));

                builder.setId("test-id")
                        .setFileName("test-file.yml")
                        .setTemplateName("test-nontx-template")
                        .setTransactionalFlag(null)
                        .setApplyPayload("applyPayload");
                builder.setProfiles(Arrays.asList("test"));

                AbstractTemplateLoadedChange<?, ?, ?> result = builder.build();

                assertFalse(result.isTransactional());
            }
        }

        @Test
        @DisplayName("Should infer true when payload supports transactions")
        void shouldInferTrueWhenPayloadSupportsTx() {
            try (MockedStatic<ChangeTemplateManager> mockedTemplateManager = mockStatic(ChangeTemplateManager.class)) {
                mockedTemplateManager.when(() -> ChangeTemplateManager.getTemplate("test-tx-template"))
                        .thenReturn(Optional.of(new ChangeTemplateDefinition("test-tx-template", TxTemplate.class, false, false)));

                builder.setId("test-id")
                        .setFileName("test-file.yml")
                        .setTemplateName("test-tx-template")
                        .setTransactionalFlag(null)
                        .setApplyPayload("applyPayload");
                builder.setProfiles(Arrays.asList("test"));

                AbstractTemplateLoadedChange<?, ?, ?> result = builder.build();

                assertTrue(result.isTransactional());
            }
        }

        @Test
        @DisplayName("Should infer true when payload makes no claim")
        void shouldInferTrueWhenPayloadMakesNoClaim() {
            try (MockedStatic<ChangeTemplateManager> mockedTemplateManager = mockStatic(ChangeTemplateManager.class)) {
                mockedTemplateManager.when(() -> ChangeTemplateManager.getTemplate("test-template"))
                        .thenReturn(Optional.of(new ChangeTemplateDefinition("test-change-template", TestChangeTemplate.class, false, false)));

                builder.setId("test-id")
                        .setFileName("test-file.yml")
                        .setTemplateName("test-template")
                        .setTransactionalFlag(null)
                        .setApplyPayload("applyPayload");
                builder.setProfiles(Arrays.asList("test"));

                AbstractTemplateLoadedChange<?, ?, ?> result = builder.build();

                assertTrue(result.isTransactional());
            }
        }

        @Test
        @DisplayName("Should infer true when apply payload is null")
        void shouldInferTrueWhenApplyPayloadIsNull() {
            try (MockedStatic<ChangeTemplateManager> mockedTemplateManager = mockStatic(ChangeTemplateManager.class)) {
                mockedTemplateManager.when(() -> ChangeTemplateManager.getTemplate("test-template"))
                        .thenReturn(Optional.of(new ChangeTemplateDefinition("test-change-template", TestChangeTemplate.class, false, false)));

                builder.setId("test-id")
                        .setFileName("test-file.yml")
                        .setTemplateName("test-template")
                        .setTransactionalFlag(null)
                        .setApplyPayload(null);
                builder.setProfiles(Arrays.asList("test"));

                AbstractTemplateLoadedChange<?, ?, ?> result = builder.build();

                assertTrue(result.isTransactional());
            }
        }

        @Test
        @DisplayName("Should respect explicit true even when payload does not support transactions")
        void shouldRespectExplicitTrueEvenWhenPayloadDoesNotSupportTx() {
            try (MockedStatic<ChangeTemplateManager> mockedTemplateManager = mockStatic(ChangeTemplateManager.class)) {
                mockedTemplateManager.when(() -> ChangeTemplateManager.getTemplate("test-nontx-template"))
                        .thenReturn(Optional.of(new ChangeTemplateDefinition("test-nontx-template", NonTxTemplate.class, false, false)));

                builder.setId("test-id")
                        .setFileName("test-file.yml")
                        .setTemplateName("test-nontx-template")
                        .setTransactionalFlag(true)
                        .setApplyPayload("applyPayload");
                builder.setProfiles(Arrays.asList("test"));

                AbstractTemplateLoadedChange<?, ?, ?> result = builder.build();

                assertTrue(result.isTransactional());
            }
        }

        @Test
        @DisplayName("Should respect explicit false even when payload supports transactions")
        void shouldRespectExplicitFalseEvenWhenPayloadSupportsTx() {
            try (MockedStatic<ChangeTemplateManager> mockedTemplateManager = mockStatic(ChangeTemplateManager.class)) {
                mockedTemplateManager.when(() -> ChangeTemplateManager.getTemplate("test-tx-template"))
                        .thenReturn(Optional.of(new ChangeTemplateDefinition("test-tx-template", TxTemplate.class, false, false)));

                builder.setId("test-id")
                        .setFileName("test-file.yml")
                        .setTemplateName("test-tx-template")
                        .setTransactionalFlag(false)
                        .setApplyPayload("applyPayload");
                builder.setProfiles(Arrays.asList("test"));

                AbstractTemplateLoadedChange<?, ?, ?> result = builder.build();

                assertFalse(result.isTransactional());
            }
        }
    }
}
