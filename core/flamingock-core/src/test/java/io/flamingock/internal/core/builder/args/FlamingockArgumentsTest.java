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

import io.flamingock.internal.core.operation.OperationType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FlamingockArgumentsTest {

    @Test
    void shouldParseAllDefinedParametersWithEqualsFormat() {
        String[] args = {
                "--flamingock.cli.mode=true",
                "--flamingock.operation=EXECUTE",
                "--flamingock.output-file=/tmp/output.json"
        };

        FlamingockArguments arguments = FlamingockArguments.parse(args);

        assertTrue(arguments.isCliMode());
        assertTrue(arguments.isOperationProvided());
        assertEquals(OperationType.EXECUTE, arguments.getOperation());
        assertTrue(arguments.getOutputFile().isPresent());
        assertEquals("/tmp/output.json", arguments.getOutputFile().orElse(null));
        assertTrue(arguments.getRemainingArgs().isEmpty());
    }

    @Test
    void shouldParseAllDefinedParametersWithSpaceFormat() {
        String[] args = {
                "--flamingock.cli.mode", "true",
                "--flamingock.operation", "UNDO",
                "--flamingock.output-file", "/var/log/flamingock.log"
        };

        FlamingockArguments arguments = FlamingockArguments.parse(args);

        assertTrue(arguments.isCliMode());
        assertEquals(OperationType.UNDO, arguments.getOperation());
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
        assertEquals(OperationType.EXECUTE, arguments.getOperation());
        assertFalse(arguments.getOutputFile().isPresent());
        assertTrue(arguments.getRemainingArgs().isEmpty());
    }

    @Test
    void shouldHandleEmptyArgs() {
        FlamingockArguments arguments = FlamingockArguments.parse(new String[0]);

        assertFalse(arguments.isCliMode());
        assertFalse(arguments.isOperationProvided());
        assertEquals(OperationType.EXECUTE, arguments.getOperation());
        assertFalse(arguments.getOutputFile().isPresent());
        assertTrue(arguments.getRemainingArgs().isEmpty());
    }

    @Test
    void shouldDefaultCliModeToFalse() {
        String[] args = {"--flamingock.operation=EXECUTE"};

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
                "--flamingock.operation=EXECUTE"
        };

        FlamingockArguments arguments = FlamingockArguments.parse(args);

        assertTrue(arguments.isCliMode());
        assertEquals(OperationType.EXECUTE, arguments.getOperation());
    }

    @Test
    void shouldReturnDefaultOperationWhenNotProvided() {
        String[] args = {"--flamingock.cli.mode=true"};

        FlamingockArguments arguments = FlamingockArguments.parse(args);

        assertFalse(arguments.isOperationProvided());
        assertEquals(OperationType.EXECUTE, arguments.getOperation());
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
        String[] args = {"--flamingock.operation=execute"};

        FlamingockArguments arguments = FlamingockArguments.parse(args);

        assertEquals(OperationType.EXECUTE, arguments.getOperation());
    }

    @Test
    void shouldThrowExceptionForInvalidOperationType() {
        String[] args = {"--flamingock.operation=INVALID_OP"};

        ArgumentException exception = assertThrows(
                ArgumentException.class,
                () -> FlamingockArguments.parse(args)
        );

        assertTrue(exception.getMessage().contains("INVALID_OP"));
        assertTrue(exception.getMessage().contains("EXECUTE"));
        assertTrue(exception.getMessage().contains("UNDO"));
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
                "--flamingock.operation", "DRY_RUN",
                "--flamingock.output-file=/output.json",
                "--custom.prop", "customValue"
        };

        FlamingockArguments arguments = FlamingockArguments.parse(args);

        assertTrue(arguments.isCliMode());
        assertEquals(OperationType.DRY_RUN, arguments.getOperation());
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
        String[] args = {"--flamingock.operation=EXECUTE"};

        FlamingockArguments arguments = FlamingockArguments.parse(args);

        assertTrue(arguments.isOperationProvided());
        assertEquals(OperationType.EXECUTE, arguments.getOperation());
    }

    @Test
    void shouldReturnIsOperationProvidedFalseWhenUsingDefault() {
        String[] args = {"--flamingock.cli.mode=true"};

        FlamingockArguments arguments = FlamingockArguments.parse(args);

        assertFalse(arguments.isOperationProvided());
        assertEquals(OperationType.EXECUTE, arguments.getOperation());
    }
}
