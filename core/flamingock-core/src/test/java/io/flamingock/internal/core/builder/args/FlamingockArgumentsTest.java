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
package io.flamingock.internal.core.builder.args;

import io.flamingock.internal.common.core.operation.OperationType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class FlamingockArgumentsTest {

    // Test enum moved to class level for Java 8 compatibility
    enum TestEnum {
        VALUE_ONE, VALUE_TWO, VALUE_THREE
    }

    @Test
    void shouldParseAllDefinedParametersWithEqualsFormat() {
        String[] args = {
                "--flamingock.cli.mode=true",
                "--flamingock.operation=EXECUTE_APPLY",
                "--flamingock.output-file=/tmp/output.json"
        };

        FlamingockArguments arguments = FlamingockArguments.parse(args);

        assertTrue(arguments.isCliMode());
        assertTrue(arguments.isOperationProvided());
        assertEquals(OperationType.EXECUTE_APPLY, arguments.getOperation());
        assertTrue(arguments.getOutputFile().isPresent());
        assertEquals("/tmp/output.json", arguments.getOutputFile().orElse(null));
        assertTrue(arguments.getRemainingArgs().isEmpty());
    }

    @Test
    void shouldParseAllDefinedParametersWithSpaceFormat() {
        String[] args = {
                "--flamingock.cli.mode", "true",
                "--flamingock.operation", "EXECUTE_ROLLBACK",
                "--flamingock.output-file", "/var/log/flamingock.log"
        };

        FlamingockArguments arguments = FlamingockArguments.parse(args);

        assertTrue(arguments.isCliMode());
        assertEquals(OperationType.EXECUTE_ROLLBACK, arguments.getOperation());
        assertEquals("/var/log/flamingock.log", arguments.getOutputFile().orElse(null));
    }

    @Test
    void shouldCollectRemainingArgs() {
        String[] args = {
                "--flamingock.cli.mode=true",
                "--custom.property=value1",
                "--another.setting", "value2"
        };

        FlamingockArguments arguments = FlamingockArguments.parse(args);

        assertEquals(2, arguments.getRemainingArgs().size());
        assertEquals("value1", arguments.getRemainingArgs().get("custom.property"));
        assertEquals("value2", arguments.getRemainingArgs().get("another.setting"));
    }

    @Test
    void shouldHandleNullArgs() {
        FlamingockArguments arguments = FlamingockArguments.parse(null);

        assertFalse(arguments.isCliMode());
        assertFalse(arguments.isOperationProvided());
        assertEquals(OperationType.EXECUTE_APPLY, arguments.getOperation());
        assertFalse(arguments.getOutputFile().isPresent());
        assertTrue(arguments.getRemainingArgs().isEmpty());
    }

    @Test
    void shouldHandleEmptyArgs() {
        FlamingockArguments arguments = FlamingockArguments.parse(new String[0]);

        assertFalse(arguments.isCliMode());
        assertFalse(arguments.isOperationProvided());
        assertEquals(OperationType.EXECUTE_APPLY, arguments.getOperation());
        assertFalse(arguments.getOutputFile().isPresent());
        assertTrue(arguments.getRemainingArgs().isEmpty());
    }

    @Test
    void shouldDefaultCliModeToFalse() {
        String[] args = {"--flamingock.operation=EXECUTE_APPLY"};

        FlamingockArguments arguments = FlamingockArguments.parse(args);

        assertFalse(arguments.isCliMode());
    }

    @Test
    void shouldTreatStandaloneBooleanFlagAsTrue() {
        String[] args = {"--flamingock.cli.mode"};

        FlamingockArguments arguments = FlamingockArguments.parse(args);

        assertTrue(arguments.isCliMode());
    }

    @Test
    void shouldTreatBooleanFlagFollowedByAnotherFlagAsTrue() {
        String[] args = {
                "--flamingock.cli.mode",
                "--flamingock.operation=EXECUTE_APPLY"
        };

        FlamingockArguments arguments = FlamingockArguments.parse(args);

        assertTrue(arguments.isCliMode());
        assertEquals(OperationType.EXECUTE_APPLY, arguments.getOperation());
    }

    @Test
    void shouldReturnDefaultOperationWhenNotProvided() {
        String[] args = {"--flamingock.cli.mode=true"};

        FlamingockArguments arguments = FlamingockArguments.parse(args);

        assertFalse(arguments.isOperationProvided());
        assertEquals(OperationType.EXECUTE_APPLY, arguments.getOperation());
    }

    @Test
    void shouldParseAllValidOperationTypes() {
        for (OperationType expectedType : OperationType.values()) {
            String[] args = {"--flamingock.operation=" + expectedType.name()};

            FlamingockArguments arguments = FlamingockArguments.parse(args);

            assertTrue(arguments.isOperationProvided());
            assertEquals(expectedType, arguments.getOperation(),
                    "Failed to parse operation type: " + expectedType);
        }
    }

    @Test
    void shouldParseOperationTypeCaseInsensitively() {
        String[] args = {"--flamingock.operation=execute_apply"};

        FlamingockArguments arguments = FlamingockArguments.parse(args);

        assertEquals(OperationType.EXECUTE_APPLY, arguments.getOperation());
    }

    @Test
    void shouldThrowExceptionForInvalidOperationType() {
        String[] args = {"--flamingock.operation=INVALID_OP"};

        ArgumentException exception = assertThrows(
                ArgumentException.class,
                () -> FlamingockArguments.parse(args)
        );

        assertTrue(exception.getMessage().contains("INVALID_OP"));
        assertTrue(exception.getMessage().contains("EXECUTE_APPLY"));
        assertTrue(exception.getMessage().contains("EXECUTE_ROLLBACK"));
    }

    @Test
    void shouldThrowExceptionForInvalidBooleanValue() {
        String[] args = {"--flamingock.cli.mode=yes"};

        ArgumentException exception = assertThrows(
                ArgumentException.class,
                () -> FlamingockArguments.parse(args)
        );

        assertTrue(exception.getMessage().contains("yes"));
        assertTrue(exception.getMessage().contains("true"));
        assertTrue(exception.getMessage().contains("false"));
    }

    @Test
    void shouldThrowExceptionForNonBooleanWithoutValue() {
        String[] args = {"--flamingock.operation"};

        ArgumentException exception = assertThrows(
                ArgumentException.class,
                () -> FlamingockArguments.parse(args)
        );

        assertTrue(exception.getMessage().contains("flamingock.operation"));
        assertTrue(exception.getMessage().contains("requires a value"));
    }

    @Test
    void shouldSkipNonPrefixedArguments() {
        String[] args = {
                "plainArg",
                "-singleDash",
                "--flamingock.cli.mode=true"
        };

        FlamingockArguments arguments = FlamingockArguments.parse(args);

        assertTrue(arguments.isCliMode());
        assertTrue(arguments.getRemainingArgs().isEmpty());
    }

    @Test
    void shouldHandleEmptyValue() {
        String[] args = {"--flamingock.output-file="};

        FlamingockArguments arguments = FlamingockArguments.parse(args);

        assertTrue(arguments.getOutputFile().isPresent());
        assertEquals("", arguments.getOutputFile().orElse(null));
    }

    @Test
    void shouldHandleValueWithEqualsSign() {
        String[] args = {"--flamingock.output-file=path=with=equals"};

        FlamingockArguments arguments = FlamingockArguments.parse(args);

        assertEquals("path=with=equals", arguments.getOutputFile().orElse(null));
    }

    @Test
    void shouldConsumeNextArgAsValueInSpaceFormat() {
        String[] args = {
                "--flamingock.output-file", "/tmp/file.json",
                "--flamingock.cli.mode"
        };

        FlamingockArguments arguments = FlamingockArguments.parse(args);

        assertEquals("/tmp/file.json", arguments.getOutputFile().orElse(null));
        assertTrue(arguments.isCliMode());
    }

    @Test
    void shouldTreatUnknownFlagWithoutValueAsBooleanTrue() {
        String[] args = {
                "--custom.flag",
                "--flamingock.cli.mode"
        };

        FlamingockArguments arguments = FlamingockArguments.parse(args);

        assertEquals("true", arguments.getRemainingArgs().get("custom.flag"));
        assertTrue(arguments.isCliMode());
    }

    @Test
    void shouldHandleMixedFormats() {
        String[] args = {
                "--flamingock.cli.mode=true",
                "--flamingock.operation", "EXECUTE_DRYRUN",
                "--flamingock.output-file=/output.json",
                "--custom.prop", "customValue"
        };

        FlamingockArguments arguments = FlamingockArguments.parse(args);

        assertTrue(arguments.isCliMode());
        assertEquals(OperationType.EXECUTE_DRYRUN, arguments.getOperation());
        assertEquals("/output.json", arguments.getOutputFile().orElse(null));
        assertEquals("customValue", arguments.getRemainingArgs().get("custom.prop"));
    }

    @Test
    void shouldThrowExceptionForOutputFileWithoutValue() {
        String[] args = {"--flamingock.output-file", "--flamingock.cli.mode"};

        ArgumentException exception = assertThrows(
                ArgumentException.class,
                () -> FlamingockArguments.parse(args)
        );

        assertTrue(exception.getMessage().contains("flamingock.output-file"));
        assertTrue(exception.getMessage().contains("requires a value"));
    }

    @Test
    void shouldReturnIsOperationProvidedTrueWhenOperationExplicitlyPassed() {
        String[] args = {"--flamingock.operation=EXECUTE_APPLY"};

        FlamingockArguments arguments = FlamingockArguments.parse(args);

        assertTrue(arguments.isOperationProvided());
        assertEquals(OperationType.EXECUTE_APPLY, arguments.getOperation());
    }

    @Test
    void shouldReturnIsOperationProvidedFalseWhenUsingDefault() {
        String[] args = {"--flamingock.cli.mode=true"};

        FlamingockArguments arguments = FlamingockArguments.parse(args);

        assertFalse(arguments.isOperationProvided());
        assertEquals(OperationType.EXECUTE_APPLY, arguments.getOperation());
    }

    // ========== Typed Accessor Methods Tests ==========

    @Nested
    class GetStringOrThrowTests {

        @Test
        void shouldReturnValueWhenPresent() {
            String[] args = {"--my.key=myValue"};
            FlamingockArguments arguments = FlamingockArguments.parse(args);

            String result = arguments.getStringOrThrow("my.key", "Key is required");

            assertEquals("myValue", result);
        }

        @Test
        void shouldThrowWhenKeyNotPresent() {
            FlamingockArguments arguments = FlamingockArguments.parse(new String[0]);

            ArgumentException exception = assertThrows(
                    ArgumentException.class,
                    () -> arguments.getStringOrThrow("missing.key", "Key is required")
            );

            assertEquals("Key is required", exception.getMessage());
        }

        @Test
        void shouldThrowWhenValueIsEmpty() {
            String[] args = {"--my.key="};
            FlamingockArguments arguments = FlamingockArguments.parse(args);

            ArgumentException exception = assertThrows(
                    ArgumentException.class,
                    () -> arguments.getStringOrThrow("my.key", "Key is required")
            );

            assertEquals("Key is required", exception.getMessage());
        }
    }

    @Nested
    class GetStringOrTests {

        @Test
        void shouldReturnValueWhenPresent() {
            String[] args = {"--my.key=myValue"};
            FlamingockArguments arguments = FlamingockArguments.parse(args);

            String result = arguments.getStringOr("my.key", "default");

            assertEquals("myValue", result);
        }

        @Test
        void shouldReturnDefaultWhenKeyNotPresent() {
            FlamingockArguments arguments = FlamingockArguments.parse(new String[0]);

            String result = arguments.getStringOr("missing.key", "defaultValue");

            assertEquals("defaultValue", result);
        }

        @Test
        void shouldReturnDefaultWhenValueIsEmpty() {
            String[] args = {"--my.key="};
            FlamingockArguments arguments = FlamingockArguments.parse(args);

            String result = arguments.getStringOr("my.key", "defaultValue");

            assertEquals("defaultValue", result);
        }

        @Test
        void shouldReturnNullDefaultWhenValueNotPresentAndDefaultIsNull() {
            FlamingockArguments arguments = FlamingockArguments.parse(new String[0]);

            String result = arguments.getStringOr("missing.key", null);

            assertNull(result);
        }
    }

    @Nested
    class GetBooleanOrTests {

        @Test
        void shouldReturnTrueWhenValueIsTrue() {
            String[] args = {"--my.flag=true"};
            FlamingockArguments arguments = FlamingockArguments.parse(args);

            boolean result = arguments.getBooleanOr("my.flag", false);

            assertTrue(result);
        }

        @Test
        void shouldReturnTrueWhenValueIsTrueCaseInsensitive() {
            String[] args = {"--my.flag=TRUE"};
            FlamingockArguments arguments = FlamingockArguments.parse(args);

            boolean result = arguments.getBooleanOr("my.flag", false);

            assertTrue(result);
        }

        @Test
        void shouldReturnFalseWhenValueIsFalse() {
            String[] args = {"--my.flag=false"};
            FlamingockArguments arguments = FlamingockArguments.parse(args);

            boolean result = arguments.getBooleanOr("my.flag", true);

            assertFalse(result);
        }

        @Test
        void shouldReturnFalseForNonTrueValue() {
            String[] args = {"--my.flag=yes"};
            FlamingockArguments arguments = FlamingockArguments.parse(args);

            boolean result = arguments.getBooleanOr("my.flag", true);

            assertFalse(result);
        }

        @Test
        void shouldReturnDefaultWhenKeyNotPresent() {
            FlamingockArguments arguments = FlamingockArguments.parse(new String[0]);

            assertTrue(arguments.getBooleanOr("missing.key", true));
            assertFalse(arguments.getBooleanOr("missing.key", false));
        }

        @Test
        void shouldReturnDefaultWhenValueIsEmpty() {
            String[] args = {"--my.flag="};
            FlamingockArguments arguments = FlamingockArguments.parse(args);

            boolean result = arguments.getBooleanOr("my.flag", true);

            assertTrue(result);
        }

        @Test
        void shouldReturnTrueForStandaloneFlag() {
            // When a flag is provided without a value, the parser sets it to "true"
            String[] args = {"--my.flag"};
            FlamingockArguments arguments = FlamingockArguments.parse(args);

            boolean result = arguments.getBooleanOr("my.flag", false);

            assertTrue(result);
        }
    }

    @Nested
    class GetDateTimeOrTests {

        @Test
        void shouldParseDateTimeWithTime() {
            String[] args = {"--my.date=2025-06-15T10:30:00"};
            FlamingockArguments arguments = FlamingockArguments.parse(args);

            LocalDateTime result = arguments.getDateTimeOr("my.date", null);

            assertEquals(LocalDateTime.of(2025, 6, 15, 10, 30, 0), result);
        }

        @Test
        void shouldParseDateOnly() {
            String[] args = {"--my.date=2025-06-15"};
            FlamingockArguments arguments = FlamingockArguments.parse(args);

            LocalDateTime result = arguments.getDateTimeOr("my.date", null);

            assertEquals(LocalDateTime.of(2025, 6, 15, 0, 0, 0), result);
        }

        @Test
        void shouldReturnDefaultWhenKeyNotPresent() {
            FlamingockArguments arguments = FlamingockArguments.parse(new String[0]);
            LocalDateTime defaultValue = LocalDateTime.of(2020, 1, 1, 0, 0);

            LocalDateTime result = arguments.getDateTimeOr("missing.key", defaultValue);

            assertEquals(defaultValue, result);
        }

        @Test
        void shouldReturnNullWhenKeyNotPresentAndDefaultIsNull() {
            FlamingockArguments arguments = FlamingockArguments.parse(new String[0]);

            LocalDateTime result = arguments.getDateTimeOr("missing.key", null);

            assertNull(result);
        }

        @Test
        void shouldReturnDefaultWhenValueIsEmpty() {
            String[] args = {"--my.date="};
            FlamingockArguments arguments = FlamingockArguments.parse(args);
            LocalDateTime defaultValue = LocalDateTime.of(2020, 1, 1, 0, 0);

            LocalDateTime result = arguments.getDateTimeOr("my.date", defaultValue);

            assertEquals(defaultValue, result);
        }

        @Test
        void shouldThrowForInvalidDateFormat() {
            String[] args = {"--my.date=not-a-date"};
            FlamingockArguments arguments = FlamingockArguments.parse(args);

            ArgumentException exception = assertThrows(
                    ArgumentException.class,
                    () -> arguments.getDateTimeOr("my.date", null)
            );

            assertTrue(exception.getMessage().contains("Invalid date format"));
            assertTrue(exception.getMessage().contains("not-a-date"));
            assertTrue(exception.getMessage().contains("my.date"));
        }
    }

    @Nested
    class GetEnumOrThrowTests {

        @Test
        void shouldReturnEnumValueWhenPresent() {
            String[] args = {"--my.enum=VALUE_ONE"};
            FlamingockArguments arguments = FlamingockArguments.parse(args);

            TestEnum result = arguments.getEnumOrThrow("my.enum", TestEnum.class, "Enum is required");

            assertEquals(TestEnum.VALUE_ONE, result);
        }

        @Test
        void shouldParseCaseInsensitively() {
            String[] args = {"--my.enum=value_two"};
            FlamingockArguments arguments = FlamingockArguments.parse(args);

            TestEnum result = arguments.getEnumOrThrow("my.enum", TestEnum.class, "Enum is required");

            assertEquals(TestEnum.VALUE_TWO, result);
        }

        @Test
        void shouldThrowWhenKeyNotPresent() {
            FlamingockArguments arguments = FlamingockArguments.parse(new String[0]);

            ArgumentException exception = assertThrows(
                    ArgumentException.class,
                    () -> arguments.getEnumOrThrow("missing.key", TestEnum.class, "Enum is required")
            );

            assertEquals("Enum is required", exception.getMessage());
        }

        @Test
        void shouldThrowWhenValueIsEmpty() {
            String[] args = {"--my.enum="};
            FlamingockArguments arguments = FlamingockArguments.parse(args);

            ArgumentException exception = assertThrows(
                    ArgumentException.class,
                    () -> arguments.getEnumOrThrow("my.enum", TestEnum.class, "Enum is required")
            );

            assertEquals("Enum is required", exception.getMessage());
        }

        @Test
        void shouldThrowWithValidValuesWhenInvalidEnumValue() {
            String[] args = {"--my.enum=INVALID"};
            FlamingockArguments arguments = FlamingockArguments.parse(args);

            ArgumentException exception = assertThrows(
                    ArgumentException.class,
                    () -> arguments.getEnumOrThrow("my.enum", TestEnum.class, "Enum is required.")
            );

            assertTrue(exception.getMessage().contains("Enum is required."));
            assertTrue(exception.getMessage().contains("Valid values:"));
            assertTrue(exception.getMessage().contains("VALUE_ONE"));
            assertTrue(exception.getMessage().contains("VALUE_TWO"));
            assertTrue(exception.getMessage().contains("VALUE_THREE"));
        }
    }
}
