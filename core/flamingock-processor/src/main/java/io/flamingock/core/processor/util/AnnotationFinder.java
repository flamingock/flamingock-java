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

import io.flamingock.api.annotations.EnableFlamingock;
import io.flamingock.internal.common.core.discover.ChangeDiscoverer;
import io.flamingock.internal.common.core.preview.CodePreviewChange;
import io.flamingock.internal.common.core.util.LoggerPreProcessor;

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

    public Optional<EnableFlamingock> getPipelineAnnotation() {
        logger.info("Searching for @EnableFlamingock annotation");
        return roundEnv.getElementsAnnotatedWith(EnableFlamingock.class)
                .stream()
                .filter(e -> e.getKind() == ElementKind.CLASS)
                .map(e -> (TypeElement) e)
                .map(e -> e.getAnnotation(EnableFlamingock.class))
                .findFirst();
    }

    public Collection<CodePreviewChange> findAnnotatedChanges() {
        logger.info("Searching for code-based changes");
        return getAllChangeDiscoverers()
                .stream()
                .peek(cd -> logger.info(String.format("Using %s for discover changes", cd.getClass().getName())))
                .map(cd -> cd.findAnnotatedChanges(roundEnv, logger))
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    private List<ChangeDiscoverer> getAllChangeDiscoverers() {
        Set<String> seen = new LinkedHashSet<>();
        List<ChangeDiscoverer> result = new ArrayList<>();

        ClassLoader[] loaders = new ClassLoader[] {
                Thread.currentThread().getContextClassLoader(),
                ChangeDiscoverer.class.getClassLoader(),
                ClassLoader.getSystemClassLoader()
        };

        for (ClassLoader cl : loaders) {
            if (cl == null) continue;
            ServiceLoader<ChangeDiscoverer> sl = ServiceLoader.load(ChangeDiscoverer.class, cl);
            for (ChangeDiscoverer d : sl) {
                if (seen.add(d.getClass().getName())) {
                    result.add(d);
                }
            }
        }
        return result;
    }

}