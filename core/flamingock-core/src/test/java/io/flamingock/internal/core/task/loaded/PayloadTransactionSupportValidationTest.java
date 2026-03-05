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
package io.flamingock.internal.core.task.loaded;

import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.Rollback;
import io.flamingock.api.template.AbstractChangeTemplate;
import io.flamingock.api.template.TemplatePayload;
import io.flamingock.api.template.TemplatePayloadInfo;
import io.flamingock.api.template.TemplatePayloadValidationError;
import io.flamingock.api.template.TemplateStep;
import io.flamingock.api.template.TemplateValidationContext;
import io.flamingock.api.template.wrappers.TemplateString;
import io.flamingock.api.template.wrappers.TemplateVoid;
import io.flamingock.internal.common.core.error.validation.ValidationError;
import io.flamingock.internal.core.pipeline.loaded.stage.StageValidationContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Payload transaction support validation")
class PayloadTransactionSupportValidationTest {

    // ── Helper payload classes ──────────────────────────────────────────

    static class NonTransactionalPayload implements TemplatePayload {
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

    static class TransactionalPayload implements TemplatePayload {
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

    static class UnclaimedPayload implements TemplatePayload {
        @Override
        public List<TemplatePayloadValidationError> validate(TemplateValidationContext context) {
            return Collections.emptyList();
        }

        @Override
        public TemplatePayloadInfo getInfo() {
            return new TemplatePayloadInfo();
        }
    }

    // ── Dummy template (never instantiated — only .class is used) ───────

    static class DummyTemplate extends AbstractChangeTemplate<TemplateVoid, TemplateString, TemplateString> {
        @Apply
        public void apply() {
        }

        @Rollback
        public void rollback() {
        }
    }

    // ── Shared fixtures ─────────────────────────────────────────────────

    private static final StageValidationContext VALIDATION_CONTEXT = StageValidationContext.builder()
            .setSorted(StageValidationContext.SortType.UNSORTED)
            .build();

    private static Constructor<?> dummyConstructor;

    @BeforeAll
    static void setUp() {
        dummyConstructor = DummyTemplate.class.getDeclaredConstructors()[0];
    }

