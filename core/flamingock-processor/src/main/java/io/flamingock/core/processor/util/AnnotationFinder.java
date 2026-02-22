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
import io.flamingock.api.annotations.FlamingockCliBuilder;
import io.flamingock.internal.common.core.processor.AnnotationProcessorPlugin;
import io.flamingock.internal.common.core.processor.ChangeDiscoverer;
import io.flamingock.internal.common.core.metadata.BuilderProviderInfo;
import io.flamingock.internal.common.core.preview.CodePreviewChange;
import io.flamingock.internal.common.core.util.LoggerPreProcessor;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import java.util.*;
import java.util.stream.Collectors;

public final class AnnotationFinder {

    private final LoggerPreProcessor logger;
    private final RoundEnvironment roundEnv;
    private final ProcessingEnvironment processingEnv;

    public AnnotationFinder(RoundEnvironment roundEnv, LoggerPreProcessor logger) {
        this(roundEnv, logger, null);
    }

    public AnnotationFinder(RoundEnvironment roundEnv, LoggerPreProcessor logger, ProcessingEnvironment processingEnv) {
        this.roundEnv = roundEnv;
        this.logger = logger;
        this.processingEnv = processingEnv;
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

    public Collection<CodePreviewChange> findAnnotatedChanges(List<ChangeDiscoverer> changeDiscoverers) {
        logger.info("Searching for code-based changes");
        return changeDiscoverers
                .stream()
                .peek(cd -> logger.info(String.format("Using %s for discover changes", cd.getClass().getName())))
                .map(ChangeDiscoverer::findAnnotatedChanges)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    /**
     * Finds the @FlamingockCliBuilder annotated method and validates it.
     *
     * @return Optional containing BuilderProviderInfo if found, empty otherwise
     * @throws RuntimeException if validation fails (multiple annotations, non-static, wrong return type, etc.)
     */
    public Optional<BuilderProviderInfo> findBuilderProvider() {
        logger.info("Searching for @FlamingockCliBuilder annotation");
        Set<? extends Element> annotatedMethods = roundEnv.getElementsAnnotatedWith(FlamingockCliBuilder.class);

        if (annotatedMethods.isEmpty()) {
            logger.verbose("No @FlamingockCliBuilder annotation found");
            return Optional.empty();
        }

        if (annotatedMethods.size() > 1) {
            throw new RuntimeException("Multiple @FlamingockCliBuilder annotations found. Only one is allowed.");
        }

        Element element = annotatedMethods.iterator().next();
        if (element.getKind() != ElementKind.METHOD) {
            throw new RuntimeException("@FlamingockCliBuilder must be placed on a method.");
        }

        ExecutableElement method = (ExecutableElement) element;

        // Validate: must be static
        if (!method.getModifiers().contains(Modifier.STATIC)) {
            throw new RuntimeException("@FlamingockCliBuilder method must be static.");
        }

        // Validate: must have 0 or 1 parameter (String[] args)
        boolean acceptsArgs = validateParameters(method);

        // Validate: return type must be compatible with AbstractChangeRunnerBuilder
        validateReturnType(method);

        TypeElement enclosingClass = (TypeElement) method.getEnclosingElement();
        String className = enclosingClass.getQualifiedName().toString();
        String methodName = method.getSimpleName().toString();

        String signature = acceptsArgs ? "(String[] args)" : "()";
        logger.info("Found @FlamingockCliBuilder method: " + className + "." + methodName + signature);
        return Optional.of(new BuilderProviderInfo(className, methodName, acceptsArgs));
    }

    /**
     * Validates the method parameters.
     * Allowed signatures:
     * - No parameters: methodName()
     * - One String[] parameter: methodName(String[] args)
     *
     * @param method the method to validate
     * @return true if the method accepts String[] args, false if no parameters
     * @throws RuntimeException if parameters are invalid
     */
    private boolean validateParameters(ExecutableElement method) {
        List<? extends VariableElement> params = method.getParameters();

        if (params.isEmpty()) {
            return false;
        }

        if (params.size() > 1) {
            throw new RuntimeException(
                "@FlamingockCliBuilder method must have 0 or 1 parameter (String[] args). " +
                "Found " + params.size() + " parameters."
            );
        }

        // Exactly one parameter - must be String[]
        VariableElement param = params.get(0);
        TypeMirror paramType = param.asType();

        if (paramType.getKind() != TypeKind.ARRAY) {
            throw new RuntimeException(
                "@FlamingockCliBuilder method parameter must be String[]. " +
                "Found: " + paramType.toString()
            );
        }

        ArrayType arrayType = (ArrayType) paramType;
        TypeMirror componentType = arrayType.getComponentType();

        if (!componentType.toString().equals("java.lang.String")) {
            throw new RuntimeException(
                "@FlamingockCliBuilder method parameter must be String[]. " +
                "Found: " + paramType.toString()
            );
        }

        return true;
    }

    private void validateReturnType(ExecutableElement method) {
        if (processingEnv == null) {
            // Skip validation if processingEnv is not available
            return;
        }

        TypeMirror returnType = method.getReturnType();

        // Check if return type is assignable to AbstractChangeRunnerBuilder
        TypeElement builderType = processingEnv.getElementUtils()
            .getTypeElement("io.flamingock.internal.core.builder.AbstractChangeRunnerBuilder");

        if (builderType != null) {
            Types types = processingEnv.getTypeUtils();
            TypeMirror builderTypeMirror = types.erasure(builderType.asType());
            TypeMirror erasedReturnType = types.erasure(returnType);

            if (!types.isAssignable(erasedReturnType, builderTypeMirror)) {
                throw new RuntimeException(
                    "@FlamingockCliBuilder method must return AbstractChangeRunnerBuilder or a subtype. " +
                    "Found: " + returnType.toString()
                );
            }
        }
        // If we can't find the builder type (shouldn't happen), we skip validation
    }

}