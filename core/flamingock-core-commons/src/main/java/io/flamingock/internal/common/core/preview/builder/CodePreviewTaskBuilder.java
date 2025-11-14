/*
 * Copyright 2023 Flamingock (https://www.flamingock.io)
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
package io.flamingock.internal.common.core.preview.builder;

import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.FlamingockConstructor;
import io.flamingock.api.annotations.Recovery;
import io.flamingock.api.annotations.Rollback;
import io.flamingock.api.annotations.TargetSystem;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.common.core.preview.ChangeOrderExtractor;
import io.flamingock.internal.common.core.preview.CodePreviewChange;
import io.flamingock.internal.common.core.preview.PreviewConstructor;
import io.flamingock.internal.common.core.preview.PreviewMethod;
import io.flamingock.internal.common.core.task.RecoveryDescriptor;
import io.flamingock.internal.common.core.task.TargetSystemDescriptor;
import io.flamingock.internal.util.ReflectionUtil;
import org.jetbrains.annotations.NotNull;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class CodePreviewTaskBuilder implements PreviewTaskBuilder<CodePreviewChange> {

    private String id;
    private String order;
    private String author;
    private String sourceClassPath;
    private PreviewConstructor constructor;
    private PreviewMethod applyMethod;
    private PreviewMethod rollbackMethod;
    private boolean runAlways = false;
    private boolean transactional;
    private boolean system;
    private TargetSystemDescriptor targetSystem;
    private RecoveryDescriptor recovery;

    private CodePreviewTaskBuilder() {
    }

    public static CodePreviewTaskBuilder builder() {
        return new CodePreviewTaskBuilder();
    }

    public static CodePreviewTaskBuilder builder(TypeElement typeElement) {
        return  builder().setTypeElement(typeElement);
    }

    public CodePreviewTaskBuilder setId(String id) {
        this.id = id;
        return this;
    }

    public CodePreviewTaskBuilder setTargetSystem(TargetSystemDescriptor targetSystem) {
        this.targetSystem = targetSystem;
        return this;
    }

    public CodePreviewTaskBuilder setRecovery(RecoveryDescriptor recovery) {
        this.recovery = recovery;
        return this;
    }

    public CodePreviewTaskBuilder setOrder(String order) {
        this.order = order;
        return this;
    }

    public CodePreviewTaskBuilder setAuthor(String author) {
        this.author = author;
        return this;
    }

    public CodePreviewTaskBuilder setSourceClassPath(String sourceClassPath) {
        this.sourceClassPath = sourceClassPath;
        return this;
    }

    public CodePreviewTaskBuilder setConstructor(PreviewConstructor constructor) {
        this.constructor = constructor;
        return this;
    }

    public CodePreviewTaskBuilder setApplyMethod(PreviewMethod executionMethod) {
        this.applyMethod = executionMethod;
        return this;
    }

    public CodePreviewTaskBuilder setRollbackMethod(PreviewMethod rollbackMethod) {
        this.rollbackMethod = rollbackMethod;
        return this;
    }

    public CodePreviewTaskBuilder setRunAlways(boolean runAlways) {
        this.runAlways = runAlways;
        return this;
    }

    public CodePreviewTaskBuilder setTransactional(boolean transactional) {
        this.transactional = transactional;
        return this;
    }

    public CodePreviewTaskBuilder setSystem(boolean system) {
        this.system = system;
        return this;
    }

    CodePreviewTaskBuilder setTypeElement(TypeElement typeElement) {
        Change changeAnnotation = typeElement.getAnnotation(Change.class);
        TargetSystem targetSystemAnnotation = typeElement.getAnnotation(TargetSystem.class);
        Recovery recoveryAnnotation = typeElement.getAnnotation(Recovery.class);

        if(changeAnnotation != null) {
            String changeId = changeAnnotation.id();
            String classPath = typeElement.getQualifiedName().toString();
            String order = ChangeOrderExtractor.extractOrderFromClassName(changeId, classPath);
            setId(changeId);
            setOrder(order);
            setAuthor(changeAnnotation.author());
            setSourceClassPath(classPath);
            setConstructor(getPreviewConstructor(typeElement));
            setApplyMethod(getAnnotatedMethodInfo(typeElement, Apply.class).orElse(null));
            setRollbackMethod(getAnnotatedMethodInfo(typeElement, Rollback.class).orElse(null));
            setRunAlways(false); //TODO: how to set runAlways
            setTransactional(changeAnnotation.transactional());
            setSystem(false);
        }
        if(targetSystemAnnotation != null) {
            setTargetSystem(TargetSystemDescriptor.fromId(targetSystemAnnotation.id()));
        }
        if(recoveryAnnotation != null) {
            setRecovery(RecoveryDescriptor.fromStrategy(recoveryAnnotation.strategy()));
        } else {
            setRecovery(RecoveryDescriptor.getDefault());
        }
        return this;
    }



    @Override
    public CodePreviewChange build() {
        return getCodePreviewChange();
    }

    @NotNull
    private CodePreviewChange getCodePreviewChange() {
        return new CodePreviewChange(
                id,
                order,
                author,
                sourceClassPath,
                constructor,
                applyMethod,
                rollbackMethod,
                runAlways,
                transactional,
                system,
                targetSystem,
                recovery,
                false);
    }

    private Optional<PreviewMethod> getAnnotatedMethodInfo(TypeElement typeElement,
                                                           Class<? extends Annotation> annotationType) {
        for (Element enclosedElement : typeElement.getEnclosedElements()) {
            if (enclosedElement.getKind() == ElementKind.METHOD &&
                    enclosedElement.getAnnotation(annotationType) != null) {

                ExecutableElement method = (ExecutableElement) enclosedElement;
                String methodName = method.getSimpleName().toString();

                List<String> parameterTypes = ReflectionUtil.getParametersTypesQualifiedNames(method);

                return Optional.of(new PreviewMethod(methodName, parameterTypes));
            }
        }

        return Optional.empty();
    }

    private PreviewConstructor getPreviewConstructor(TypeElement typeElement) {
        ExecutableElement constructorElement = getConstructorElement(typeElement);
        List<String> parameterTypes = ReflectionUtil.getParametersTypesQualifiedNames(constructorElement);
        return new PreviewConstructor(parameterTypes);
    }

    private ExecutableElement getConstructorElement(TypeElement typeElement) {
        try {
            return ReflectionUtil.getConstructorWithAnnotationPreference(typeElement, FlamingockConstructor.class);
        } catch (ReflectionUtil.MultipleAnnotatedConstructorsFound ex) {
            throw new FlamingockException("Found multiple constructors for class[%s] annotated with %s." +
                    " Annotate the one you want Flamingock to use to instantiate your change",
                    typeElement.getQualifiedName(),
                    FlamingockConstructor.class.getName());
        } catch (ReflectionUtil.MultipleConstructorsFound ex) {
            throw new FlamingockException("Found multiple constructors for class[%s].\n" +
                    "When more than one constructor, exactly one of them must be annotated with %s, and it will be taken as default ",
                    typeElement.getQualifiedName(),
                    FlamingockConstructor.class.getSimpleName()
            );
        } catch (ReflectionUtil.ConstructorNotFound ex) {
            throw new FlamingockException("Cannot find a valid constructor for class[%s]", typeElement.getQualifiedName());
        }
    }
}