    // ── Factory helpers (raw types to bypass generics mismatch) ─────────

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static SimpleTemplateLoadedChange buildSimple(boolean transactional,
                                                          TemplatePayload config,
                                                          TemplatePayload apply,
                                                          TemplatePayload rollback) {
        return new SimpleTemplateLoadedChange(
                "test-file.yml", "test-id", "001", "author",
                (Class) DummyTemplate.class, dummyConstructor,
                Collections.emptyList(), transactional,
                false, false,
                config, apply, rollback,
                null, null, false);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static MultiStepTemplateLoadedChange buildMultiStep(boolean transactional,
                                                                TemplatePayload config,
                                                                List<TemplateStep> steps) {
        return new MultiStepTemplateLoadedChange(
                "test-file.yml", "test-id", "001", "author",
                (Class) DummyTemplate.class, dummyConstructor,
                Collections.emptyList(), transactional,
                false, false,
                config, (List) steps,
                null, null, false);
    }

    // ── Assertion helpers ───────────────────────────────────────────────

    private static boolean isTxSupportError(ValidationError e) {
        return e.getMessage().contains("does not support transactions");
    }

    // ═════════════════════════════════════════════════════════════════════
    //  Simple template tests
    // ═════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("SimpleTemplate transaction support validation")
    class SimpleTemplateValidation {

        @Test
        @DisplayName("ERROR: transactional change with non-tx apply payload")
        void transactionalChange_nonTxApplyPayload_shouldError() {
            List<ValidationError> errors = buildSimple(true, null, new NonTransactionalPayload(), new UnclaimedPayload())
                    .getValidationErrors(VALIDATION_CONTEXT);

            assertTrue(errors.stream().anyMatch(e -> e.getMessage().contains("apply payload does not support transactions")),
                    "Expected error about apply payload not supporting transactions, but got: " + errors);
        }

        @Test
        @DisplayName("No error when all payloads are unclaimed (transactional change)")
        void transactionalChange_allUnclaimed_noTxError() {
            List<ValidationError> errors = buildSimple(true, null, new UnclaimedPayload(), new UnclaimedPayload())
                    .getValidationErrors(VALIDATION_CONTEXT);

            assertFalse(errors.stream().anyMatch(PayloadTransactionSupportValidationTest::isTxSupportError),
                    "Expected no transaction-support errors, but got: " + errors);
        }

        @Test
        @DisplayName("No error when change is non-transactional with non-tx apply payload")
        void nonTransactionalChange_nonTxApplyPayload_noError() {
            List<ValidationError> errors = buildSimple(false, null, new NonTransactionalPayload(), null)
                    .getValidationErrors(VALIDATION_CONTEXT);

            assertFalse(errors.stream().anyMatch(PayloadTransactionSupportValidationTest::isTxSupportError),
                    "Expected no transaction-support errors, but got: " + errors);
        }

        @Test
        @DisplayName("WARNING path: non-tx change but apply payload supports transactions — no validation error")
        void nonTransactionalChange_txApplyPayload_warningPathNoError() {
            List<ValidationError> errors = buildSimple(false, null, new TransactionalPayload(), null)
                    .getValidationErrors(VALIDATION_CONTEXT);

            assertFalse(errors.stream().anyMatch(PayloadTransactionSupportValidationTest::isTxSupportError),
                    "Warning path must not produce transaction-support validation errors, but got: " + errors);
        }

        @Test
        @DisplayName("No warning when non-tx change has unclaimed apply payload")
        void nonTransactionalChange_unclaimedApplyPayload_noWarning() {
            List<ValidationError> errors = buildSimple(false, null, new UnclaimedPayload(), null)
                    .getValidationErrors(VALIDATION_CONTEXT);

            assertFalse(errors.stream().anyMatch(PayloadTransactionSupportValidationTest::isTxSupportError),
                    "Unclaimed apply payload should not trigger any transaction-support errors, but got: " + errors);
        }

        @Test
        @DisplayName("Rollback payload with non-tx is NOT checked for transaction support")
        void transactionalChange_nonTxRollbackPayload_notChecked() {
            List<ValidationError> errors = buildSimple(true, null, new UnclaimedPayload(), new NonTransactionalPayload())
                    .getValidationErrors(VALIDATION_CONTEXT);

            assertFalse(errors.stream().anyMatch(PayloadTransactionSupportValidationTest::isTxSupportError),
                    "Rollback payload should not be checked for transaction support, but got: " + errors);
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  Multi-step template tests
    // ═════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("MultiStep transaction support validation")
    class MultiStepValidation {

        @SuppressWarnings({"unchecked", "rawtypes"})
        private TemplateStep step(TemplatePayload apply, TemplatePayload rollback) {
            return new TemplateStep(apply, rollback);
        }

        @Test
        @DisplayName("ERROR: transactional change with non-tx step apply payload")
        void transactionalChange_stepApplyNonTx_shouldError() {
            List<ValidationError> errors = buildMultiStep(true, null,
                    Collections.singletonList(step(new NonTransactionalPayload(), new UnclaimedPayload())))
                    .getValidationErrors(VALIDATION_CONTEXT);

            assertTrue(errors.stream().anyMatch(e -> e.getMessage().contains("step 1 apply payload does not support transactions")),
                    "Expected error about step 1 apply payload not supporting transactions, but got: " + errors);
        }

        @Test
        @DisplayName("WARNING path: non-tx change but all step apply payloads support transactions — no validation error")
        void nonTransactionalChange_allStepApplyPayloadsTx_warningPathNoError() {
            List<ValidationError> errors = buildMultiStep(false, null,
                    Collections.singletonList(step(new TransactionalPayload(), new NonTransactionalPayload())))
                    .getValidationErrors(VALIDATION_CONTEXT);

            assertFalse(errors.stream().anyMatch(PayloadTransactionSupportValidationTest::isTxSupportError),
                    "Warning path must not produce transaction-support validation errors, but got: " + errors);
        }

        @Test
        @DisplayName("Mixed step apply payloads on non-tx change — no error, no warning")
        void nonTransactionalChange_mixedStepApplyPayloads_noErrorNoWarning() {
            List<ValidationError> errors = buildMultiStep(false, null,
                    Arrays.asList(
                            step(new TransactionalPayload(), null),
                            step(new NonTransactionalPayload(), null)))
                    .getValidationErrors(VALIDATION_CONTEXT);

            assertFalse(errors.stream().anyMatch(PayloadTransactionSupportValidationTest::isTxSupportError),
                    "Mixed step apply payloads should not trigger transaction-support errors, but got: " + errors);
        }

        @Test
        @DisplayName("Step rollback payload with non-tx is NOT checked for transaction support")
        void transactionalChange_stepRollbackNonTx_notChecked() {
            List<ValidationError> errors = buildMultiStep(true, null,
                    Collections.singletonList(step(new UnclaimedPayload(), new NonTransactionalPayload())))
                    .getValidationErrors(VALIDATION_CONTEXT);

            assertFalse(errors.stream().anyMatch(PayloadTransactionSupportValidationTest::isTxSupportError),
                    "Step rollback payload should not be checked for transaction support, but got: " + errors);
        }
    }
}
