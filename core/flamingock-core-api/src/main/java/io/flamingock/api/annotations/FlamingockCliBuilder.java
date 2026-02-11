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
package io.flamingock.api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a static method that provides a configured Flamingock builder for CLI execution.
 *
 * <p>The annotated method must:
 * <ul>
 *   <li>Be static</li>
 *   <li>Take no parameters, OR take a single {@code String[] args} parameter</li>
 *   <li>Return AbstractChangeRunnerBuilder (or a subtype)</li>
 * </ul>
 *
 * <p>Example without arguments:
 * <pre>
 * &#64;FlamingockCliBuilder
 * public static AbstractChangeRunnerBuilder flamingockBuilder() {
 *     return Flamingock.builder()
 *         .setAuditStore(auditStore)
 *         .addTargetSystem(targetSystem);
 * }
 * </pre>
 *
 * <p>Example with arguments (for configuration based on CLI args):
 * <pre>
 * &#64;FlamingockCliBuilder
 * public static AbstractChangeRunnerBuilder flamingockBuilder(String[] args) {
 *     // args can be used during builder configuration
 *     return Flamingock.builder()
 *         .setAuditStore(auditStore)
 *         .addTargetSystem(targetSystem);
 * }
 * </pre>
 *
 * <p>The CLI will invoke this method to get the builder, add CLI arguments
 * via {@code setApplicationArguments(args)}, build, and run the Flamingock pipeline.
 *
 * <p><b>Note:</b> When using the {@code String[] args} parameter, you can access
 * the arguments during builder creation. The CLI will still call
 * {@code setApplicationArguments(args)} after your method returns, ensuring
 * Flamingock's internal argument parsing always occurs.
 *
 * @since 1.1.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FlamingockCliBuilder {
}
