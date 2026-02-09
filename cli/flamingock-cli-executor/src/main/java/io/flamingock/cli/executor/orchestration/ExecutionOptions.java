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
package io.flamingock.cli.executor.orchestration;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Encapsulates options for command execution.
 */
public class ExecutionOptions {

    private final String logLevel;
    private final boolean streamOutput;
    private final Map<String, String> operationArgs;

    private ExecutionOptions(Builder builder) {
        this.logLevel = builder.logLevel;
        this.streamOutput = builder.streamOutput;
        this.operationArgs = Collections.unmodifiableMap(new HashMap<>(builder.operationArgs));
    }

    /**
     * Returns the log level, or null if not set.
     *
     * @return the log level
     */
    public String getLogLevel() {
        return logLevel;
    }

    /**
     * Returns whether to stream output to console.
     *
     * @return true if output should be streamed
     */
    public boolean isStreamOutput() {
        return streamOutput;
    }

    /**
     * Returns additional operation-specific arguments to pass to the JAR.
     *
     * @return the operation arguments map (unmodifiable)
     */
    public Map<String, String> getOperationArgs() {
        return operationArgs;
    }

    /**
     * Creates a new builder.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for ExecutionOptions.
     */
    public static class Builder {
        private String logLevel;
        private boolean streamOutput = true;
        private Map<String, String> operationArgs = new HashMap<>();

        private Builder() {
        }

        /**
         * Sets the log level.
         *
         * @param logLevel the log level
         * @return this builder
         */
        public Builder logLevel(String logLevel) {
            this.logLevel = logLevel;
            return this;
        }

        /**
         * Sets whether to stream output.
         *
         * @param streamOutput true to stream output
         * @return this builder
         */
        public Builder streamOutput(boolean streamOutput) {
            this.streamOutput = streamOutput;
            return this;
        }

        /**
         * Sets additional operation-specific arguments.
         *
         * @param operationArgs the operation arguments
         * @return this builder
         */
        public Builder operationArgs(Map<String, String> operationArgs) {
            this.operationArgs = operationArgs != null ? operationArgs : new HashMap<>();
            return this;
        }

        /**
         * Adds a single operation argument.
         *
         * @param key   the argument key
         * @param value the argument value
         * @return this builder
         */
        public Builder operationArg(String key, String value) {
            this.operationArgs.put(key, value);
            return this;
        }

        /**
         * Builds the ExecutionOptions.
         *
         * @return the built options
         */
        public ExecutionOptions build() {
            return new ExecutionOptions(this);
        }
    }
}
