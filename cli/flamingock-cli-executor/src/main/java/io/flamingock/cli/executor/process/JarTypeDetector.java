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

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Detects the type of a JAR file to determine the appropriate execution strategy.
 *
 * <p>Detection logic for Spring Boot:
 * <ul>
 *   <li>Presence of any entry starting with {@code BOOT-INF/}</li>
 *   <li>Presence of any entry starting with {@code org/springframework/boot/loader/}</li>
 *   <li>Main-Class manifest attribute pointing to Spring Boot loader</li>
 * </ul>
 *
 * <p>If none of the above are detected, the JAR is classified as a plain uber JAR.
 */
public class JarTypeDetector {

    private static final String BOOT_INF_PREFIX = "BOOT-INF/";
    private static final String SPRING_BOOT_LOADER_PREFIX = "org/springframework/boot/loader/";
    private static final String SPRING_BOOT_LOADER_MAIN_CLASS_PREFIX = "org.springframework.boot.loader.";

    /**
     * Detects the type of the specified JAR file.
     *
     * @param jarPath the path to the JAR file
     * @return the detected JAR type
     * @throws JarDetectionException if the JAR cannot be analyzed
     */
    public JarType detect(String jarPath) throws JarDetectionException {
        return detect(new File(jarPath));
    }

    /**
     * Detects the type of the specified JAR file.
     *
     * @param jarFile the JAR file
     * @return the detected JAR type
     * @throws JarDetectionException if the JAR cannot be analyzed
     */
    public JarType detect(File jarFile) throws JarDetectionException {
        validateJarFile(jarFile);

        try (JarFile jar = new JarFile(jarFile)) {
            // Check manifest Main-Class first (fastest check)
            if (hasSpringBootMainClass(jar)) {
                return JarType.SPRING_BOOT;
            }

            // Check for Spring Boot structure in entries
            if (hasSpringBootEntries(jar)) {
                return JarType.SPRING_BOOT;
            }

            // Default to plain uber jar
            return JarType.PLAIN_UBER;

        } catch (IOException e) {
            throw new JarDetectionException(
                    "Cannot read JAR file: " + jarFile.getAbsolutePath() + " - " + e.getMessage(), e);
        }
    }

    private void validateJarFile(File jarFile) throws JarDetectionException {
        if (!jarFile.exists()) {
            throw new JarDetectionException("JAR file not found: " + jarFile.getAbsolutePath());
        }
        if (!jarFile.isFile()) {
            throw new JarDetectionException("Path is not a file: " + jarFile.getAbsolutePath());
        }
    }

    private boolean hasSpringBootMainClass(JarFile jar) throws IOException {
        Manifest manifest = jar.getManifest();
        if (manifest == null) {
            return false;
        }

        Attributes mainAttributes = manifest.getMainAttributes();
        String mainClass = mainAttributes.getValue(Attributes.Name.MAIN_CLASS);

        return mainClass != null && mainClass.startsWith(SPRING_BOOT_LOADER_MAIN_CLASS_PREFIX);
    }

    private boolean hasSpringBootEntries(JarFile jar) {
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();

            if (name.startsWith(BOOT_INF_PREFIX) || name.startsWith(SPRING_BOOT_LOADER_PREFIX)) {
                return true;
            }
        }
        return false;
    }
}
