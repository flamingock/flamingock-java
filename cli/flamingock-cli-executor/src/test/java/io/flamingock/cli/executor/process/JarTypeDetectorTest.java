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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for JarTypeDetector.
 */
class JarTypeDetectorTest {

    @TempDir
    Path tempDir;

    private JarTypeDetector detector;

    @BeforeEach
    void setUp() {
        detector = new JarTypeDetector();
    }

    @Test
    void detect_springBootJar_withBootInf_returnsSpringBoot() throws Exception {
        File jar = createJarWithEntries("spring-boot.jar",
                null,
                "BOOT-INF/classes/",
                "BOOT-INF/lib/");

        JarType result = detector.detect(jar);

        assertEquals(JarType.SPRING_BOOT, result);
    }

    @Test
    void detect_springBootJar_withLoaderClasses_returnsSpringBoot() throws Exception {
        File jar = createJarWithEntries("spring-boot-loader.jar",
                null,
                "org/springframework/boot/loader/JarLauncher.class",
                "org/springframework/boot/loader/LaunchedURLClassLoader.class");

        JarType result = detector.detect(jar);

        assertEquals(JarType.SPRING_BOOT, result);
    }

    @Test
    void detect_springBootJar_byManifest_returnsSpringBoot() throws Exception {
        Manifest manifest = createManifest("org.springframework.boot.loader.JarLauncher");
        File jar = createJarWithEntries("spring-boot-manifest.jar",
                manifest,
                "com/example/Application.class");

        JarType result = detector.detect(jar);

        assertEquals(JarType.SPRING_BOOT, result);
    }

    @Test
    void detect_springBootJar_withPropertiesLauncher_returnsSpringBoot() throws Exception {
        Manifest manifest = createManifest("org.springframework.boot.loader.PropertiesLauncher");
        File jar = createJarWithEntries("spring-boot-properties.jar",
                manifest,
                "com/example/Application.class");

        JarType result = detector.detect(jar);

        assertEquals(JarType.SPRING_BOOT, result);
    }

    @Test
    void detect_plainUberJar_withFlamingockEntryPoint_returnsPlainUber() throws Exception {
        Manifest manifest = createManifest("com.example.Application");
        File jar = createJarWithEntries("plain-uber.jar",
                manifest,
                "com/example/Application.class",
                "com/example/Service.class",
                "io/flamingock/core/cli/FlamingockCliMainEntryPoint.class");

        JarType result = detector.detect(jar);

        assertEquals(JarType.PLAIN_UBER, result);
    }

    @Test
    void detect_jarWithoutFlamingockEntryPoint_returnsMissingRuntime() throws Exception {
        Manifest manifest = createManifest("com.example.Application");
        File jar = createJarWithEntries("missing-runtime.jar",
                manifest,
                "com/example/Application.class",
                "com/example/Service.class");

        JarType result = detector.detect(jar);

        assertEquals(JarType.MISSING_FLAMINGOCK_RUNTIME, result);
    }

    @Test
    void detect_emptyJar_returnsMissingRuntime() throws Exception {
        File jar = createJarWithEntries("empty.jar", null);

        JarType result = detector.detect(jar);

        assertEquals(JarType.MISSING_FLAMINGOCK_RUNTIME, result);
    }

    @Test
    void detect_jarWithNoManifest_andNoFlamingockEntryPoint_returnsMissingRuntime() throws Exception {
        File jar = createJarWithEntries("no-manifest.jar",
                null,
                "com/example/Application.class");

        JarType result = detector.detect(jar);

        assertEquals(JarType.MISSING_FLAMINGOCK_RUNTIME, result);
    }

    @Test
    void detect_thinJar_withOnlyAppClasses_returnsMissingRuntime() throws Exception {
        // Simulates a thin JAR - only app classes, no dependencies bundled
        Manifest manifest = createManifest("com.example.Application");
        File jar = createJarWithEntries("thin-jar.jar",
                manifest,
                "com/example/Application.class",
                "com/example/Service.class",
                "com/example/Repository.class");

        JarType result = detector.detect(jar);

        assertEquals(JarType.MISSING_FLAMINGOCK_RUNTIME, result);
    }

    @Test
    void detect_uberJar_withFlamingockCore_butMissingEntryPoint_returnsMissingRuntime() throws Exception {
        // Simulates uber JAR where flamingock-core was relocated or entry point excluded
        Manifest manifest = createManifest("com.example.Application");
        File jar = createJarWithEntries("relocated-flamingock.jar",
                manifest,
                "com/example/Application.class",
                "io/flamingock/core/SomeOtherClass.class",  // Has flamingock-core but not entry point
                "io/flamingock/api/SomeApiClass.class");

        JarType result = detector.detect(jar);

        assertEquals(JarType.MISSING_FLAMINGOCK_RUNTIME, result);
    }

    @Test
    void detect_nonExistentJar_throwsException() {
        File nonExistent = new File(tempDir.toFile(), "does-not-exist.jar");

        JarDetectionException exception = assertThrows(JarDetectionException.class,
                () -> detector.detect(nonExistent));

        assertTrue(exception.getMessage().contains("JAR file not found"));
    }

    @Test
    void detect_directoryInsteadOfFile_throwsException() {
        File directory = tempDir.toFile();

        JarDetectionException exception = assertThrows(JarDetectionException.class,
                () -> detector.detect(directory));

        assertTrue(exception.getMessage().contains("Path is not a file"));
    }

    @Test
    void detect_corruptJar_throwsException() throws Exception {
        File corruptJar = new File(tempDir.toFile(), "corrupt.jar");
        try (FileOutputStream fos = new FileOutputStream(corruptJar)) {
            fos.write("not a valid jar file".getBytes());
        }

        JarDetectionException exception = assertThrows(JarDetectionException.class,
                () -> detector.detect(corruptJar));

        assertTrue(exception.getMessage().contains("Cannot read JAR file"));
    }

    @Test
    void detect_stringPath_works() throws Exception {
        Manifest manifest = createManifest("com.example.Application");
        File jar = createJarWithEntries("string-path.jar",
                manifest,
                "com/example/Application.class",
                "io/flamingock/core/cli/FlamingockCliMainEntryPoint.class");

        JarType result = detector.detect(jar.getAbsolutePath());

        assertEquals(JarType.PLAIN_UBER, result);
    }

    private File createJarWithEntries(String name, Manifest manifest, String... entryNames) throws IOException {
        File jarFile = new File(tempDir.toFile(), name);

        Manifest m = manifest;
        if (m == null) {
            m = new Manifest();
            m.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        }

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarFile), m)) {
            for (String entryName : entryNames) {
                JarEntry entry = new JarEntry(entryName);
                jos.putNextEntry(entry);
                // Write a dummy byte for class files
                if (entryName.endsWith(".class")) {
                    jos.write(0xCA);
                    jos.write(0xFE);
                    jos.write(0xBA);
                    jos.write(0xBE);
                }
                jos.closeEntry();
            }
        }

        return jarFile;
    }

    private Manifest createManifest(String mainClass) {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, mainClass);
        return manifest;
    }
}
