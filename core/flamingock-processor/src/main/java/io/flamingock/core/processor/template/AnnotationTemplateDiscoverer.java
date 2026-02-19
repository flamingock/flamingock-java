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

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Discovers templates via {@code @ChangeTemplate} annotation during annotation processing.
 *
 * <p>This discoverer finds all classes annotated with {@code @ChangeTemplate} in the
 * current compilation round and extracts their metadata.
 */
public class AnnotationTemplateDiscoverer implements ChangeTemplateDiscoverer {

    private final RoundEnvironment roundEnv;
    private final LoggerPreProcessor logger;

    /**
     * Creates a new AnnotationTemplateDiscoverer.
     *
     * @param roundEnv the current annotation processing round environment
     * @param logger   the logger for diagnostic messages
     */
    public AnnotationTemplateDiscoverer(RoundEnvironment roundEnv, LoggerPreProcessor logger) {
        this.roundEnv = roundEnv;
        this.logger = logger;
    }

    @Override
    public Collection<TemplateMetadata> discover() {
        logger.verbose("Discovering templates via @ChangeTemplate annotation");

        Collection<TemplateMetadata> templates = roundEnv.getElementsAnnotatedWith(ChangeTemplate.class)
                .stream()
                .filter(e -> e.getKind() == ElementKind.CLASS)
                .map(e -> (TypeElement) e)
                .map(this::buildTemplateMetadata)
                .collect(Collectors.toList());

        logger.verbose("Discovered " + templates.size() + " templates via annotation");
        return templates;
    }

    /**
     * Builds TemplateMetadata from a TypeElement annotated with @ChangeTemplate.
     *
     * @param element the type element representing the template class
     * @return the extracted template metadata
     */
    private TemplateMetadata buildTemplateMetadata(TypeElement element) {
        ChangeTemplate annotation = element.getAnnotation(ChangeTemplate.class);
        String fullyQualifiedClassName = element.getQualifiedName().toString();
        String id = annotation.id();
        boolean multiStep = annotation.multiStep();

        logger.verbose("  Found template: " + id + " (" + fullyQualifiedClassName + ")");

        return new TemplateMetadata(id, multiStep, fullyQualifiedClassName);
    }
}
