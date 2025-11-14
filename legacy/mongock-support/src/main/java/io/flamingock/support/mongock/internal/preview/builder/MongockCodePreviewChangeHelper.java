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
package io.flamingock.support.mongock.internal.preview.builder;

import com.github.cloudyrock.mongock.ChangeLog;
import com.github.cloudyrock.mongock.ChangeSet;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.common.core.preview.CodePreviewChange;
import io.flamingock.internal.common.core.preview.PreviewConstructor;
import io.flamingock.internal.common.core.preview.PreviewMethod;
import io.flamingock.internal.common.core.task.RecoveryDescriptor;
import io.flamingock.internal.common.core.task.TargetSystemDescriptor;
import io.flamingock.internal.util.ReflectionUtil;
import io.mongock.api.annotations.BeforeExecution;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.ChangeUnitConstructor;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackBeforeExecution;
import io.mongock.api.annotations.RollbackExecution;
import org.jetbrains.annotations.NotNull;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


@SuppressWarnings("deprecation")
public class MongockCodePreviewChangeHelper {

    @NotNull
    public List<CodePreviewChange> getCodePreviewChanges(TypeElement typeElement, String mongockTargetSystemId) {

        ChangeUnit changeUnitAnnotation = typeElement.getAnnotation(ChangeUnit.class);
        if (changeUnitAnnotation != null) {
            return getCodePreviewChangesFromChangeUnit(typeElement, mongockTargetSystemId, changeUnitAnnotation);
        }
        else {
            ChangeLog changeLogAnnotation = typeElement.getAnnotation(ChangeLog.class);
            if (changeLogAnnotation != null) {
                return getCodePreviewChangesFromChangeLog(typeElement, mongockTargetSystemId, changeLogAnnotation);
            }
            else {
                // This should not happen
                throw new FlamingockException("Mongock change class must be annotated with @ChangeUnit or @ChangeLog annotations.");
            }
        }
    }


    @NotNull
    private List<CodePreviewChange> getCodePreviewChangesFromChangeUnit(TypeElement typeElement, String mongockTargetSystemId, ChangeUnit changeUnitAnnotation) {

        // In Flamingock, when @BeforeExecution is present, it will be processed as an independent change.

        // ChangeUnit annotation validations
        validateChangeUnitAnnotation(changeUnitAnnotation);

        List<CodePreviewChange> changes = new ArrayList<>();

        String id = changeUnitAnnotation.id();
        String sourceClassPath = typeElement.getQualifiedName().toString();
        String order = getChangeUnitOrder(changeUnitAnnotation.order(), sourceClassPath);
        String author = changeUnitAnnotation.author();
        PreviewConstructor constructor = getPreviewConstructor(typeElement);
        PreviewMethod executionMethod = getAnnotatedMethodInfo(typeElement, Execution.class).orElse(null);
        PreviewMethod rollbackMethod = getAnnotatedMethodInfo(typeElement, RollbackExecution.class).orElse(null);
        PreviewMethod beforeExecutionMethod = getAnnotatedMethodInfo(typeElement, BeforeExecution.class).orElse(null);
        PreviewMethod rollbackBeforeExecutionMethod = getAnnotatedMethodInfo(typeElement, RollbackBeforeExecution.class).orElse(null);
        boolean runAlways = changeUnitAnnotation.runAlways();
        boolean transactional = changeUnitAnnotation.transactional();
        boolean system = false;
        TargetSystemDescriptor targetSystem = TargetSystemDescriptor.fromId(mongockTargetSystemId);
        RecoveryDescriptor recovery = RecoveryDescriptor.getDefault();

        // BeforeExecution change (Optional)
        if (beforeExecutionMethod != null) {
            changes.add(new CodePreviewChange(
                    getBeforeExecutionId(id),
                    getBeforeExecutionChangeOrder(order),
                    author,
                    sourceClassPath,
                    constructor,
                    beforeExecutionMethod,
                    rollbackBeforeExecutionMethod,
                    runAlways,
                    false,
                    system,
                    targetSystem,
                    recovery,
                    true));
        }

        // Default change
        changes.add(new CodePreviewChange(
                id,
                getExecutionChangeOrder(order, beforeExecutionMethod != null),
                author,
                sourceClassPath,
                constructor,
                executionMethod,
                rollbackMethod,
                runAlways,
                transactional,
                system,
                targetSystem,
                recovery,
                true));

        return changes;
    }

