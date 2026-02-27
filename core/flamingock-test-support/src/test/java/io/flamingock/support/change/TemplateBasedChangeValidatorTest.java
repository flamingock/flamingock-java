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
package io.flamingock.support.change;

import io.flamingock.api.RecoveryStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateBasedChangeValidatorTest {

    private static final String FIXTURES_BASE = "io/flamingock/support/change/fixtures/";

    private Path fixture(String fileName) {
        try {
            return Paths.get(Objects.requireNonNull(getClass().getClassLoader().getResource(FIXTURES_BASE + fileName)).toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        @DisplayName("Should throw IllegalArgumentException when file does not exist")
        void shouldThrowWhenFileDoesNotExist() {
            Path missing = Paths.get("/tmp/nonexistent_flamingock_fixture.yaml");
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> ChangeValidator.of(missing));
            assertTrue(ex.getMessage().contains("does not exist"));
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when 'id' field is absent")
        void shouldThrowWhenIdAbsent() throws Exception {
            Path tmp = java.nio.file.Files.createTempFile("_0001__no_id", ".yaml");
            java.nio.file.Files.write(tmp, "template: MongoChangeTemplate\napply: something\n".getBytes());
            try {
                IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                        () -> ChangeValidator.of(tmp));
                assertTrue(ex.getMessage().contains("id"));
            } finally {
                java.nio.file.Files.deleteIfExists(tmp);
            }
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when 'template' field is absent")
        void shouldThrowWhenTemplateAbsent() throws Exception {
            Path tmp = java.nio.file.Files.createTempFile("_0001__no_template", ".yaml");
            java.nio.file.Files.write(tmp, "id: some-id\napply: something\n".getBytes());
            try {
                IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                        () -> ChangeValidator.of(tmp));
                assertTrue(ex.getMessage().contains("template"));
            } finally {
                java.nio.file.Files.deleteIfExists(tmp);
            }
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when both 'apply' and 'steps' are absent")
        void shouldThrowWhenApplyAndStepsAbsent() throws Exception {
            Path tmp = java.nio.file.Files.createTempFile("_0001__no_apply", ".yaml");
            java.nio.file.Files.write(tmp, "id: some-id\ntemplate: SomeTemplate\n".getBytes());
            try {
                IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                        () -> ChangeValidator.of(tmp));
                assertTrue(ex.getMessage().contains("apply") || ex.getMessage().contains("steps"));
            } finally {
                java.nio.file.Files.deleteIfExists(tmp);
            }
        }

        @Test
        @DisplayName("Should construct successfully for a valid simple YAML file")
        void shouldConstructSuccessfullyForSimpleTemplate() {
            assertDoesNotThrow(() -> ChangeValidator.of(fixture("_0001__simple_with_rollback.yaml")));
        }

        @Test
        @DisplayName("Should construct successfully for a valid multi-step YAML file")
        void shouldConstructSuccessfullyForMultiStepTemplate() {
            assertDoesNotThrow(() -> ChangeValidator.of(fixture("_0003__multi_step_all_rollback.yaml")));
        }

        @Test
        @DisplayName("Should pass validate() with no assertions added")
        void shouldPassValidateWithNoAssertions() {
            TemplateBasedChangeValidator validator = ChangeValidator.of(fixture("_0001__simple_with_rollback.yaml"));
            assertDoesNotThrow(validator::validate);
        }
    }

    @Nested
    @DisplayName("withId")
    class WithIdTests {

        @Test
        @DisplayName("Should pass when id matches")
        void shouldPassWhenIdMatches() {
            TemplateBasedChangeValidator validator = ChangeValidator.of(fixture("_0001__simple_with_rollback.yaml"))
                    .withId("simple-with-rollback");
            assertDoesNotThrow(validator::validate);
        }

        @Test
        @DisplayName("Should fail when id does not match")
        void shouldFailWhenIdDoesNotMatch() {
            TemplateBasedChangeValidator validator = ChangeValidator.of(fixture("_0001__simple_with_rollback.yaml"))
                    .withId("wrong-id");
            AssertionError error = assertThrows(AssertionError.class, validator::validate);
            assertTrue(error.getMessage().contains("withId"));
            assertTrue(error.getMessage().contains("wrong-id"));
            assertTrue(error.getMessage().contains("simple-with-rollback"));
        }
    }

    @Nested
    @DisplayName("withAuthor")
    class WithAuthorTests {

        @Test
        @DisplayName("Should pass when author matches")
        void shouldPassWhenAuthorMatches() {
            TemplateBasedChangeValidator validator = ChangeValidator.of(fixture("_0005__with_author_and_recovery.yaml"))
                    .withAuthor("test-author");
            assertDoesNotThrow(validator::validate);
        }

        @Test
        @DisplayName("Should pass when no author in YAML and withAuthor(null) is called")
        void shouldPassWhenNoAuthorInYamlAndNullExpected() {
            TemplateBasedChangeValidator validator = ChangeValidator.of(fixture("_0001__simple_with_rollback.yaml"))
                    .withAuthor(null);
            assertDoesNotThrow(validator::validate);
        }

        @Test
        @DisplayName("Should fail when expected author but none is set in YAML")
        void shouldFailWhenExpectedAuthorButNoneInYaml() {
            TemplateBasedChangeValidator validator = ChangeValidator.of(fixture("_0001__simple_with_rollback.yaml"))
                    .withAuthor("expected-author");
            AssertionError error = assertThrows(AssertionError.class, validator::validate);
            assertTrue(error.getMessage().contains("withAuthor"));
            assertTrue(error.getMessage().contains("expected-author"));
        }

        @Test
        @DisplayName("Should fail when author does not match")
        void shouldFailWhenAuthorDoesNotMatch() {
            TemplateBasedChangeValidator validator = ChangeValidator.of(fixture("_0005__with_author_and_recovery.yaml"))
                    .withAuthor("wrong-author");
            AssertionError error = assertThrows(AssertionError.class, validator::validate);
            assertTrue(error.getMessage().contains("withAuthor"));
            assertTrue(error.getMessage().contains("wrong-author"));
            assertTrue(error.getMessage().contains("test-author"));
        }
    }

    @Nested
    @DisplayName("withOrder")
    class WithOrderTests {

        @Test
        @DisplayName("Should pass when order matches the file name prefix")
        void shouldPassWhenOrderMatches() {
            TemplateBasedChangeValidator validator = ChangeValidator.of(fixture("_0001__simple_with_rollback.yaml"))
                    .withOrder("0001");
            assertDoesNotThrow(validator::validate);
        }

        @Test
        @DisplayName("Should fail when order does not match")
        void shouldFailWhenOrderDoesNotMatch() {
            TemplateBasedChangeValidator validator = ChangeValidator.of(fixture("_0001__simple_with_rollback.yaml"))
                    .withOrder("9999");
            AssertionError error = assertThrows(AssertionError.class, validator::validate);
            assertTrue(error.getMessage().contains("withOrder"));
            assertTrue(error.getMessage().contains("9999"));
            assertTrue(error.getMessage().contains("0001"));
        }
    }

    @Nested
    @DisplayName("withTemplateName")
    class WithTemplateNameTests {

        @Test
        @DisplayName("Should pass when template name matches")
        void shouldPassWhenTemplateNameMatches() {
            TemplateBasedChangeValidator validator = ChangeValidator.of(fixture("_0001__simple_with_rollback.yaml"))
                    .withTemplateName("MongoChangeTemplate");
            assertDoesNotThrow(validator::validate);
        }

        @Test
        @DisplayName("Should fail when template name does not match")
        void shouldFailWhenTemplateNameDoesNotMatch() {
            TemplateBasedChangeValidator validator = ChangeValidator.of(fixture("_0001__simple_with_rollback.yaml"))
                    .withTemplateName("WrongTemplate");
            AssertionError error = assertThrows(AssertionError.class, validator::validate);
            assertTrue(error.getMessage().contains("withTemplateName"));
            assertTrue(error.getMessage().contains("WrongTemplate"));
            assertTrue(error.getMessage().contains("MongoChangeTemplate"));
        }
    }

    @Nested
    @DisplayName("isTransactional")
    class IsTransactionalTests {

        @Test
        @DisplayName("Should pass when transactional: true is explicit in YAML")
        void shouldPassWhenExplicitlyTransactional() {
            TemplateBasedChangeValidator validator = ChangeValidator.of(fixture("_0001__simple_with_rollback.yaml"))
                    .isTransactional();
            assertDoesNotThrow(validator::validate);
        }

        @Test
        @DisplayName("Should pass when transactional field is absent (defaults to true)")
        void shouldPassWhenTransactionalFieldAbsent() {
            TemplateBasedChangeValidator validator = ChangeValidator.of(fixture("_0003__multi_step_all_rollback.yaml"))
                    .isTransactional();
            assertDoesNotThrow(validator::validate);
        }

        @Test
        @DisplayName("Should fail when transactional: false is set")
        void shouldFailWhenTransactionalFalse() {
            TemplateBasedChangeValidator validator = ChangeValidator.of(fixture("_0002__simple_no_rollback.yaml"))
                    .isTransactional();
            AssertionError error = assertThrows(AssertionError.class, validator::validate);
            assertTrue(error.getMessage().contains("isTransactional"));
        }
    }

    @Nested
    @DisplayName("isNotTransactional")
    class IsNotTransactionalTests {

        @Test
        @DisplayName("Should pass when transactional: false is set")
        void shouldPassWhenTransactionalFalse() {
            TemplateBasedChangeValidator validator = ChangeValidator.of(fixture("_0002__simple_no_rollback.yaml"))
                    .isNotTransactional();
            assertDoesNotThrow(validator::validate);
        }

        @Test
        @DisplayName("Should fail when transactional: true is set")
        void shouldFailWhenTransactionalTrue() {
            TemplateBasedChangeValidator validator = ChangeValidator.of(fixture("_0001__simple_with_rollback.yaml"))
                    .isNotTransactional();
            AssertionError error = assertThrows(AssertionError.class, validator::validate);
            assertTrue(error.getMessage().contains("isNotTransactional"));
        }
    }

    @Nested
    @DisplayName("withTargetSystem")
    class WithTargetSystemTests {

        @Test
        @DisplayName("Should pass when target system id matches")
        void shouldPassWhenTargetSystemMatches() {
            TemplateBasedChangeValidator validator = ChangeValidator.of(fixture("_0001__simple_with_rollback.yaml"))
                    .withTargetSystem("mongodb");
            assertDoesNotThrow(validator::validate);
        }

        @Test
        @DisplayName("Should fail when target system id does not match")
        void shouldFailWhenTargetSystemDoesNotMatch() {
            TemplateBasedChangeValidator validator = ChangeValidator.of(fixture("_0001__simple_with_rollback.yaml"))
                    .withTargetSystem("postgresql");
            AssertionError error = assertThrows(AssertionError.class, validator::validate);
            assertTrue(error.getMessage().contains("withTargetSystem"));
            assertTrue(error.getMessage().contains("postgresql"));
            assertTrue(error.getMessage().contains("mongodb"));
        }

        @Test
        @DisplayName("Should fail when targetSystem is absent in YAML")
        void shouldFailWhenTargetSystemAbsent() {
            TemplateBasedChangeValidator validator = ChangeValidator.of(fixture("_0002__simple_no_rollback.yaml"))
                    .withTargetSystem("mongodb");
            AssertionError error = assertThrows(AssertionError.class, validator::validate);
            assertTrue(error.getMessage().contains("withTargetSystem"));
            assertTrue(error.getMessage().contains("none is declared"));
        }
    }

    @Nested
    @DisplayName("withRecovery")
    class WithRecoveryTests {

        @Test
        @DisplayName("Should pass for ALWAYS_RETRY when recovery.strategy is set in YAML")
        void shouldPassForAlwaysRetry() {
            TemplateBasedChangeValidator validator = ChangeValidator.of(fixture("_0005__with_author_and_recovery.yaml"))
                    .withRecovery(RecoveryStrategy.ALWAYS_RETRY);
            assertDoesNotThrow(validator::validate);
        }

        @Test
        @DisplayName("Should pass for MANUAL_INTERVENTION when recovery field is absent (default)")
        void shouldPassForDefaultManualIntervention() {
            TemplateBasedChangeValidator validator = ChangeValidator.of(fixture("_0001__simple_with_rollback.yaml"))
                    .withRecovery(RecoveryStrategy.MANUAL_INTERVENTION);
            assertDoesNotThrow(validator::validate);
        }

        @Test
        @DisplayName("Should fail when recovery strategy does not match")
        void shouldFailWhenRecoveryDoesNotMatch() {
            TemplateBasedChangeValidator validator = ChangeValidator.of(fixture("_0005__with_author_and_recovery.yaml"))
                    .withRecovery(RecoveryStrategy.MANUAL_INTERVENTION);
            AssertionError error = assertThrows(AssertionError.class, validator::validate);
            assertTrue(error.getMessage().contains("withRecovery"));
            assertTrue(error.getMessage().contains("MANUAL_INTERVENTION"));
            assertTrue(error.getMessage().contains("ALWAYS_RETRY"));
        }
    }

    @Nested
    @DisplayName("withStepCount")
    class WithStepCountTests {

        @Test
        @DisplayName("Should pass for multi-step template with correct step count")
        void shouldPassForMultiStepWithCorrectCount() {
            TemplateBasedChangeValidator validator = ChangeValidator.of(fixture("_0003__multi_step_all_rollback.yaml"))
                    .withStepCount(3);
            assertDoesNotThrow(validator::validate);
        }

        @Test
        @DisplayName("Should fail for multi-step template with wrong step count")
        void shouldFailForMultiStepWithWrongCount() {
            TemplateBasedChangeValidator validator = ChangeValidator.of(fixture("_0003__multi_step_all_rollback.yaml"))
                    .withStepCount(5);
            AssertionError error = assertThrows(AssertionError.class, validator::validate);
            assertTrue(error.getMessage().contains("withStepCount"));
            assertTrue(error.getMessage().contains("5"));
            assertTrue(error.getMessage().contains("3"));
        }

        @Test
        @DisplayName("Should fail with descriptive error when applied to a simple template")
        void shouldFailWithDescriptiveErrorOnSimpleTemplate() {
            TemplateBasedChangeValidator validator = ChangeValidator.of(fixture("_0001__simple_with_rollback.yaml"))
                    .withStepCount(1);
            AssertionError error = assertThrows(AssertionError.class, validator::validate);
            assertTrue(error.getMessage().contains("withStepCount"));
            assertTrue(error.getMessage().contains("simple"));
        }
    }

    @Nested
    @DisplayName("hasRollback")
    class HasRollbackTests {

        @Test
        @DisplayName("Should pass for simple template with top-level rollback")
        void shouldPassForSimpleTemplateWithRollback() {
            TemplateBasedChangeValidator validator = ChangeValidator.of(fixture("_0001__simple_with_rollback.yaml"))
                    .hasRollback();
            assertDoesNotThrow(validator::validate);
        }

        @Test
        @DisplayName("Should fail for simple template without rollback")
        void shouldFailForSimpleTemplateWithoutRollback() {
            TemplateBasedChangeValidator validator = ChangeValidator.of(fixture("_0002__simple_no_rollback.yaml"))
                    .hasRollback();
            AssertionError error = assertThrows(AssertionError.class, validator::validate);
            assertTrue(error.getMessage().contains("hasRollback"));
        }

        @Test
        @DisplayName("Should pass for multi-step template where all steps have rollback")
        void shouldPassForMultiStepAllHaveRollback() {
            TemplateBasedChangeValidator validator = ChangeValidator.of(fixture("_0003__multi_step_all_rollback.yaml"))
                    .hasRollback();
            assertDoesNotThrow(validator::validate);
        }

        @Test
        @DisplayName("Should fail for multi-step template where some steps are missing rollback")
        void shouldFailForMultiStepPartialRollback() {
            TemplateBasedChangeValidator validator = ChangeValidator.of(fixture("_0004__multi_step_partial_rollback.yaml"))
                    .hasRollback();
            AssertionError error = assertThrows(AssertionError.class, validator::validate);
            assertTrue(error.getMessage().contains("hasRollback"));
            assertTrue(error.getMessage().contains("step 1"));
        }
    }

    @Nested
    @DisplayName("hasRollbackForStep")
    class HasRollbackForStepTests {

        @Test
        @DisplayName("Should pass when the specified step has a rollback")
        void shouldPassWhenStepHasRollback() {
            TemplateBasedChangeValidator validator = ChangeValidator.of(fixture("_0004__multi_step_partial_rollback.yaml"))
                    .hasRollbackForStep(0);
            assertDoesNotThrow(validator::validate);
        }

        @Test
        @DisplayName("Should fail when the specified step is missing rollback")
        void shouldFailWhenStepMissingRollback() {
            TemplateBasedChangeValidator validator = ChangeValidator.of(fixture("_0004__multi_step_partial_rollback.yaml"))
                    .hasRollbackForStep(1);
            AssertionError error = assertThrows(AssertionError.class, validator::validate);
            assertTrue(error.getMessage().contains("hasRollbackForStep"));
            assertTrue(error.getMessage().contains("step 1"));
        }

        @Test
        @DisplayName("Should fail with descriptive error when applied to a simple template")
        void shouldFailWithDescriptiveErrorOnSimpleTemplate() {
            TemplateBasedChangeValidator validator = ChangeValidator.of(fixture("_0001__simple_with_rollback.yaml"))
                    .hasRollbackForStep(0);
            AssertionError error = assertThrows(AssertionError.class, validator::validate);
            assertTrue(error.getMessage().contains("hasRollbackForStep"));
            assertTrue(error.getMessage().contains("simple"));
        }
    }

    @Nested
    @DisplayName("Aggregated failures")
    class AggregatedFailuresTests {

        @Test
        @DisplayName("Should report all failures in a single AssertionError")
        void shouldReportAllFailuresTogether() {
            TemplateBasedChangeValidator validator = ChangeValidator.of(fixture("_0001__simple_with_rollback.yaml"))
                    .withId("wrong-id")
                    .withTemplateName("WrongTemplate")
                    .withOrder("9999");
            AssertionError error = assertThrows(AssertionError.class, validator::validate);
            assertTrue(error.getMessage().contains("withId"));
            assertTrue(error.getMessage().contains("withTemplateName"));
            assertTrue(error.getMessage().contains("withOrder"));
        }

        @Test
        @DisplayName("Should only report failed assertions, not passing ones")
        void shouldOnlyReportFailedAssertions() {
            TemplateBasedChangeValidator validator = ChangeValidator.of(fixture("_0001__simple_with_rollback.yaml"))
                    .withId("simple-with-rollback")
                    .withTemplateName("WrongTemplate");
            AssertionError error = assertThrows(AssertionError.class, validator::validate);
            assertTrue(error.getMessage().contains("withTemplateName"));
            assertFalse(error.getMessage().contains("withId"));
        }

        @Test
        @DisplayName("Should include the file name in the error header")
        void shouldIncludeFileNameInErrorHeader() {
            TemplateBasedChangeValidator validator = ChangeValidator.of(fixture("_0001__simple_with_rollback.yaml"))
                    .withId("wrong-id");
            AssertionError error = assertThrows(AssertionError.class, validator::validate);
            assertTrue(error.getMessage().contains("_0001__simple_with_rollback"));
        }

        @Test
        @DisplayName("Should combine assertions across all assertion types")
        void shouldCombineAssertionsAcrossAllTypes() {
            TemplateBasedChangeValidator validator = ChangeValidator.of(fixture("_0002__simple_no_rollback.yaml"))
                    .withId("wrong-id")
                    .isTransactional()
                    .withTargetSystem("mongodb")
                    .withRecovery(RecoveryStrategy.ALWAYS_RETRY)
                    .hasRollback();
            AssertionError error = assertThrows(AssertionError.class, validator::validate);
            assertTrue(error.getMessage().contains("withId"));
            assertTrue(error.getMessage().contains("isTransactional"));
            assertTrue(error.getMessage().contains("withTargetSystem"));
            assertTrue(error.getMessage().contains("withRecovery"));
            assertTrue(error.getMessage().contains("hasRollback"));
        }
    }
}
