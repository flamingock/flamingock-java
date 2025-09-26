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
import io.flamingock.api.annotations.Recovery;
import io.flamingock.api.annotations.Rollback;
import io.flamingock.api.annotations.TargetSystem;
import io.flamingock.internal.common.core.preview.CodePreviewChange;
import io.flamingock.internal.common.core.preview.PreviewMethod;
import io.flamingock.internal.common.core.task.RecoveryDescriptor;
import io.flamingock.internal.common.core.task.TargetSystemDescriptor;
import io.mongock.api.annotations.BeforeExecution;
import io.mongock.api.annotations.RollbackBeforeExecution;
import org.jetbrains.annotations.NotNull;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


//TODO how to set transactional and runAlways
public class CodePreviewTaskBuilder implements PreviewTaskBuilder<CodePreviewChange> {

    private String id;
    private String order;
    private String author;
    private String sourceClassPath;
    private PreviewMethod executionMethod;
    private PreviewMethod rollbackMethod;
    private PreviewMethod beforeExecutionMethod;
    private PreviewMethod rollbackBeforeExecutionMethod;
    private boolean runAlways = false;
    private boolean transactional;
    private boolean system;
    private TargetSystemDescriptor targetSystem;
    private RecoveryDescriptor recovery;

    private CodePreviewTaskBuilder() {
    }

    static CodePreviewTaskBuilder builder() {
        return new CodePreviewTaskBuilder();
    }

    static CodePreviewTaskBuilder builder(TypeElement typeElement) {
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

    public CodePreviewTaskBuilder setExecutionMethod(PreviewMethod executionMethod) {
        this.executionMethod = executionMethod;
        return this;
    }

    public CodePreviewTaskBuilder setRollbackMethod(PreviewMethod rollbackMethod) {
        this.rollbackMethod = rollbackMethod;
        return this;
    }

    public void setBeforeExecutionMethod(PreviewMethod beforeExecutionMethod) {
        this.beforeExecutionMethod = beforeExecutionMethod;
    }

    public void setRollbackBeforeExecutionMethod(PreviewMethod rollbackBeforeExecutionMethod) {
        this.rollbackBeforeExecutionMethod = rollbackBeforeExecutionMethod;
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
            setId(changeAnnotation.id());
            setOrder(null);//TODO replace with order from class
            setAuthor(changeAnnotation.author());
            setSourceClassPath(typeElement.getQualifiedName().toString());
            setExecutionMethod(getAnnotatedMethodInfo(typeElement, Apply.class).orElse(null));
            setRollbackMethod(getAnnotatedMethodInfo(typeElement, Rollback.class).orElse(null));
            setBeforeExecutionMethod(getAnnotatedMethodInfo(typeElement, BeforeExecution.class).orElse(null));
            setRollbackBeforeExecutionMethod(getAnnotatedMethodInfo(typeElement, RollbackBeforeExecution.class).orElse(null));
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
                executionMethod,
                rollbackMethod,
                beforeExecutionMethod,
                rollbackBeforeExecutionMethod,
                runAlways,
                transactional,
                system,
                targetSystem,
                recovery);
    }

    private Optional<PreviewMethod> getAnnotatedMethodInfo(TypeElement typeElement,
                                                           Class<? extends Annotation> annotationType) {
        for (Element enclosedElement : typeElement.getEnclosedElements()) {
            if (enclosedElement.getKind() == ElementKind.METHOD &&
                    enclosedElement.getAnnotation(annotationType) != null) {

                ExecutableElement method = (ExecutableElement) enclosedElement;
                String methodName = method.getSimpleName().toString();

                List<String> parameterTypes = new ArrayList<>();
                for (VariableElement param : method.getParameters()) {
                    TypeMirror paramType = param.asType();
                    parameterTypes.add(paramType.toString()); // fully qualified name (e.g., java.lang.String)
                }

                return Optional.of(new PreviewMethod(methodName, parameterTypes));
            }
        }

        return Optional.empty();
    }

}
