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
package io.flamingock.common.test.processor;

import javax.annotation.processing.Processor;
import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A helper class for compiling Java source files with annotation processors during tests.
 */
public class JavacHelper {
    private final JavaCompiler compiler;

    /**
     * Initializes a new instance of the JavacHelper class and retrieves the system Java compiler.
     * Throws an assertion error if the system Java compiler is not available (e.g., when running on a JRE instead of a JDK).
     */
    public JavacHelper() {
        this.compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(this.compiler, "Tests must run on a JDK with a system Java compiler");
    }

    /**
     * Compiles the Java source file at the specified path using the provided annotation processors.
     *
     * @param sourcePath the path to the Java source file to compile
     * @param processors the annotation processors to use during compilation
     * @throws IOException if an I/O error occurs during compilation
     */
    public void compile(Path sourcePath, Processor... processors) throws IOException {
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager =
                     compiler.getStandardFileManager(diagnostics, null, null)) {
            fileManager.setLocation(StandardLocation.CLASS_PATH, currentClasspath());

            JavaCompiler.CompilationTask task = compiler.getTask(
                    null,
                    fileManager,
                    diagnostics,
                    Collections.singletonList("-proc:only"),
                    null,
                    fileManager.getJavaFileObjects(sourcePath.toFile())
            );
            task.setProcessors(Arrays.asList(processors));
            assertTrue(task.call(), () -> diagnostics.getDiagnostics().toString());
        }
    }

    /**
     * Retrieves the source path of a resource relative to the specified class.
     *
     * @param klass        the class relative to which the resource path is specified
     * @param resourcePath the resource path to retrieve
     * @return the Path of the resource
     * @throws URISyntaxException if the resource URL cannot be converted to a URI
     * @throws AssertionError     if the resource is not found
     */
    public static Path getSourcePath(Class<?> klass, String resourcePath) throws URISyntaxException {
        URL sourceResource = klass.getResource(resourcePath);
        assertNotNull(sourceResource, "Source resource not found: " + resourcePath);
        return Paths.get(sourceResource.toURI());
    }

    private static List<File> currentClasspath() {
        return Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
                .map(File::new)
                .collect(Collectors.toList());
    }
}
