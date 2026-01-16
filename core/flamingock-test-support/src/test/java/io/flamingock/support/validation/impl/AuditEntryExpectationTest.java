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
package io.flamingock.support.validation.impl;

import io.flamingock.api.RecoveryStrategy;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import io.flamingock.support.domain.AuditEntryDefinition;
import io.flamingock.support.validation.error.FieldMismatchError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditEntryExpectationTest {

    private static final String CHANGE_ID = "test-change-id";
    private static final String EXECUTION_ID = "exec-123";
    private static final String STAGE_ID = "stage-456";
    private static final String AUTHOR = "test-author";
    private static final String CLASS_NAME = "com.example.TestChange";
    private static final String METHOD_NAME = "apply";
    private static final String ERROR_TRACE = "java.lang.RuntimeException: Test error";
    private static final String TARGET_SYSTEM_ID = "mongodb-main";
    private static final String ORDER = "001";
    private static final String HOSTNAME = "test-host";
    private static final LocalDateTime TIMESTAMP = LocalDateTime.of(2025, 1, 15, 10, 30, 0);

    @Nested
    @DisplayName("Required Fields Tests")
    class RequiredFieldsTests {

        @Test
        @DisplayName("Should return empty errors when changeId and status match")
        void shouldReturnEmptyErrorsWhenChangeIdAndStatusMatch() {
            // Given
            AuditEntryDefinition definition = AuditEntryDefinition.APPLIED(CHANGE_ID);
            AuditEntry actual = createBasicAuditEntry(CHANGE_ID, AuditEntry.Status.APPLIED);
            AuditEntryExpectation expectation = new AuditEntryExpectation(definition);

            // When
            List<FieldMismatchError> errors = expectation.compareWith(actual);

            // Then
            assertTrue(errors.isEmpty(), "Expected no errors but got: " + errors);
        }

        @Test
        @DisplayName("Should return error when changeId does not match")
        void shouldReturnErrorWhenChangeIdDoesNotMatch() {
            // Given
            AuditEntryDefinition definition = AuditEntryDefinition.APPLIED("expected-id");
            AuditEntry actual = createBasicAuditEntry("actual-id", AuditEntry.Status.APPLIED);
            AuditEntryExpectation expectation = new AuditEntryExpectation(definition);

            // When
            List<FieldMismatchError> errors = expectation.compareWith(actual);

            // Then
            assertEquals(1, errors.size());
            assertEquals("changeId", errors.get(0).getFieldName());
            assertEquals("expected-id", errors.get(0).getExpectedValue());
            assertEquals("actual-id", errors.get(0).getActualValue());
        }

        @Test
        @DisplayName("Should return error when status does not match")
        void shouldReturnErrorWhenStatusDoesNotMatch() {
            // Given
            AuditEntryDefinition definition = AuditEntryDefinition.APPLIED(CHANGE_ID);
            AuditEntry actual = createBasicAuditEntry(CHANGE_ID, AuditEntry.Status.FAILED);
            AuditEntryExpectation expectation = new AuditEntryExpectation(definition);

            // When
            List<FieldMismatchError> errors = expectation.compareWith(actual);

            // Then
            assertEquals(1, errors.size());
            assertEquals("status", errors.get(0).getFieldName());
            assertEquals("APPLIED", errors.get(0).getExpectedValue());
            assertEquals("FAILED", errors.get(0).getActualValue());
        }

        @Test
        @DisplayName("Should return errors when both changeId and status do not match")
        void shouldReturnErrorsWhenBothChangeIdAndStatusDoNotMatch() {
            // Given
            AuditEntryDefinition definition = AuditEntryDefinition.APPLIED("expected-id");
            AuditEntry actual = createBasicAuditEntry("actual-id", AuditEntry.Status.FAILED);
            AuditEntryExpectation expectation = new AuditEntryExpectation(definition);

            // When
            List<FieldMismatchError> errors = expectation.compareWith(actual);

            // Then
            assertEquals(2, errors.size());
        }
    }

    @Nested
    @DisplayName("ExecutionId Tests")
    class ExecutionIdTests {

        @Test
        @DisplayName("Should not validate executionId when expected is null")
        void shouldNotValidateWhenExpectedIsNull() {
            // Given
            AuditEntryDefinition definition = AuditEntryDefinition.APPLIED(CHANGE_ID);
            // executionId not set on definition
            AuditEntry actual = createBasicAuditEntry(CHANGE_ID, AuditEntry.Status.APPLIED);
            AuditEntryExpectation expectation = new AuditEntryExpectation(definition);

            // When
            List<FieldMismatchError> errors = expectation.compareWith(actual);

            // Then
            assertTrue(errors.isEmpty());
        }

        @Test
        @DisplayName("Should return no error when executionId matches")
        void shouldReturnNoErrorWhenMatches() {
            // Given
            AuditEntryDefinition definition = AuditEntryDefinition.APPLIED(CHANGE_ID)
                    .withExecutionId(EXECUTION_ID);
            AuditEntry actual = createBasicAuditEntry(CHANGE_ID, AuditEntry.Status.APPLIED);
            AuditEntryExpectation expectation = new AuditEntryExpectation(definition);

            // When
            List<FieldMismatchError> errors = expectation.compareWith(actual);

            // Then
            assertTrue(errors.isEmpty());
        }

        @Test
        @DisplayName("Should return error when executionId does not match")
        void shouldReturnErrorWhenDoesNotMatch() {
            // Given
            AuditEntryDefinition definition = AuditEntryDefinition.APPLIED(CHANGE_ID)
                    .withExecutionId("different-exec-id");
            AuditEntry actual = createBasicAuditEntry(CHANGE_ID, AuditEntry.Status.APPLIED);
            AuditEntryExpectation expectation = new AuditEntryExpectation(definition);

            // When
            List<FieldMismatchError> errors = expectation.compareWith(actual);

            // Then
            assertEquals(1, errors.size());
            assertEquals("executionId", errors.get(0).getFieldName());
        }
    }

    @Nested
    @DisplayName("Author Tests")
    class AuthorTests {

        @Test
        @DisplayName("Should not validate author when expected is null")
        void shouldNotValidateWhenExpectedIsNull() {
            // Given
            AuditEntryDefinition definition = AuditEntryDefinition.APPLIED(CHANGE_ID);
            AuditEntry actual = createBasicAuditEntry(CHANGE_ID, AuditEntry.Status.APPLIED);
            AuditEntryExpectation expectation = new AuditEntryExpectation(definition);

            // When
            List<FieldMismatchError> errors = expectation.compareWith(actual);

            // Then
            assertTrue(errors.isEmpty());
        }

        @Test
        @DisplayName("Should return error when author does not match")
        void shouldReturnErrorWhenDoesNotMatch() {
            // Given
            AuditEntryDefinition definition = AuditEntryDefinition.APPLIED(CHANGE_ID)
                    .withAuthor("different-author");
            AuditEntry actual = createBasicAuditEntry(CHANGE_ID, AuditEntry.Status.APPLIED);
            AuditEntryExpectation expectation = new AuditEntryExpectation(definition);

            // When
            List<FieldMismatchError> errors = expectation.compareWith(actual);

            // Then
            assertEquals(1, errors.size());
            assertEquals("author", errors.get(0).getFieldName());
        }
    }

    @Nested
    @DisplayName("ClassName Tests")
    class ClassNameTests {

        @Test
        @DisplayName("Should return error when className does not match")
        void shouldReturnErrorWhenDoesNotMatch() {
            // Given
            AuditEntryDefinition definition = AuditEntryDefinition.APPLIED(CHANGE_ID)
                    .withClassName("com.example.DifferentClass");
            AuditEntry actual = createBasicAuditEntry(CHANGE_ID, AuditEntry.Status.APPLIED);
            AuditEntryExpectation expectation = new AuditEntryExpectation(definition);

            // When
            List<FieldMismatchError> errors = expectation.compareWith(actual);

            // Then
            assertEquals(1, errors.size());
            assertEquals("className", errors.get(0).getFieldName());
        }
    }

    @Nested
    @DisplayName("MethodName Tests")
    class MethodNameTests {

        @Test
        @DisplayName("Should return error when methodName does not match")
        void shouldReturnErrorWhenDoesNotMatch() {
            // Given
            AuditEntryDefinition definition = AuditEntryDefinition.APPLIED(CHANGE_ID)
                    .withMethodName("differentMethod");
            AuditEntry actual = createBasicAuditEntry(CHANGE_ID, AuditEntry.Status.APPLIED);
            AuditEntryExpectation expectation = new AuditEntryExpectation(definition);

            // When
            List<FieldMismatchError> errors = expectation.compareWith(actual);

            // Then
            assertEquals(1, errors.size());
            assertEquals("methodName", errors.get(0).getFieldName());
        }
    }

    @Nested
    @DisplayName("Metadata Tests")
    class MetadataTests {

        @Test
        @DisplayName("Should return error when metadata does not match")
        void shouldReturnErrorWhenDoesNotMatch() {
            // Given
            AuditEntryDefinition definition = AuditEntryDefinition.APPLIED(CHANGE_ID)
                    .withMetadata("expected-metadata");
            AuditEntry actual = createAuditEntry(
                    CHANGE_ID, AuditEntry.Status.APPLIED,
                    EXECUTION_ID, STAGE_ID, AUTHOR, TIMESTAMP,
                    CLASS_NAME, METHOD_NAME, 100L, HOSTNAME,
                    "different-metadata", null, TARGET_SYSTEM_ID, ORDER,
                    RecoveryStrategy.MANUAL_INTERVENTION, true
            );
            AuditEntryExpectation expectation = new AuditEntryExpectation(definition);

            // When
            List<FieldMismatchError> errors = expectation.compareWith(actual);

            // Then
            assertEquals(1, errors.size());
            assertEquals("metadata", errors.get(0).getFieldName());
        }
    }

    @Nested
    @DisplayName("ExecutionMillis Tests")
    class ExecutionMillisTests {

        @Test
        @DisplayName("Should return error when executionMillis does not match")
        void shouldReturnErrorWhenDoesNotMatch() {
            // Given
            AuditEntryDefinition definition = AuditEntryDefinition.APPLIED(CHANGE_ID)
                    .withExecutionMillis(500L);
            AuditEntry actual = createBasicAuditEntry(CHANGE_ID, AuditEntry.Status.APPLIED);
            AuditEntryExpectation expectation = new AuditEntryExpectation(definition);

            // When
            List<FieldMismatchError> errors = expectation.compareWith(actual);

            // Then
            assertEquals(1, errors.size());
            assertEquals("executionMillis", errors.get(0).getFieldName());
        }
    }

    @Nested
    @DisplayName("RecoveryStrategy Tests")
    class RecoveryStrategyTests {

        @Test
        @DisplayName("Should return error when recoveryStrategy does not match")
        void shouldReturnErrorWhenDoesNotMatch() {
            // Given
            AuditEntryDefinition definition = AuditEntryDefinition.APPLIED(CHANGE_ID)
                    .withRecoveryStrategy(RecoveryStrategy.ALWAYS_RETRY);
            AuditEntry actual = createBasicAuditEntry(CHANGE_ID, AuditEntry.Status.APPLIED);
            AuditEntryExpectation expectation = new AuditEntryExpectation(definition);

            // When
            List<FieldMismatchError> errors = expectation.compareWith(actual);

            // Then
            assertEquals(1, errors.size());
            assertEquals("recoveryStrategy", errors.get(0).getFieldName());
            assertEquals("ALWAYS_RETRY", errors.get(0).getExpectedValue());
            assertEquals("MANUAL_INTERVENTION", errors.get(0).getActualValue());
        }
    }

    @Nested
    @DisplayName("Order Tests")
    class OrderTests {

        @Test
        @DisplayName("Should return error when order does not match")
        void shouldReturnErrorWhenDoesNotMatch() {
            // Given
            AuditEntryDefinition definition = AuditEntryDefinition.APPLIED(CHANGE_ID)
                    .withOrder("999");
            AuditEntry actual = createBasicAuditEntry(CHANGE_ID, AuditEntry.Status.APPLIED);
            AuditEntryExpectation expectation = new AuditEntryExpectation(definition);

            // When
            List<FieldMismatchError> errors = expectation.compareWith(actual);

            // Then
            assertEquals(1, errors.size());
            assertEquals("order", errors.get(0).getFieldName());
        }
    }

    @Nested
    @DisplayName("Transactional Tests")
    class TransactionalTests {

        @Test
        @DisplayName("Should return error when transactional does not match")
        void shouldReturnErrorWhenDoesNotMatch() {
            // Given
            AuditEntryDefinition definition = AuditEntryDefinition.APPLIED(CHANGE_ID)
                    .withTransactional(false);
            AuditEntry actual = createBasicAuditEntry(CHANGE_ID, AuditEntry.Status.APPLIED);
            AuditEntryExpectation expectation = new AuditEntryExpectation(definition);

            // When
            List<FieldMismatchError> errors = expectation.compareWith(actual);

            // Then
            assertEquals(1, errors.size());
            assertEquals("transactional", errors.get(0).getFieldName());
        }
    }

    @Nested
    @DisplayName("CreatedAt Tests")
    class CreatedAtTests {

        @Test
        @DisplayName("Should return no error when createdAt matches exactly")
        void shouldReturnNoErrorWhenMatches() {
            // Given
            AuditEntryDefinition definition = AuditEntryDefinition.APPLIED(CHANGE_ID)
                    .withCreatedAt(TIMESTAMP);
            AuditEntry actual = createBasicAuditEntry(CHANGE_ID, AuditEntry.Status.APPLIED);
            AuditEntryExpectation expectation = new AuditEntryExpectation(definition);

            // When
            List<FieldMismatchError> errors = expectation.compareWith(actual);

            // Then
            assertTrue(errors.isEmpty());
        }

        @Test
        @DisplayName("Should return error when createdAt does not match")
        void shouldReturnErrorWhenDoesNotMatch() {
            // Given
            LocalDateTime differentTimestamp = LocalDateTime.of(2025, 12, 25, 12, 0, 0);
            AuditEntryDefinition definition = AuditEntryDefinition.APPLIED(CHANGE_ID)
                    .withCreatedAt(differentTimestamp);
            AuditEntry actual = createBasicAuditEntry(CHANGE_ID, AuditEntry.Status.APPLIED);
            AuditEntryExpectation expectation = new AuditEntryExpectation(definition);

            // When
            List<FieldMismatchError> errors = expectation.compareWith(actual);

            // Then
            assertEquals(1, errors.size());
            assertEquals("createdAt", errors.get(0).getFieldName());
        }
    }

    @Nested
    @DisplayName("Combined Scenarios")
    class CombinedScenarios {

        @Test
        @DisplayName("Should return multiple errors when multiple fields do not match")
        void shouldReturnMultipleErrorsWhenMultipleFieldsDoNotMatch() {
            // Given
            AuditEntryDefinition definition = AuditEntryDefinition.APPLIED(CHANGE_ID)
                    .withAuthor("expected-author")
                    .withClassName("expected-class")
                    .withMethodName("expectedMethod")
                    .withOrder("expected-order");
            AuditEntry actual = createBasicAuditEntry(CHANGE_ID, AuditEntry.Status.APPLIED);
            AuditEntryExpectation expectation = new AuditEntryExpectation(definition);

            // When
            List<FieldMismatchError> errors = expectation.compareWith(actual);

            // Then
            assertEquals(4, errors.size());
        }

        @Test
        @DisplayName("Should validate only set fields ignoring null expectations")
        void shouldValidateOnlySetFieldsIgnoringNullExpectations() {
            // Given - only author is set, other optional fields are null
            AuditEntryDefinition definition = AuditEntryDefinition.APPLIED(CHANGE_ID)
                    .withAuthor(AUTHOR);
            AuditEntry actual = createBasicAuditEntry(CHANGE_ID, AuditEntry.Status.APPLIED);
            AuditEntryExpectation expectation = new AuditEntryExpectation(definition);

            // When
            List<FieldMismatchError> errors = expectation.compareWith(actual);

            // Then - only changeId, status, and author are validated; others are ignored
            assertTrue(errors.isEmpty());
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Should handle null actual state gracefully")
        void shouldHandleNullActualStateGracefully() {
            // Given
            AuditEntryDefinition definition = AuditEntryDefinition.APPLIED(CHANGE_ID);
            AuditEntry actual = createAuditEntry(
                    CHANGE_ID, null,  // null status
                    EXECUTION_ID, STAGE_ID, AUTHOR, TIMESTAMP,
                    CLASS_NAME, METHOD_NAME, 100L, HOSTNAME,
                    null, null, TARGET_SYSTEM_ID, ORDER,
                    RecoveryStrategy.MANUAL_INTERVENTION, true
            );
            AuditEntryExpectation expectation = new AuditEntryExpectation(definition);

            // When
            List<FieldMismatchError> errors = expectation.compareWith(actual);

            // Then
            assertEquals(1, errors.size());
            assertEquals("status", errors.get(0).getFieldName());
            assertEquals("APPLIED", errors.get(0).getExpectedValue());
            assertEquals(null, errors.get(0).getActualValue());
        }

        @Test
        @DisplayName("Should handle null actual recoveryStrategy gracefully")
        void shouldHandleNullActualRecoveryStrategyGracefully() {
            // Given
            AuditEntryDefinition definition = AuditEntryDefinition.APPLIED(CHANGE_ID)
                    .withRecoveryStrategy(RecoveryStrategy.ALWAYS_RETRY);
            AuditEntry actual = createAuditEntry(
                    CHANGE_ID, AuditEntry.Status.APPLIED,
                    EXECUTION_ID, STAGE_ID, AUTHOR, TIMESTAMP,
                    CLASS_NAME, METHOD_NAME, 100L, HOSTNAME,
                    null, null, TARGET_SYSTEM_ID, ORDER,
                    null, true  // null recovery strategy
            );
            AuditEntryExpectation expectation = new AuditEntryExpectation(definition);

            // When
            List<FieldMismatchError> errors = expectation.compareWith(actual);

            // Then
            assertEquals(1, errors.size());
            assertEquals("recoveryStrategy", errors.get(0).getFieldName());
        }

        @Test
        @DisplayName("Should handle null actual createdAt gracefully")
        void shouldHandleNullActualCreatedAtGracefully() {
            // Given
            AuditEntryDefinition definition = AuditEntryDefinition.APPLIED(CHANGE_ID)
                    .withCreatedAt(TIMESTAMP);
            AuditEntry actual = createAuditEntry(
                    CHANGE_ID, AuditEntry.Status.APPLIED,
                    EXECUTION_ID, STAGE_ID, AUTHOR, null,  // null timestamp
                    CLASS_NAME, METHOD_NAME, 100L, HOSTNAME,
                    null, null, TARGET_SYSTEM_ID, ORDER,
                    RecoveryStrategy.MANUAL_INTERVENTION, true
            );
            AuditEntryExpectation expectation = new AuditEntryExpectation(definition);

            // When
            List<FieldMismatchError> errors = expectation.compareWith(actual);

            // Then
            assertEquals(1, errors.size());
            assertEquals("createdAt", errors.get(0).getFieldName());
            assertEquals(null, errors.get(0).getActualValue());
        }
    }

    @Nested
    @DisplayName("All Status Types Tests")
    class AllStatusTypesTests {

        @Test
        @DisplayName("Should validate FAILED status correctly")
        void shouldValidateFailedStatus() {
            // Given
            AuditEntryDefinition definition = AuditEntryDefinition.FAILED(CHANGE_ID);
            AuditEntry actual = createBasicAuditEntry(CHANGE_ID, AuditEntry.Status.FAILED);
            AuditEntryExpectation expectation = new AuditEntryExpectation(definition);

            // When
            List<FieldMismatchError> errors = expectation.compareWith(actual);

            // Then
            assertTrue(errors.isEmpty());
        }

        @Test
        @DisplayName("Should validate ROLLED_BACK status correctly")
        void shouldValidateRolledBackStatus() {
            // Given
            AuditEntryDefinition definition = AuditEntryDefinition.ROLLED_BACK(CHANGE_ID);
            AuditEntry actual = createBasicAuditEntry(CHANGE_ID, AuditEntry.Status.ROLLED_BACK);
            AuditEntryExpectation expectation = new AuditEntryExpectation(definition);

            // When
            List<FieldMismatchError> errors = expectation.compareWith(actual);

            // Then
            assertTrue(errors.isEmpty());
        }

        @Test
        @DisplayName("Should validate ROLLBACK_FAILED status correctly")
        void shouldValidateRollbackFailedStatus() {
            // Given
            AuditEntryDefinition definition = AuditEntryDefinition.ROLLBACK_FAILED(CHANGE_ID);
            AuditEntry actual = createBasicAuditEntry(CHANGE_ID, AuditEntry.Status.ROLLBACK_FAILED);
            AuditEntryExpectation expectation = new AuditEntryExpectation(definition);

            // When
            List<FieldMismatchError> errors = expectation.compareWith(actual);

            // Then
            assertTrue(errors.isEmpty());
        }
    }


    private static AuditEntry createAuditEntry(
            String changeId,
            AuditEntry.Status status,
            String executionId,
            String stageId,
            String author,
            LocalDateTime timestamp,
            String className,
            String methodName,
            long executionMillis,
            String hostname,
            Object metadata,
            String errorTrace,
            String targetSystemId,
            String order,
            RecoveryStrategy recoveryStrategy,
            Boolean transactional
    ) {
        return new AuditEntry(
                executionId,
                stageId,
                changeId,
                author,
                timestamp,
                status,
                AuditEntry.ExecutionType.EXECUTION,
                className,
                methodName,
                null, //TODO: set sourceFile
                executionMillis,
                hostname,
                metadata,
                false,
                false,
                errorTrace,
                AuditTxType.NON_TX,
                targetSystemId,
                order,
                recoveryStrategy,
                transactional
        );
    }

    private static AuditEntry createBasicAuditEntry(String changeId, AuditEntry.Status status) {
        return createAuditEntry(
                changeId, status,
                EXECUTION_ID, STAGE_ID, AUTHOR, TIMESTAMP,
                CLASS_NAME, METHOD_NAME, 100L, HOSTNAME,
                null, null, TARGET_SYSTEM_ID, ORDER,
                RecoveryStrategy.MANUAL_INTERVENTION, true
        );
    }
}
