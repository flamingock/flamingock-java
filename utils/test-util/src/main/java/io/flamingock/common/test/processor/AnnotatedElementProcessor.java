/*
 * Copyright 2026 Flamingock (https://www.flamingock.io)
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
package io.flamingock.common.test.processor;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A simple annotation processor that collects elements annotated with a specific annotation and passes them to a provided consumer.
 */
public class AnnotatedElementProcessor extends AbstractProcessor {
    private final Class<? extends Annotation> annotationClass;
    private final Consumer<TypeElement> elementConsumer;

    /**
     * Creates a new AnnotatedElementProcessor for the specified annotation class and element consumer.
     * @param annotationClass the annotation class to look for
     * @param elementConsumer the consumer to process the annotated elements
     */
    public AnnotatedElementProcessor(Class<? extends Annotation> annotationClass, Consumer<TypeElement> elementConsumer) {
        this.annotationClass = annotationClass;
        this.elementConsumer = elementConsumer;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Collections.singleton(annotationClass.getCanonicalName());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
        for (Element element : roundEnvironment.getElementsAnnotatedWith(annotationClass)) {
            elementConsumer.accept((TypeElement) element);
        }
        return false;
    }
}
