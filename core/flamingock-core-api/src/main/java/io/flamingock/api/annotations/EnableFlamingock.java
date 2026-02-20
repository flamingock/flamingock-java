/*
 * Copyright 2023 Flamingock (https://www.flamingock.io)
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
 * Core annotation for configuring Flamingock pipeline execution.
 * This annotation must be placed on a class to enable Flamingock processing and define
 * how the pipeline should be configured.
 *
 * <h2>Pipeline Configuration</h2>
 *
 * The annotation supports two mutually exclusive pipeline configuration modes:
 *
 * <h3>1. File-based Configuration</h3>
 * Use {@link #configFile()} to reference a YAML pipeline definition:
 * <pre>
 * &#64;EnableFlamingock(configFile = "config/pipeline.yaml")
 * public class MyMigrationConfig {
 *     // Configuration class
 * }
 * </pre>
 *
 * <h3>2. Annotation-based Configuration</h3>
 * Use {@link #stages()} to define the pipeline inline:
 * <pre>
 * &#64;EnableFlamingock(
 *     stages = {
 *         &#64;Stage(type = StageType.SYSTEM, location = "com.example.system"),
 *         &#64;Stage(type = StageType.LEGACY, location = "com.example.init"),
 *         &#64;Stage(location = "com.example.migrations")
 *     }
 * )
 * public class MyMigrationConfig {
 *     // Configuration class
 * }
 * </pre>
 *
 * <h2>Validation Rules</h2>
 * <ul>
 *     <li>Either {@link #configFile()} OR {@link #stages()} must be specified (mutually exclusive)</li>
 *     <li>At least one configuration mode must be provided</li>
 *     <li>Maximum of 1 stage with type {@code StageType.SYSTEM} is allowed</li>
 *     <li>Maximum of 1 stage with type {@code StageType.LEGACY} is allowed</li>
 * </ul>
 *
 * @since 1.0
 * @see Stage
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface EnableFlamingock {


    /**
     * Defines the pipeline stages.
     * Each stage represents a logical grouping of changes that execute in sequence.
     *
     * <p>Mutually exclusive with {@link #configFile()}. When using stages,
     * do not specify a pipeline file.
     *
     * <p>Stage type restrictions:
     * <ul>
     *   <li>Maximum of 1 stage with type {@code StageType.SYSTEM} is allowed</li>
     *   <li>Maximum of 1 stage with type {@code StageType.LEGACY} is allowed</li>
     *   <li>Unlimited stages with type {@code StageType.DEFAULT} are allowed</li>
     * </ul>
     *
     * <p>Example:
     * <pre>
     * stages = {
     *     &#64;Stage(type = StageType.SYSTEM, location = "com.example.system"),
     *     &#64;Stage(type = StageType.LEGACY, location = "com.example.init"),
     *     &#64;Stage(type = StageType.DEFAULT, location = "com.example.changes")
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
     * configFile = "config/flamingock-pipeline.yaml"
     * </pre>
     *
     * @return the pipeline file path, or empty string for annotation-based configuration
     */
    String configFile() default "";

    /**
     * If true, the annotation processor will validate that all code-based changes
     * (classes annotated with @Change) are mapped to some stage. When unmapped changes
     * are found and this flag is true**(default)**, a RuntimeException is thrown at compilation time.
     * When false, only a warning is emitted.
     */
    boolean strictStageMapping() default true;

}
