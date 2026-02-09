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
package io.flamingock.cli.executor.process;

/**
 * Represents the status of a JVM launch operation.
 */
public enum LaunchStatus {

    /**
     * The process launched and completed successfully (exit code 0).
     */
    SUCCESS,

    /**
     * The Flamingock CLI entry point class was not found in the JAR.
     * This typically means flamingock-core is not included in the uber JAR.
     */
    ENTRY_POINT_NOT_FOUND,

    /**
     * The process failed to start (e.g., java executable not found, IO error).
     */
    PROCESS_START_FAILED,

    /**
     * The process was interrupted while running.
     */
    PROCESS_INTERRUPTED,

    /**
     * The process completed but with a non-zero exit code.
     */
    PROCESS_FAILED,

    /**
     * Failed to analyze the JAR file.
     */
    JAR_ANALYSIS_FAILED,

    /**
     * The JAR does not contain the Flamingock CLI entry point.
     * This can occur when:
     * - User provided a thin JAR (dependencies not bundled)
     * - User built an uber JAR but flamingock-core was excluded/relocated
     */
    MISSING_FLAMINGOCK_RUNTIME
}