    private FlamingockException buildNotSupportedException(String message) {
        return new FlamingockException(message + " If it’s important for your use case, let us know at https://github.com/flamingock/flamingock-java/issues or email us at support@flamingock.io");
    }

    private void validateChangeUnitAnnotation(ChangeUnit changeUnitAnnotation) {

        // failFast not supported (expected the default value "true")
        if (!changeUnitAnnotation.failFast()) {
            throw buildNotSupportedException("ChangeUnit.failFast=false expected behavior is not supported by Flamingock.");
        }

        // runAlways not supported (expected the default value "false")
        if (changeUnitAnnotation.runAlways()) {
            throw buildNotSupportedException("ChangeUnit.runAlways=true expected behavior is not supported by Flamingock.");
        }

        // systemVersion not supported (expected the default value "0")
        if (changeUnitAnnotation.systemVersion() == null || !changeUnitAnnotation.systemVersion().equals("0")) {
            throw buildNotSupportedException("ChangeUnit.systemVersion is not supported by Flamingock.");
        }
    }

    private Optional<PreviewMethod> getAnnotatedMethodInfo(TypeElement typeElement,
                                                           Class<? extends Annotation> annotationType) {
        for (Element enclosedElement : typeElement.getEnclosedElements()) {
            if (enclosedElement.getKind() == ElementKind.METHOD &&
                    enclosedElement.getAnnotation(annotationType) != null) {

                ExecutableElement method = (ExecutableElement) enclosedElement;
                return Optional.of(previewMethodFromExecutableElement(method));
            }
        }

        return Optional.empty();
    }

    private PreviewMethod previewMethodFromExecutableElement(ExecutableElement element) {
        String methodName = element.getSimpleName().toString();

        List<String> parameterTypes = new ArrayList<>();
        for (VariableElement param : element.getParameters()) {
            TypeMirror paramType = param.asType();
            parameterTypes.add(paramType.toString()); // fully qualified name (e.g., java.lang.String)
        }
        return new PreviewMethod(methodName, parameterTypes);
    }

    private String getChangeUnitOrder(String annotationOrder, String classCanonicalName) {
        return annotationOrder != null && !annotationOrder.trim().isEmpty() ? annotationOrder : classCanonicalName;
    }

    private String getBeforeExecutionId(String baseId) {
        return String.format("%s_%s", baseId, "before");
    }

    private String getBeforeExecutionChangeOrder(String baseOrder) {
        return getComposedOrder(baseOrder, 0);
    }

    private String getExecutionChangeOrder(String baseOrder, boolean withBeforeExecution) {
        return withBeforeExecution ? getComposedOrder(baseOrder, 1) : baseOrder;
    }

    private String getComposedOrder(String baseOrder, int index) {
        return String.format("%s#%s", baseOrder, index);
    }


