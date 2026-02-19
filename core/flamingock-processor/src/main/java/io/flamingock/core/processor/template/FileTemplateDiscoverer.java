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
package io.flamingock.core.processor.template;

import io.flamingock.api.annotations.ChangeTemplate;
import io.flamingock.internal.common.core.template.TemplateMetadata;
import io.flamingock.internal.common.core.util.LoggerPreProcessor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Discovers templates via {@code META-INF/flamingock/templates} files on the classpath.
 *
 * <p>This discoverer reads template class names from files and loads them to extract
 * metadata from their {@code @ChangeTemplate} annotations. This enables external
 * template libraries to register their templates.
 *
 * <p>File format: One fully qualified class name per line, blank lines and lines
 * starting with '#' are ignored.
 */
public class FileTemplateDiscoverer implements ChangeTemplateDiscoverer {

    /**
     * The path to the templates registration file.
     */
    public static final String TEMPLATES_FILE_PATH = "META-INF/flamingock/templates";

    private final ClassLoader classLoader;
    private final LoggerPreProcessor logger;
    private Set<String> registeredClassNames;

    /**
     * Creates a new FileTemplateDiscoverer.
     *
     * @param classLoader the class loader to use for reading resources and loading classes
     * @param logger      the logger for diagnostic messages
     */
    public FileTemplateDiscoverer(ClassLoader classLoader, LoggerPreProcessor logger) {
        this.classLoader = classLoader;
        this.logger = logger;
    }

    @Override
    public Collection<TemplateMetadata> discover() {
        logger.verbose("Discovering templates via " + TEMPLATES_FILE_PATH);

        Set<String> classNames = readTemplateClassNamesFromFiles();
        this.registeredClassNames = classNames;

        List<TemplateMetadata> templates = classNames.stream()
                .map(this::loadAndBuildMetadata)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        logger.verbose("Discovered " + templates.size() + " templates via file");
        return templates;
    }

    /**
     * Returns the set of class names registered in template files.
     * This is used by the orchestrator to check for unregistered local templates.
     *
     * @return set of fully qualified class names from template files
     */
    public Set<String> getRegisteredClassNames() {
        if (registeredClassNames == null) {
            registeredClassNames = readTemplateClassNamesFromFiles();
        }
        return registeredClassNames;
    }

    /**
     * Reads all template class names from META-INF/flamingock/templates files.
     *
     * @return set of fully qualified class names
     */
    private Set<String> readTemplateClassNamesFromFiles() {
        Set<String> classNames = new HashSet<>();

        try {
            Enumeration<URL> resources = classLoader.getResources(TEMPLATES_FILE_PATH);
            while (resources.hasMoreElements()) {
                URL resourceUrl = resources.nextElement();
                classNames.addAll(readClassNamesFromFile(resourceUrl));
            }
        } catch (IOException e) {
            logger.warn("Failed to read template files: " + e.getMessage());
        }

        return classNames;
    }

    /**
     * Reads class names from a single template file.
     *
     * @param resourceUrl the URL of the resource file
     * @return list of class names from the file
     */
    private List<String> readClassNamesFromFile(URL resourceUrl) {
        List<String> classNames = new ArrayList<>();

        try (InputStream is = resourceUrl.openStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                // Skip empty lines and comments
                if (!line.isEmpty() && !line.startsWith("#")) {
                    classNames.add(line);
                }
            }
        } catch (IOException e) {
            logger.warn("Failed to read template file " + resourceUrl + ": " + e.getMessage());
        }

        return classNames;
    }

    /**
     * Loads a class and builds TemplateMetadata from its @ChangeTemplate annotation.
     *
     * @param className the fully qualified class name
     * @return the template metadata, or null if the class cannot be loaded or lacks annotation
     */
    private TemplateMetadata loadAndBuildMetadata(String className) {
        try {
            Class<?> clazz = Class.forName(className, false, classLoader);
            ChangeTemplate annotation = clazz.getAnnotation(ChangeTemplate.class);

            if (annotation == null) {
                logger.warn("Class " + className + " in templates file is missing @ChangeTemplate annotation");
                return null;
            }

            String id = annotation.id();
            boolean multiStep = annotation.multiStep();

            logger.verbose("  Found template: " + id + " (" + className + ")");

            return new TemplateMetadata(id, multiStep, className);
        } catch (ClassNotFoundException e) {
            logger.warn("Template class not found: " + className);
            return null;
        } catch (Exception e) {
            logger.warn("Failed to load template class " + className + ": " + e.getMessage());
            return null;
        }
    }
}
