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
package io.flamingock.core.processor.discover;

import io.flamingock.api.annotations.Change;
import io.flamingock.internal.common.core.util.LoggerPreProcessor;
import io.flamingock.internal.common.core.discover.ChangeDiscoverer;
import io.flamingock.internal.common.core.preview.CodePreviewChange;
import io.flamingock.internal.common.core.preview.builder.CodePreviewTaskBuilder;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class FlamingockChangeDiscoverer implements ChangeDiscoverer {

    @Override
    public Collection<CodePreviewChange> findAnnotatedChanges(RoundEnvironment roundEnv, LoggerPreProcessor logger) {
        logger.info("Searching for code-based changes (Java classes annotated with @Change annotation)");
        return roundEnv.getElementsAnnotatedWith(Change.class)
                .stream()
                .filter(e -> e.getKind() == ElementKind.CLASS)
                .map(e -> (TypeElement) e)
                .map(this::buildCodePreviewChange)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private CodePreviewChange buildCodePreviewChange(TypeElement typeElement) {
        return Optional.ofNullable(CodePreviewTaskBuilder.instance(typeElement).build())
                .map(CodePreviewChange.class::cast)
                .orElse(null);
    }
}
