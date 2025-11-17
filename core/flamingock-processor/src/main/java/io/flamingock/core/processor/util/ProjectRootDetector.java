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
package io.flamingock.core.processor.util;

import javax.annotation.processing.ProcessingEnvironment;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for detecting the project root directory during annotation processing.
 * <p>
 * This is particularly useful for Eclipse IDE compatibility, where the annotation processor's
 * working directory may differ from the project root, causing relative paths to fail.
 * <p>
 * Detection strategies (in order of priority):
 * <ol>
 *     <li>Search upward from current working directory for project markers</li>
 *     <li>Infer from annotation processor's CLASS_OUTPUT location</li>
 * </ol>
 * <p>
 * Project markers include: {@code build.gradle}, {@code build.gradle.kts}, {@code pom.xml}, {@code .git}
 *
 * @since 1.0.0
 */
public final class ProjectRootDetector {

    private static final int MAX_PARENT_LEVELS = 5;

    private ProjectRootDetector() {
        // Utility class - prevent instantiation
    }

    /**
     * Attempts to detect the project root directory using multiple strategies.
     * <p>
     * This method tries different approaches to find the project root and returns
     * the first successful detection. If all strategies fail, returns {@code null}.
     *
     * @param processingEnv the annotation processing environment
     * @return the project root directory, or {@code null} if detection fails
     */
    public static File detectProjectRoot(ProcessingEnvironment processingEnv) {
        // Strategy 1: Search from working directory
        File fromWorkingDir = findFromWorkingDirectory();
        if (fromWorkingDir != null) {
            return fromWorkingDir;
        }

        // Strategy 2: Infer from Filer's output location
        File fromFiler = findFromFilerOutput(processingEnv);
        if (fromFiler != null) {
            return fromFiler;
        }

        return null;
    }

    /**
     * Searches upward from the current working directory to find the project root.
     * <p>
     * Starts from {@code user.dir} system property and traverses up the directory
     * tree looking for project marker files. Stops after {@value #MAX_PARENT_LEVELS} levels
     * to avoid excessive searching.
     *
     * @return the project root directory, or {@code null} if not found
     */
    static File findFromWorkingDirectory() {
        try {
            File current = new File(System.getProperty("user.dir"));
            if (!current.exists()) {
                return null;
            }

            // Check current directory first
            if (isProjectRoot(current)) {
                return current;
            }

            // Search up the directory tree
            File parent = current.getParentFile();
            for (int level = 0; level < MAX_PARENT_LEVELS && parent != null; level++) {
                if (isProjectRoot(parent)) {
                    return parent;
                }
                parent = parent.getParentFile();
            }
        } catch (Exception e) {
            // Silently fail and try next strategy
        }

        return null;
    }

    /**
     * Attempts to infer the project root from the annotation processor's CLASS_OUTPUT location.
     * <p>
     * The CLASS_OUTPUT typically points to {@code build/classes/java/main} in Gradle projects
     * or {@code target/classes} in Maven projects. This method navigates up the directory tree
     * to find the project root.
     *
     * @param processingEnv the annotation processing environment
     * @return the project root directory, or {@code null} if inference fails
     */
    static File findFromFilerOutput(ProcessingEnvironment processingEnv) {
        try {
            FileObject resource = processingEnv.getFiler()
                    .getResource(StandardLocation.CLASS_OUTPUT, "", "dummy");

            File classOutput = new File(resource.toUri()).getParentFile();
            if (classOutput == null || !classOutput.exists()) {
                return null;
            }

            // Navigate up from build/classes/java/main or target/classes
            File current = classOutput.getParentFile();
            for (int level = 0; level < MAX_PARENT_LEVELS && current != null; level++) {
                if (isProjectRoot(current)) {
                    return current;
                }
                current = current.getParentFile();
            }
        } catch (Exception e) {
            // Silently fail - this is a fallback strategy
        }

        return null;
    }

    /**
     * Checks if the given directory is a project root by looking for marker files.
     * <p>
     * A directory is considered a project root if it contains any of:
     * <ul>
     *     <li>{@code build.gradle} - Gradle with Groovy DSL</li>
     *     <li>{@code build.gradle.kts} - Gradle with Kotlin DSL</li>
     *     <li>{@code pom.xml} - Maven project</li>
     *     <li>{@code .git} - Git repository root</li>
     * </ul>
     *
     * @param dir the directory to check
     * @return {@code true} if the directory appears to be a project root
     */
    static boolean isProjectRoot(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return false;
        }

        return new File(dir, "build.gradle").exists()
                || new File(dir, "build.gradle.kts").exists()
                || new File(dir, "pom.xml").exists()
                || new File(dir, ".git").exists();
    }

    /**
     * Converts a list of relative source paths to absolute paths based on the project root.
     * <p>
     * Example:
     * <pre>
     * projectRoot = /home/user/myproject
     * relativePaths = ["src/main/java", "src/main/kotlin"]
     * returns = ["/home/user/myproject/src/main/java", "/home/user/myproject/src/main/kotlin"]
     * </pre>
     *
     * @param projectRoot the project root directory
     * @param relativePaths list of relative paths
     * @return list of absolute paths
     */
    public static List<String> toAbsoluteSourcePaths(File projectRoot, List<String> relativePaths) {
        List<String> absolutePaths = new ArrayList<>();
        for (String relativePath : relativePaths) {
            File absolutePath = new File(projectRoot, relativePath);
            absolutePaths.add(absolutePath.getAbsolutePath());
        }
        return absolutePaths;
    }
}
