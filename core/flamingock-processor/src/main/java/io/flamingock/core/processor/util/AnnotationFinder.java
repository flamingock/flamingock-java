package io.flamingock.core.processor.util;

import io.flamingock.api.annotations.ChangeUnit;
import io.flamingock.api.annotations.Flamingock;
import io.flamingock.internal.common.core.preview.AbstractPreviewTask;
import io.flamingock.internal.common.core.preview.CodePreviewChangeUnit;
import io.flamingock.internal.common.core.preview.builder.PreviewTaskBuilder;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class AnnotationFinder {

    private final LoggerPreProcessor logger;
    private final RoundEnvironment roundEnv;

    public AnnotationFinder(RoundEnvironment roundEnv, LoggerPreProcessor logger) {
        this.roundEnv = roundEnv;
        this.logger = logger;
    }

    public Map<String, List<AbstractPreviewTask>> getCodedChangeUnitsMapByPackage() {
        logger.info("Searching for code-based changes (Java classes annotated with @ChangeUnit annotation)");
        Collection<CodePreviewChangeUnit> allChanges = new LinkedList<>(findAnnotatedChanges(ChangeUnit.class));
        Map<String, List<AbstractPreviewTask>> mapByPackage = new HashMap<>();
        for(CodePreviewChangeUnit item: allChanges) {
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
    
    public Flamingock getPipelineAnnotation() {
        logger.info("Searching for @Flamingock annotation");
        return roundEnv.getElementsAnnotatedWith(Flamingock.class)
                .stream()
                .filter(e -> e.getKind() == ElementKind.CLASS)
                .map(e -> (TypeElement) e)
                .map(e -> e.getAnnotation(Flamingock.class))
                .findFirst()
                .orElse(null);
    }

    private Collection<CodePreviewChangeUnit> findAnnotatedChanges(Class<? extends Annotation> annotationType) {
        return roundEnv.getElementsAnnotatedWith(annotationType)
                .stream()
                .filter(e -> e.getKind() == ElementKind.CLASS)
                .map(e -> (TypeElement) e)
                .map(PreviewTaskBuilder::getCodeBuilder)
                .map(PreviewTaskBuilder::build)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }



}
