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
package io.flamingock.core.cli;

/**
 * Main entry point for running Flamingock CLI operations in non-Spring Boot uber jars.
 *
 * <p>This class is invoked when the CLI detects a standard (non-Spring Boot) uber jar.
 *
 * <p>Arguments expected:
 * <ul>
 *   <li>--flamingock.cli.mode=true</li>
 *   <li>--flamingock.operation=EXECUTE|LIST|...</li>
 *   <li>--flamingock.output-file=/path/to/response.json</li>
 *   <li>--flamingock.log.level=DEBUG|INFO|... (optional)</li>
 * </ul>
 */
public class FlamingockCliMainEntryPoint {

    public static void main(String[] args) {
        System.err.println("[Flamingock] Non-Spring Boot CLI entry point not yet implemented.");
        System.err.println("[Flamingock] This feature is coming soon.");
        System.exit(1);
    }
}
