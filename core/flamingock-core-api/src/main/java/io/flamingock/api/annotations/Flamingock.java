/*
 * Copyright 2023 Flamingock (https://oss.flamingock.io)
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

import io.flamingock.api.SetupType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Core annotation for configuring Flamingock setup execution and framework integration.
 * This annotation must be placed on a class to enable Flamingock processing and define
 * how the pipeline should be configured and executed.
 * 
 * <h2>Pipeline Configuration</h2>
 * 
 * The annotation supports two mutually exclusive pipeline configuration modes:
 * 
 * <h3>1. File-based Configuration</h3>
 * Use {@link #pipelineFile()} to reference a YAML pipeline definition:
 * <pre>
 * &#64;Flamingock(pipelineFile = "config/pipeline.yaml")
 * public class MyMigrationConfig {
 *     // Configuration class
 * }
 * </pre>
 * 
 * <h3>2. Annotation-based Configuration</h3>
 * Use {@link #stages()} and {@link #systemStage()} to define the pipeline inline:
 * <pre>
 * &#64;Flamingock(
 *     systemStage = "com.example.system",
 *     stages = {
 *         &#64;Stage(type = StageType.LEGACY, location = "com.example.init"),
 *         &#64;Stage(location = "com.example.migrations")
 *     }
 * )
 * public class MyMigrationConfig {
 *     // Configuration class
 * }
 * </pre>
 * 
 * <h2>Framework Integration Setup</h2>
 * 
 * The {@link #setup()} field controls how Flamingock integrates with application frameworks:
 * 
 * <h3>DEFAULT Setup (Automatic Integration)</h3>
 * In framework environments like Spring Boot, Flamingock automatically registers and configures
 * the Flamingock runner bean based on configuration properties and annotation settings.
 * In standalone applications, behaves the same as BUILDER setup.
 * 
 * <pre>
 * &#64;Flamingock(
 *     setup = SetupType.DEFAULT,  // Default value - automatic framework integration
 *     stages = { &#64;Stage(location = "com.example.migrations") }
 * )
 * &#64;Configuration
 * public class FlamingockConfig {
 *     // Spring Boot will automatically create and configure the Flamingock runner
 *     // No manual bean configuration required
 * }
 * </pre>
 * 
 * <h3>BUILDER Setup (Manual Configuration)</h3>
 * Disables automatic framework integration, requiring manual Flamingock runner configuration.
 * Useful when you need full control over the Flamingock setup process.
 * 
 * <pre>
 * &#64;Flamingock(
 *     setup = SetupType.BUILDER,  // Manual configuration required
 *     stages = { &#64;Stage(location = "com.example.migrations") }
 * )
 * &#64;Configuration
 * public class FlamingockConfig {
 * 
 *     &#64;Bean
 *     public Flamingock flamingock(MongoTemplate mongoTemplate) {
 *         return FlamingockFactory.cloudBuilder()
 *             .setDriver(MongoSyncDriver.withDefaultLock(mongoTemplate))
 *             .buildRunner();
 *     }
 * }
 * </pre>
 * 
 * <h2>Validation Rules</h2>
 * <ul>
 *     <li>Either {@link #pipelineFile()} OR {@link #stages()} must be specified (mutually exclusive)</li>
 *     <li>{@link #systemStage()} can only be used with {@link #stages()}, not with {@link #pipelineFile()}</li>
 *     <li>At least one configuration mode must be provided</li>
 * </ul>
 * 
 * @since 1.0
 * @see Stage
 * @see SetupType
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Flamingock {
    
    /**
     * Configures the system stage location for system-level changes that run before all other stages.
     * The system stage is dedicated to infrastructure and foundational changes.
     * 
     * <p>Can only be used with annotation-based configuration ({@link #stages()}), 
     * not with file-based configuration ({@link #pipelineFile()}).
     * 
     * <p>The location format determines how it's interpreted:
     * <ul>
     *   <li><b>Package name:</b> Contains dots and no slashes (e.g., "com.example.system") - 
     *       Used to scan for annotated change units in the specified package</li>
     *   <li><b>Resource directory (relative):</b> Starts with "resources/" (e.g., "resources/system/migrations") - 
     *       Used to scan for template-based change units in the specified resources directory</li>
     *   <li><b>Resource directory (absolute):</b> Starts with "/" (e.g., "/absolute/path/to/system/templates") - 
     *       Used to scan for template-based change units in the specified absolute path</li>
     * </ul>
     * 
     * <p>Example:
     * <pre>
     * systemStage = "com.example.system"
     * </pre>
     * 
     * @return the system stage location, or empty string if no system stage
     */
    String systemStage() default "";
    
    /**
     * Defines the pipeline stages for annotation-based configuration.
     * Each stage represents a logical grouping of changes that execute in sequence.
     * 
     * <p>Mutually exclusive with {@link #pipelineFile()}. When using stages,
     * do not specify a pipeline file.
     * 
     * <p>Example:
     * <pre>
     * stages = {
     *     &#64;Stage(type = StageType.BEFORE, location = "com.example.setup"),
     *     &#64;Stage(type = StageType.DEFAULT, location = "com.example.changes"),
     *     &#64;Stage(type = StageType.AFTER, location = "com.example.cleanup")
     * }
     * </pre>
     * 
     * @return array of stage configurations
     * @see Stage
     */
    Stage[] stages() default {};

    /**
     * Specifies the path to a YAML pipeline configuration file for file-based configuration.
     * The file path supports both absolute paths and classpath resources.
     * 
     * <p>Mutually exclusive with {@link #stages()}. When using a pipeline file,
     * do not specify stages in the annotation.
     * 
     * <p>File resolution order:
     * <ol>
     *     <li>Direct file path (absolute or relative to working directory)</li>
     *     <li>Classpath resource in {@code src/main/resources/}</li>
     *     <li>Classpath resource in {@code src/test/resources/}</li>
     * </ol>
     * 
     * <p>Example:
     * <pre>
     * pipelineFile = "config/flamingock-pipeline.yaml"
     * </pre>
     * 
     * @return the pipeline file path, or empty string for annotation-based configuration
     */
    String pipelineFile() default "";

    /**
     * Controls how Flamingock integrates with application frameworks.
     * 
     * <p><b>DEFAULT</b> - Automatic framework integration:
     * <ul>
     *     <li>In Spring Boot: Flamingock automatically registers and configures the runner bean</li>
     *     <li>In standalone applications: Behaves the same as BUILDER</li>
     *     <li>Configuration is derived from application properties and annotation settings</li>
     * </ul>
     * 
     * <p><b>BUILDER</b> - Manual configuration required:
     * <ul>
     *     <li>Framework integration is disabled</li>
     *     <li>Developer must manually create and configure Flamingock beans</li>
     *     <li>Provides full control over the setup process</li>
     * </ul>
     * 
     * <p>Example with automatic setup (DEFAULT):
     * <pre>
     * &#64;Flamingock(setup = SetupType.DEFAULT)  // or omit for default
     * &#64;Configuration
     * public class Config {
     *     // Spring Boot auto-configures Flamingock
     * }
     * </pre>
     * 
     * <p>Example with manual setup (BUILDER):
     * <pre>
     * &#64;Flamingock(setup = SetupType.BUILDER)
     * &#64;Configuration  
     * public class Config {
     *     &#64;Bean
     *     public Flamingock flamingock(Driver driver) {
     *         return FlamingockFactory.cloudBuilder()
     *             .setDriver(driver)
     *             .buildRunner();
     *     }
     * }
     * </pre>
     * 
     * @return the setup type for framework integration
     * @see SetupType
     */
    SetupType setup() default SetupType.DEFAULT;
}