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
 * Represents the type of JAR file for determining the appropriate execution strategy.
 */
public enum JarType {

    /**
     * A Spring Boot executable JAR.
     * Detected by presence of BOOT-INF/, Spring Boot loader entries,
     * or Spring Boot Main-Class in manifest.
     */
    SPRING_BOOT,

    /**
     * A standard (non-Spring Boot) uber/shaded JAR with Flamingock runtime.
     * Executed using classpath with explicit main class.
     */
    PLAIN_UBER,

    /**
     * A JAR that does not contain the Flamingock CLI entry point.
     * This can occur when:
     * - User provided a thin JAR (dependencies not bundled)
     * - User built an uber JAR but flamingock-core was excluded/relocated
     */
    MISSING_FLAMINGOCK_RUNTIME
}
