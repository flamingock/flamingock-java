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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class FlamingockArguments {

    public static final String KEY_CLI_MODE = "flamingock.cli.mode";
    public static final String KEY_OPERATION = "flamingock.operation";
    public static final String KEY_OUTPUT_FILE = "flamingock.output-file";

    private final boolean cliMode;
    private final OperationType operation;
    private final String outputFile;
    private final Map<String, String> remainingArgs;

    private FlamingockArguments(boolean cliMode,
                                OperationType operation,
                                String outputFile,
                                Map<String, String> remainingArgs) {
        this.cliMode = cliMode;
        this.operation = operation;
        this.outputFile = outputFile;
        this.remainingArgs = Collections.unmodifiableMap(remainingArgs);
    }

    public static FlamingockArguments parse(String[] args) {
        if (args == null || args.length == 0) {
            return new FlamingockArguments(false, null, null, Collections.emptyMap());
        }

        boolean cliMode = false;
        OperationType operation = null;
        String outputFile = null;
        Map<String, String> remaining = new HashMap<>();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            if (arg == null || !arg.startsWith("--")) {
                continue;
            }

            String withoutPrefix = arg.substring(2);
            int equalsIndex = withoutPrefix.indexOf('=');

            String key;
            String value;

            if (equalsIndex > 0) {
                key = withoutPrefix.substring(0, equalsIndex);
                value = withoutPrefix.substring(equalsIndex + 1);
            } else if (equalsIndex == 0) {
                continue;
            } else {
                key = withoutPrefix;
                if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    value = args[++i];
                } else {
                    value = null;
                }
            }

            if (KEY_CLI_MODE.equals(key)) {
                cliMode = parseBoolean(key, value);
            } else if (KEY_OPERATION.equals(key)) {
                operation = parseOperationType(key, value);
            } else if (KEY_OUTPUT_FILE.equals(key)) {
                outputFile = requireValue(key, value);
            } else {
                if (value != null) {
                    remaining.put(key, value);
                } else {
                    remaining.put(key, "true");
                }
            }
        }

        return new FlamingockArguments(cliMode, operation, outputFile, remaining);
    }

    private static boolean parseBoolean(String key, String value) {
        if (value == null) {
            return true;
        }
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw new ArgumentException(
                "Invalid boolean value '%s' for parameter '%s'. Expected 'true' or 'false'.",
                value, key);
    }

    private static OperationType parseOperationType(String key, String value) {
        String requiredValue = requireValue(key, value);
        try {
            return OperationType.valueOf(requiredValue.toUpperCase());
        } catch (IllegalArgumentException e) {
            String validValues = Arrays.stream(OperationType.values())
                    .map(Enum::name)
                    .collect(Collectors.joining(", "));
            throw new ArgumentException(
                    "Invalid operation type '%s' for parameter '%s'. Valid values are: %s",
                    requiredValue, key, validValues);
        }
    }

    private static String requireValue(String key, String value) {
        if (value == null) {
            throw new ArgumentException(
                    "Parameter '%s' requires a value.", key);
        }
        return value;
    }

    public boolean isCliMode() {
        return cliMode;
    }

    public Optional<OperationType> getOperation() {
        return Optional.ofNullable(operation);
    }

    public Optional<String> getOutputFile() {
        return Optional.ofNullable(outputFile);
    }

    public Map<String, String> getRemainingArgs() {
        return remainingArgs;
    }

    public boolean hasOperation() {
        return operation != null;
    }

    public boolean hasOutputFile() {
        return outputFile != null;
    }
}
