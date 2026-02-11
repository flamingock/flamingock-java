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

import io.flamingock.internal.common.core.metadata.BuilderProviderInfo;
import io.flamingock.internal.common.core.metadata.FlamingockMetadata;
import io.flamingock.internal.common.core.util.Deserializer;
import io.flamingock.internal.core.builder.AbstractChangeRunnerBuilder;
import io.flamingock.internal.core.builder.runner.Runner;

import java.lang.reflect.Method;

/**
 * Main entry point for running Flamingock CLI operations in non-Spring Boot uber jars.
 *
 * <p>This class is invoked when the CLI detects a standard (non-Spring Boot) uber jar.
 * It uses the @FlamingockCliBuilder annotated method to obtain a configured builder,
 * applies CLI arguments, builds the runner, and executes it.
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
        try {
            // 1. Load metadata using existing Deserializer
            FlamingockMetadata metadata = Deserializer.readMetadataFromFile();

            // 2. Validate builder provider is configured (both class and method must be present)
            if (!metadata.hasValidBuilderProvider()) {
                printMissingBuilderProviderError();
                System.exit(1);
                return;
            }

            BuilderProviderInfo builderProvider = metadata.getBuilderProvider();

            // 3. Load class and invoke method via reflection
            Class<?> providerClass = Class.forName(builderProvider.getClassName());

            Method providerMethod;
            Object builderObj;

            if (builderProvider.isAcceptsArgs()) {
                // Method signature: methodName(String[] args)
                providerMethod = providerClass.getDeclaredMethod(
                    builderProvider.getMethodName(),
                    String[].class
                );
                providerMethod.setAccessible(true);
                builderObj = providerMethod.invoke(null, (Object) args);
            } else {
                // Method signature: methodName()
                providerMethod = providerClass.getDeclaredMethod(builderProvider.getMethodName());
                providerMethod.setAccessible(true);
                builderObj = providerMethod.invoke(null);
            }

            if (!(builderObj instanceof AbstractChangeRunnerBuilder)) {
                System.err.println("[Flamingock] @FlamingockCliBuilder method must return AbstractChangeRunnerBuilder or a subtype.");
                System.err.println("Found: " + (builderObj != null ? builderObj.getClass().getName() : "null"));
                System.exit(1);
                return;
            }

            @SuppressWarnings("unchecked")
            AbstractChangeRunnerBuilder<?, ?> builder = (AbstractChangeRunnerBuilder<?, ?>) builderObj;

            // 4. Apply CLI arguments and run
            builder.setApplicationArguments(args);
            Runner runner = builder.build();
            runner.run();

        } catch (ClassNotFoundException e) {
            System.err.println("[Flamingock] Builder provider class not found: " + e.getMessage());
            System.err.println("Ensure the class is included in your uber JAR.");
            System.exit(1);
        } catch (NoSuchMethodException e) {
            System.err.println("[Flamingock] Builder provider method not found: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("[Flamingock] Failed to execute: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void printMissingBuilderProviderError() {
        System.err.println("[Flamingock] No @FlamingockCliBuilder method found in your application.");
        System.err.println();
        System.err.println("To enable CLI support, add a static method annotated with @FlamingockCliBuilder");
        System.err.println("that returns your configured Flamingock builder:");
        System.err.println();
        System.err.println("  @FlamingockCliBuilder");
        System.err.println("  public static AbstractChangeRunnerBuilder flamingockBuilder() {");
        System.err.println("      return Flamingock.builder()");
        System.err.println("          .setAuditStore(auditStore)");
        System.err.println("          .addTargetSystem(targetSystem);");
        System.err.println("  }");
        System.err.println();
        System.err.println("Or, to receive CLI arguments during builder configuration:");
        System.err.println();
        System.err.println("  @FlamingockCliBuilder");
        System.err.println("  public static AbstractChangeRunnerBuilder flamingockBuilder(String[] args) {");
        System.err.println("      // args available for configuration");
        System.err.println("      return Flamingock.builder()");
        System.err.println("          .setAuditStore(auditStore)");
        System.err.println("          .addTargetSystem(targetSystem);");
        System.err.println("  }");
    }
}