    @NotNull
    private List<CodePreviewChange> getCodePreviewChangesFromChangeLog(TypeElement typeElement, String mongockTargetSystemId, ChangeLog changeLogAnnotation) {

        // In Flamingock, all @ChangeSet will be processed as an independent change, so transaction will be individual.

        // ChangeLog annotation validations
        validateChangeLogAnnotation(changeLogAnnotation);

        List<CodePreviewChange> changes = new ArrayList<>();

        String sourceClassPath = typeElement.getQualifiedName().toString();
        boolean transactional = true;
        boolean system = false;
        TargetSystemDescriptor targetSystem = TargetSystemDescriptor.fromId(mongockTargetSystemId);
        RecoveryDescriptor recovery = RecoveryDescriptor.getDefault();

        for (ExecutableElement changeSetMethod : getChangeSetMethods(typeElement)) {

            ChangeSet changeSetAnnotation = changeSetMethod.getAnnotation(ChangeSet.class);

            if (changeSetAnnotation != null) {

                // ChangeSet annotation validations
                validateChangeSetAnnotation(changeSetAnnotation);

                changes.add(new CodePreviewChange(
                        changeSetAnnotation.id(),
                        getChangeSetOrder(sourceClassPath, changeLogAnnotation.order(), changeSetAnnotation.order()),
                        changeSetAnnotation.author(),
                        sourceClassPath,
                        getPreviewConstructor(typeElement),
                        previewMethodFromExecutableElement(changeSetMethod),
                        null,
                        changeSetAnnotation.runAlways(),
                        transactional,
                        system,
                        targetSystem,
                        recovery,
                        true));
            }
        }

        return changes;
    }

    private void validateChangeLogAnnotation(ChangeLog changeLogAnnotation) {
        if (!changeLogAnnotation.failFast()) {
            throw buildNotSupportedException("ChangeLog.failFast=false expected behavior is not supported by Flamingock.");
        }
    }

    private void validateChangeSetAnnotation(ChangeSet changeSetAnnotation) {

        // failFast not supported (expected the default value "true")
        if (!changeSetAnnotation.failFast()) {
            throw buildNotSupportedException("ChangeSet.failFast=false expected behavior is not supported by Flamingock.");
        }

        // runAlways not supported (expected the default value "false")
        if (changeSetAnnotation.runAlways()) {
            throw buildNotSupportedException("ChangeSet.runAlways=true expected behavior is not supported by Flamingock.");
        }

        // systemVersion not supported (expected the default value "0")
        if (changeSetAnnotation.systemVersion() == null || !changeSetAnnotation.systemVersion().equals("0")) {
            throw buildNotSupportedException("ChangeSet.systemVersion is not supported by Flamingock.");
        }
    }

    @NotNull
    private List<ExecutableElement> getChangeSetMethods(TypeElement typeElement) {
        return typeElement.getEnclosedElements()
                .stream()
                .filter(e -> e.getKind() == ElementKind.METHOD)
                .filter(e -> e.getAnnotation(ChangeSet.class) != null)
                .map(e -> (ExecutableElement)e)
                .collect(Collectors.toList());
    }

    private String getChangeSetOrder(String sourceClass, String changeLogOrder, String changeSetOrder) {
        if (changeLogOrder == null || changeLogOrder.trim().isEmpty()) {
            changeLogOrder = sourceClass;
        }
        return String.format("%s#%s", changeLogOrder, changeSetOrder);
    }

    private PreviewConstructor getPreviewConstructor(TypeElement typeElement) {
        ExecutableElement constructorElement = getConstructorElement(typeElement);
        List<String> parameterTypes = ReflectionUtil.getParametersTypesQualifiedNames(constructorElement);
        return new PreviewConstructor(parameterTypes);
    }

    private ExecutableElement getConstructorElement(TypeElement typeElement) {
        try {
            return ReflectionUtil.getConstructorWithAnnotationPreference(typeElement, ChangeUnitConstructor.class);
        } catch (ReflectionUtil.MultipleAnnotatedConstructorsFound ex) {
            throw new FlamingockException("Found multiple constructors for class[%s] annotated with %s." +
                    " Annotate the one you want Flamingock to use to instantiate your change",
                    typeElement.getQualifiedName(),
                    ChangeUnitConstructor.class.getName());
        } catch (ReflectionUtil.MultipleConstructorsFound ex) {
            throw new FlamingockException("Found multiple constructors for class[%s].\n" +
                    "When more than one constructor, exactly one of them must be annotated with %s, and it will be taken as default ",
                    typeElement.getQualifiedName(),
                    ChangeUnitConstructor.class.getSimpleName()
            );
        } catch (ReflectionUtil.ConstructorNotFound ex) {
            throw new FlamingockException("Cannot find a valid constructor for class[%s]", typeElement.getQualifiedName());
        }
    }
}
