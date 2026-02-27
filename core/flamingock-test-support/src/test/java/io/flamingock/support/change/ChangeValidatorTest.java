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
import io.flamingock.support.change.fixtures.NoApplyMethodChange;
import io.flamingock.support.change.fixtures.NoChangeAnnotationClass;
import io.flamingock.support.change.fixtures.NoOrderPrefixChange;
import io.flamingock.support.change.fixtures._0001__FullyAnnotatedChange;
import io.flamingock.support.change.fixtures._0002__NonTransactionalChange;
import io.flamingock.support.change.fixtures._0003__NoTargetSystemChange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChangeValidatorTest {

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        @DisplayName("Should throw IllegalArgumentException when class has no @Change annotation")
        void shouldThrowWhenNoChangeAnnotation() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> ChangeValidator.of(NoChangeAnnotationClass.class));

            assertTrue(ex.getMessage().contains(NoChangeAnnotationClass.class.getName()));
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when class has no @Apply method")
        void shouldThrowWhenNoApplyMethod() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> ChangeValidator.of(NoApplyMethodChange.class));

            assertTrue(ex.getMessage().contains(NoApplyMethodChange.class.getName()));
        }

        @Test
        @DisplayName("Should throw NullPointerException when changeClass is null")
        void shouldThrowWhenChangeClassIsNull() {
            assertThrows(NullPointerException.class, () -> ChangeValidator.of((Class<?>) null));
        }

        @Test
        @DisplayName("Should construct successfully for a valid change class")
        void shouldConstructSuccessfully() {
            assertDoesNotThrow(() -> ChangeValidator.of(_0001__FullyAnnotatedChange.class));
        }

        @Test
        @DisplayName("Should pass validate() with no assertions added")
        void shouldPassValidateWithNoAssertions() {
            CodeBasedChangeValidator validator = ChangeValidator.of(_0001__FullyAnnotatedChange.class);

            assertDoesNotThrow(validator::validate);
        }
    }

    @Nested
    @DisplayName("withId")
    class WithIdTests {

        @Test
        @DisplayName("Should pass when id matches")
        void shouldPassWhenIdMatches() {
            CodeBasedChangeValidator validator = ChangeValidator.of(_0001__FullyAnnotatedChange.class)
                    .withId("fully-annotated");

            assertDoesNotThrow(validator::validate);
        }

        @Test
        @DisplayName("Should fail when id does not match")
        void shouldFailWhenIdDoesNotMatch() {
            CodeBasedChangeValidator validator = ChangeValidator.of(_0001__FullyAnnotatedChange.class)
                    .withId("wrong-id");

            AssertionError error = assertThrows(AssertionError.class, validator::validate);

            assertTrue(error.getMessage().contains("withId"));
            assertTrue(error.getMessage().contains("wrong-id"));
            assertTrue(error.getMessage().contains("fully-annotated"));
        }
    }

    @Nested
    @DisplayName("withAuthor")
    class WithAuthorTests {

        @Test
        @DisplayName("Should pass when author matches")
        void shouldPassWhenAuthorMatches() {
            CodeBasedChangeValidator validator = ChangeValidator.of(_0001__FullyAnnotatedChange.class)
                    .withAuthor("test-author");

            assertDoesNotThrow(validator::validate);
        }

        @Test
        @DisplayName("Should fail when author does not match")
        void shouldFailWhenAuthorDoesNotMatch() {
            CodeBasedChangeValidator validator = ChangeValidator.of(_0001__FullyAnnotatedChange.class)
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
        @DisplayName("Should pass when order matches the class name prefix")
        void shouldPassWhenOrderMatches() {
            CodeBasedChangeValidator validator = ChangeValidator.of(_0001__FullyAnnotatedChange.class)
                    .withOrder("0001");

            assertDoesNotThrow(validator::validate);
        }

        @Test
        @DisplayName("Should pass for a different valid order prefix")
        void shouldPassForDifferentValidOrderPrefix() {
            CodeBasedChangeValidator validator = ChangeValidator.of(_0002__NonTransactionalChange.class)
                    .withOrder("0002");

            assertDoesNotThrow(validator::validate);
        }

        @Test
        @DisplayName("Should fail when order does not match")
        void shouldFailWhenOrderDoesNotMatch() {
            CodeBasedChangeValidator validator = ChangeValidator.of(_0001__FullyAnnotatedChange.class)
                    .withOrder("9999");

            AssertionError error = assertThrows(AssertionError.class, validator::validate);

            assertTrue(error.getMessage().contains("withOrder"));
            assertTrue(error.getMessage().contains("9999"));
            assertTrue(error.getMessage().contains("0001"));
        }

        @Test
        @DisplayName("Should fail with descriptive message when class name has no order prefix")
        void shouldFailWithDescriptiveMessageWhenNoOrderPrefix() {
            CodeBasedChangeValidator validator = ChangeValidator.of(NoOrderPrefixChange.class)
                    .withOrder("0001");

            AssertionError error = assertThrows(AssertionError.class, validator::validate);

            assertTrue(error.getMessage().contains("withOrder"));
            assertTrue(error.getMessage().contains("NoOrderPrefixChange"));
            assertTrue(error.getMessage().contains("_ORDER__Name"));
        }
    }

    @Nested
    @DisplayName("isTransactional")
    class IsTransactionalTests {

        @Test
        @DisplayName("Should pass when change is transactional via explicit annotation value")
        void shouldPassWhenTransactional() {
            CodeBasedChangeValidator validator = ChangeValidator.of(_0001__FullyAnnotatedChange.class)
                    .isTransactional();

            assertDoesNotThrow(validator::validate);
        }

        @Test
        @DisplayName("Should pass when change uses default transactional=true")
        void shouldPassForDefaultTransactional() {
            CodeBasedChangeValidator validator = ChangeValidator.of(_0003__NoTargetSystemChange.class)
                    .isTransactional();

            assertDoesNotThrow(validator::validate);
        }

        @Test
        @DisplayName("Should fail when change is not transactional")
        void shouldFailWhenNotTransactional() {
            CodeBasedChangeValidator validator = ChangeValidator.of(_0002__NonTransactionalChange.class)
                    .isTransactional();

            AssertionError error = assertThrows(AssertionError.class, validator::validate);

            assertTrue(error.getMessage().contains("isTransactional"));
        }
    }

    @Nested
    @DisplayName("isNotTransactional")
    class IsNotTransactionalTests {

        @Test
        @DisplayName("Should pass when change is not transactional")
        void shouldPassWhenNotTransactional() {
            CodeBasedChangeValidator validator = ChangeValidator.of(_0002__NonTransactionalChange.class)
                    .isNotTransactional();

            assertDoesNotThrow(validator::validate);
        }

        @Test
        @DisplayName("Should fail when change is transactional")
        void shouldFailWhenTransactional() {
            CodeBasedChangeValidator validator = ChangeValidator.of(_0001__FullyAnnotatedChange.class)
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
            CodeBasedChangeValidator validator = ChangeValidator.of(_0001__FullyAnnotatedChange.class)
                    .withTargetSystem("mongodb");

            assertDoesNotThrow(validator::validate);
        }

        @Test
        @DisplayName("Should fail when target system id does not match")
        void shouldFailWhenTargetSystemDoesNotMatch() {
            CodeBasedChangeValidator validator = ChangeValidator.of(_0001__FullyAnnotatedChange.class)
                    .withTargetSystem("postgresql");

            AssertionError error = assertThrows(AssertionError.class, validator::validate);

            assertTrue(error.getMessage().contains("withTargetSystem"));
            assertTrue(error.getMessage().contains("postgresql"));
            assertTrue(error.getMessage().contains("mongodb"));
        }

        @Test
        @DisplayName("Should fail when @TargetSystem annotation is not present")
        void shouldFailWhenTargetSystemAnnotationAbsent() {
            CodeBasedChangeValidator validator = ChangeValidator.of(_0003__NoTargetSystemChange.class)
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
        @DisplayName("Should pass for ALWAYS_RETRY when @Recovery is present with ALWAYS_RETRY")
        void shouldPassForAlwaysRetryWhenAnnotationPresent() {
            CodeBasedChangeValidator validator = ChangeValidator.of(_0001__FullyAnnotatedChange.class)
                    .withRecovery(RecoveryStrategy.ALWAYS_RETRY);

            assertDoesNotThrow(validator::validate);
        }

        @Test
        @DisplayName("Should pass for MANUAL_INTERVENTION when @Recovery annotation is absent")
        void shouldPassForDefaultWhenAnnotationAbsent() {
            CodeBasedChangeValidator validator = ChangeValidator.of(_0002__NonTransactionalChange.class)
                    .withRecovery(RecoveryStrategy.MANUAL_INTERVENTION);

            assertDoesNotThrow(validator::validate);
        }

        @Test
        @DisplayName("Should fail when recovery strategy does not match")
        void shouldFailWhenRecoveryDoesNotMatch() {
            CodeBasedChangeValidator validator = ChangeValidator.of(_0001__FullyAnnotatedChange.class)
                    .withRecovery(RecoveryStrategy.MANUAL_INTERVENTION);

            AssertionError error = assertThrows(AssertionError.class, validator::validate);

            assertTrue(error.getMessage().contains("withRecovery"));
            assertTrue(error.getMessage().contains("MANUAL_INTERVENTION"));
            assertTrue(error.getMessage().contains("ALWAYS_RETRY"));
        }

        @Test
        @DisplayName("Should fail when ALWAYS_RETRY expected but default MANUAL_INTERVENTION is in effect")
        void shouldFailWhenAlwaysRetryExpectedButDefaultApplies() {
            CodeBasedChangeValidator validator = ChangeValidator.of(_0002__NonTransactionalChange.class)
                    .withRecovery(RecoveryStrategy.ALWAYS_RETRY);

            AssertionError error = assertThrows(AssertionError.class, validator::validate);

            assertTrue(error.getMessage().contains("withRecovery"));
            assertTrue(error.getMessage().contains("ALWAYS_RETRY"));
            assertTrue(error.getMessage().contains("MANUAL_INTERVENTION"));
        }
    }

    @Nested
    @DisplayName("hasRollbackMethod")
    class HasRollbackMethodTests {

        @Test
        @DisplayName("Should pass when @Rollback method is present")
        void shouldPassWhenRollbackPresent() {
            CodeBasedChangeValidator validator = ChangeValidator.of(_0001__FullyAnnotatedChange.class)
                    .hasRollbackMethod();

            assertDoesNotThrow(validator::validate);
        }

        @Test
        @DisplayName("Should fail when no @Rollback method is present")
        void shouldFailWhenRollbackAbsent() {
            CodeBasedChangeValidator validator = ChangeValidator.of(_0002__NonTransactionalChange.class)
                    .hasRollbackMethod();

            AssertionError error = assertThrows(AssertionError.class, validator::validate);

            assertTrue(error.getMessage().contains("hasRollbackMethod"));
            assertTrue(error.getMessage().contains(_0002__NonTransactionalChange.class.getSimpleName()));
        }
    }

    @Nested
    @DisplayName("Aggregated Failures")
    class AggregatedFailuresTests {

        @Test
        @DisplayName("Should report all failures in a single AssertionError")
        void shouldReportAllFailuresTogether() {
            CodeBasedChangeValidator validator = ChangeValidator.of(_0001__FullyAnnotatedChange.class)
                    .withId("wrong-id")
                    .withAuthor("wrong-author")
                    .withOrder("9999");

            AssertionError error = assertThrows(AssertionError.class, validator::validate);

            assertTrue(error.getMessage().contains("withId"));
            assertTrue(error.getMessage().contains("withAuthor"));
            assertTrue(error.getMessage().contains("withOrder"));
        }

        @Test
        @DisplayName("Should only report failed assertions, not passing ones")
        void shouldOnlyReportFailedAssertions() {
            CodeBasedChangeValidator validator = ChangeValidator.of(_0001__FullyAnnotatedChange.class)
                    .withId("fully-annotated")
                    .withAuthor("wrong-author");

            AssertionError error = assertThrows(AssertionError.class, validator::validate);

            assertTrue(error.getMessage().contains("withAuthor"));
            assertFalse(error.getMessage().contains("withId"));
        }

        @Test
        @DisplayName("Should include the change class simple name in the error header")
        void shouldIncludeClassNameInErrorHeader() {
            CodeBasedChangeValidator validator = ChangeValidator.of(_0001__FullyAnnotatedChange.class)
                    .withId("wrong-id");

            AssertionError error = assertThrows(AssertionError.class, validator::validate);

            assertTrue(error.getMessage().contains(_0001__FullyAnnotatedChange.class.getSimpleName()));
        }

        @Test
        @DisplayName("Should combine assertions across all assertion types")
        void shouldCombineAssertionsAcrossAllTypes() {
            CodeBasedChangeValidator validator = ChangeValidator.of(_0002__NonTransactionalChange.class)
                    .withId("wrong-id")
                    .isTransactional()
                    .withTargetSystem("wrong-system")
                    .withRecovery(RecoveryStrategy.ALWAYS_RETRY)
                    .hasRollbackMethod();

            AssertionError error = assertThrows(AssertionError.class, validator::validate);

            assertTrue(error.getMessage().contains("withId"));
            assertTrue(error.getMessage().contains("isTransactional"));
            assertTrue(error.getMessage().contains("withTargetSystem"));
            assertTrue(error.getMessage().contains("withRecovery"));
            assertTrue(error.getMessage().contains("hasRollbackMethod"));
        }
    }
}
