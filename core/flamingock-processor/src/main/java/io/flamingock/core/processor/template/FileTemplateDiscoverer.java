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

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
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
    private final ProcessingEnvironment processingEnv;

    /**
     * Creates a new FileTemplateDiscoverer.
     *
     * @param classLoader   the class loader to use for reading resources and loading classes
     * @param logger        the logger for diagnostic messages
     * @param processingEnv the annotation processing environment for accessing the compilation classpath
     */
    public FileTemplateDiscoverer(ClassLoader classLoader, LoggerPreProcessor logger, ProcessingEnvironment processingEnv) {
        this.classLoader = classLoader;
        this.logger = logger;
        this.processingEnv = processingEnv;
    }

    @Override
    public Collection<TemplateMetadata> discover() {
        logger.verbose("Discovering templates via " + TEMPLATES_FILE_PATH);

        Set<String> classNames = readTemplateClassNamesFromFiles();

        List<TemplateMetadata> templates = classNames.stream()
                .map(this::loadAndBuildMetadata)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        logger.verbose("Discovered " + templates.size() + " templates via file");
        return templates;
    }

    /**
     * Reads all template class names from META-INF/flamingock/templates files.
     *
     * <p>Uses two strategies:
     * <ol>
     *   <li>The annotation processing Filer API ({@code StandardLocation.CLASS_PATH}),
     *       which sees the compilation classpath including dependency JARs and the
     *       project's own main output.</li>
     *   <li>The thread context classloader as a fallback.</li>
     * </ol>
     *
     * @return set of fully qualified class names
     */
    private Set<String> readTemplateClassNamesFromFiles() {
        Set<String> classNames = new HashSet<>();

        // Strategy 1: Use Filer API to read from the compilation classpath.
        // This is the reliable way to access resources from dependencies and the
        // project's own main classes during annotation processing.
        classNames.addAll(readClassNamesViaFiler());

        // Strategy 2: Fall back to classloader for any additional entries.
        classNames.addAll(readClassNamesViaClassLoader());

        return classNames;
    }

    /**
     * Reads template class names via the annotation processing Filer API.
     *
     * @return set of class names found via Filer, or empty set on failure
     */
    private Set<String> readClassNamesViaFiler() {
        Set<String> classNames = new HashSet<>();
        if (processingEnv == null) {
            return classNames;
        }
        try {
            FileObject resource = processingEnv.getFiler()
                    .getResource(StandardLocation.CLASS_PATH, "", TEMPLATES_FILE_PATH);
            try (InputStream is = resource.openInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        classNames.add(line);
                    }
                }
            }
            logger.verbose("Found " + classNames.size() + " template class names via Filer API");
        } catch (Exception e) {
            logger.verbose("No templates found via Filer API: " + e.getMessage());
        }
        return classNames;
    }

    /**
     * Reads template class names via the classloader.
     *
     * @return set of class names found via classloader
     */
    private Set<String> readClassNamesViaClassLoader() {
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
     * <p>Uses two strategies:
     * <ol>
     *   <li>Reflective class loading via {@code Class.forName()}</li>
     *   <li>Annotation processing mirror API via {@code Elements.getTypeElement()},
     *       which can resolve classes on the compilation classpath even when the
     *       annotation processor's classloader cannot load them</li>
     * </ol>
     *
     * @param className the fully qualified class name
     * @return the template metadata, or null if the class cannot be loaded or lacks annotation
     */
    private TemplateMetadata loadAndBuildMetadata(String className) {
        // Strategy 1: Try reflective class loading
        TemplateMetadata result = loadViaReflection(className);
        if (result != null) {
            return result;
        }

        // Strategy 2: Try annotation processing mirror API
        result = loadViaMirrorApi(className);
        if (result != null) {
            return result;
        }

        logger.warn("Template class not found: " + className);
        return null;
    }

    private TemplateMetadata loadViaReflection(String className) {
        try {
            Class<?> clazz = Class.forName(className, false, classLoader);
            ChangeTemplate annotation = clazz.getAnnotation(ChangeTemplate.class);

            if (annotation == null) {
                logger.warn("Class " + className + " in templates file is missing @ChangeTemplate annotation");
                return null;
            }

            logger.verbose("  Found template via reflection: " + annotation.id() + " (" + className + ")");
            return new TemplateMetadata(annotation.id(), annotation.multiStep(), className, true);
        } catch (ClassNotFoundException e) {
            return null;
        } catch (Exception e) {
            logger.verbose("Failed to load template class via reflection " + className + ": " + e.getMessage());
            return null;
        }
    }

    private TemplateMetadata loadViaMirrorApi(String className) {
        if (processingEnv == null) {
            return null;
        }
        try {
            TypeElement typeElement = processingEnv.getElementUtils().getTypeElement(className);
            if (typeElement == null) {
                return null;
            }

            ChangeTemplate annotation = typeElement.getAnnotation(ChangeTemplate.class);
            if (annotation == null) {
                logger.warn("Class " + className + " in templates file is missing @ChangeTemplate annotation");
                return null;
            }

            logger.verbose("  Found template via mirror API: " + annotation.id() + " (" + className + ")");
            return new TemplateMetadata(annotation.id(), annotation.multiStep(), className, true);
        } catch (Exception e) {
            logger.verbose("Failed to load template class via mirror API " + className + ": " + e.getMessage());
            return null;
        }
    }
}
