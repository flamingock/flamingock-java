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

class ArgumentsProcessorTest {

    @Test
    void shouldParseAllDefinedParametersWithEqualsFormat() {
        String[] args = {
                "--flamingock.cli.mode=true",
                "--flamingock.operation=EXECUTE",
                "--flamingock.output-file=/tmp/output.json"
        };

        ArgumentsProcessor processor = ArgumentsProcessor.parse(args);

        assertTrue(processor.isCliMode());
        assertTrue(processor.hasOperation());
        assertEquals(OperationType.EXECUTE, processor.getOperation().orElse(null));
        assertTrue(processor.hasOutputFile());
        assertEquals("/tmp/output.json", processor.getOutputFile().orElse(null));
        assertTrue(processor.getRemainingArgs().isEmpty());
    }

    @Test
    void shouldParseAllDefinedParametersWithSpaceFormat() {
        String[] args = {
                "--flamingock.cli.mode", "true",
                "--flamingock.operation", "UNDO",
                "--flamingock.output-file", "/var/log/flamingock.log"
        };

        ArgumentsProcessor processor = ArgumentsProcessor.parse(args);

        assertTrue(processor.isCliMode());
        assertEquals(OperationType.UNDO, processor.getOperation().orElse(null));
        assertEquals("/var/log/flamingock.log", processor.getOutputFile().orElse(null));
    }

    @Test
    void shouldCollectRemainingArgs() {
        String[] args = {
                "--flamingock.cli.mode=true",
                "--custom.property=value1",
                "--another.setting", "value2"
        };

        ArgumentsProcessor processor = ArgumentsProcessor.parse(args);

        assertEquals(2, processor.getRemainingArgs().size());
        assertEquals("value1", processor.getRemainingArgs().get("custom.property"));
        assertEquals("value2", processor.getRemainingArgs().get("another.setting"));
    }

    @Test
    void shouldHandleNullArgs() {
        ArgumentsProcessor processor = ArgumentsProcessor.parse(null);

        assertFalse(processor.isCliMode());
        assertFalse(processor.hasOperation());
        assertFalse(processor.hasOutputFile());
        assertTrue(processor.getRemainingArgs().isEmpty());
    }

    @Test
    void shouldHandleEmptyArgs() {
        ArgumentsProcessor processor = ArgumentsProcessor.parse(new String[0]);

        assertFalse(processor.isCliMode());
        assertFalse(processor.hasOperation());
        assertFalse(processor.hasOutputFile());
        assertTrue(processor.getRemainingArgs().isEmpty());
    }

    @Test
    void shouldDefaultCliModeToFalse() {
        String[] args = {"--flamingock.operation=EXECUTE"};

        ArgumentsProcessor processor = ArgumentsProcessor.parse(args);

        assertFalse(processor.isCliMode());
    }

    @Test
    void shouldTreatStandaloneBooleanFlagAsTrue() {
        String[] args = {"--flamingock.cli.mode"};

        ArgumentsProcessor processor = ArgumentsProcessor.parse(args);

        assertTrue(processor.isCliMode());
    }

    @Test
    void shouldTreatBooleanFlagFollowedByAnotherFlagAsTrue() {
        String[] args = {
                "--flamingock.cli.mode",
                "--flamingock.operation=EXECUTE"
        };

        ArgumentsProcessor processor = ArgumentsProcessor.parse(args);

        assertTrue(processor.isCliMode());
        assertEquals(OperationType.EXECUTE, processor.getOperation().orElse(null));
    }

    @Test
    void shouldReturnEmptyOptionalForMissingOperation() {
        String[] args = {"--flamingock.cli.mode=true"};

        ArgumentsProcessor processor = ArgumentsProcessor.parse(args);

        assertFalse(processor.hasOperation());
        assertFalse(processor.getOperation().isPresent());
    }

    @Test
    void shouldParseAllValidOperationTypes() {
        for (OperationType expectedType : OperationType.values()) {
            String[] args = {"--flamingock.operation=" + expectedType.name()};

            ArgumentsProcessor processor = ArgumentsProcessor.parse(args);

            assertEquals(expectedType, processor.getOperation().orElse(null),
                    "Failed to parse operation type: " + expectedType);
        }
    }

    @Test
    void shouldParseOperationTypeCaseInsensitively() {
        String[] args = {"--flamingock.operation=execute"};

        ArgumentsProcessor processor = ArgumentsProcessor.parse(args);

        assertEquals(OperationType.EXECUTE, processor.getOperation().orElse(null));
    }

    @Test
    void shouldThrowExceptionForInvalidOperationType() {
        String[] args = {"--flamingock.operation=INVALID_OP"};

        ArgumentException exception = assertThrows(
                ArgumentException.class,
                () -> ArgumentsProcessor.parse(args)
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
                () -> ArgumentsProcessor.parse(args)
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
                () -> ArgumentsProcessor.parse(args)
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

        ArgumentsProcessor processor = ArgumentsProcessor.parse(args);

        assertTrue(processor.isCliMode());
        assertTrue(processor.getRemainingArgs().isEmpty());
    }

    @Test
    void shouldHandleEmptyValue() {
        String[] args = {"--flamingock.output-file="};

        ArgumentsProcessor processor = ArgumentsProcessor.parse(args);

        assertTrue(processor.hasOutputFile());
        assertEquals("", processor.getOutputFile().orElse(null));
    }

    @Test
    void shouldHandleValueWithEqualsSign() {
        String[] args = {"--flamingock.output-file=path=with=equals"};

        ArgumentsProcessor processor = ArgumentsProcessor.parse(args);

        assertEquals("path=with=equals", processor.getOutputFile().orElse(null));
    }

    @Test
    void shouldConsumeNextArgAsValueInSpaceFormat() {
        String[] args = {
                "--flamingock.output-file", "/tmp/file.json",
                "--flamingock.cli.mode"
        };

        ArgumentsProcessor processor = ArgumentsProcessor.parse(args);

        assertEquals("/tmp/file.json", processor.getOutputFile().orElse(null));
        assertTrue(processor.isCliMode());
    }

    @Test
    void shouldTreatUnknownFlagWithoutValueAsBooleanTrue() {
        String[] args = {
                "--custom.flag",
                "--flamingock.cli.mode"
        };

        ArgumentsProcessor processor = ArgumentsProcessor.parse(args);

        assertEquals("true", processor.getRemainingArgs().get("custom.flag"));
        assertTrue(processor.isCliMode());
    }

    @Test
    void shouldHandleMixedFormats() {
        String[] args = {
                "--flamingock.cli.mode=true",
                "--flamingock.operation", "DRY_RUN",
                "--flamingock.output-file=/output.json",
                "--custom.prop", "customValue"
        };

        ArgumentsProcessor processor = ArgumentsProcessor.parse(args);

        assertTrue(processor.isCliMode());
        assertEquals(OperationType.DRY_RUN, processor.getOperation().orElse(null));
        assertEquals("/output.json", processor.getOutputFile().orElse(null));
        assertEquals("customValue", processor.getRemainingArgs().get("custom.prop"));
    }

    @Test
    void shouldThrowExceptionForOutputFileWithoutValue() {
        String[] args = {"--flamingock.output-file", "--flamingock.cli.mode"};

        ArgumentException exception = assertThrows(
                ArgumentException.class,
                () -> ArgumentsProcessor.parse(args)
        );

        assertTrue(exception.getMessage().contains("flamingock.output-file"));
        assertTrue(exception.getMessage().contains("requires a value"));
    }
}
