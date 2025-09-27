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

import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.EnableFlamingock;
import io.flamingock.internal.common.core.preview.AbstractPreviewTask;
import io.flamingock.internal.common.core.preview.CodePreviewChange;
import io.flamingock.internal.common.core.preview.builder.PreviewTaskBuilder;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import java.util.*;
import java.util.stream.Collectors;

public final class AnnotationFinder {

    private final LoggerPreProcessor logger;
    private final RoundEnvironment roundEnv;

    public AnnotationFinder(RoundEnvironment roundEnv, LoggerPreProcessor logger) {
        this.roundEnv = roundEnv;
        this.logger = logger;
    }

    public Map<String, List<AbstractPreviewTask>> getCodedChangesMapByPackage() {
        logger.info("Searching for code-based changes (Java classes annotated with @Change annotation)");
        Collection<CodePreviewChange> allChanges = new LinkedList<>(findAnnotatedChanges());
        Map<String, List<AbstractPreviewTask>> mapByPackage = new HashMap<>();
        for(CodePreviewChange item: allChanges) {
            mapByPackage.compute(item.getSourcePackage(), (key, descriptors) -> {
                List<AbstractPreviewTask> newDescriptors;
                if(descriptors != null) {
                    newDescriptors = descriptors;
                } else {
                    newDescriptors = new ArrayList<>();
                }
                newDescriptors.add(item);
                return newDescriptors;
            });
        }
        return mapByPackage;
    }

    public Optional<EnableFlamingock> getPipelineAnnotation() {
        logger.info("Searching for @EnableFlamingock annotation");
        return roundEnv.getElementsAnnotatedWith(EnableFlamingock.class)
                .stream()
                .filter(e -> e.getKind() == ElementKind.CLASS)
                .map(e -> (TypeElement) e)
                .map(e -> e.getAnnotation(EnableFlamingock.class))
                .findFirst();
    }

    private Collection<CodePreviewChange> findAnnotatedChanges() {
        return roundEnv.getElementsAnnotatedWith(Change.class)
                .stream()
                .filter(e -> e.getKind() == ElementKind.CLASS)
                .map(e -> (TypeElement) e)
                .map(this::buildCodePreviewChange)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private CodePreviewChange buildCodePreviewChange(TypeElement typeElement) {
        return Optional.ofNullable(PreviewTaskBuilder.getCodeBuilder(typeElement).build())
                .map(CodePreviewChange.class::cast)
                .orElse(null);
    }
}