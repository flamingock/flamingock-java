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
package io.flamingock.internal.core.task.loaded;

import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.FlamingockConstructor;
import io.flamingock.api.annotations.Rollback;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.common.core.preview.ChangeOrderExtractor;
import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.Recovery;
import io.flamingock.internal.common.core.preview.AbstractPreviewTask;
import io.flamingock.internal.common.core.preview.CodePreviewChange;
import io.flamingock.internal.common.core.task.RecoveryDescriptor;
import io.flamingock.internal.common.core.task.TargetSystemDescriptor;
import io.flamingock.internal.util.ReflectionUtil;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

public class CodeLoadedTaskBuilder implements LoadedTaskBuilder<CodeLoadedChange> {

    private String id;
    private String orderInContent;
    private String author;
    private String changeClassName;
    private Constructor<?> constructor;
    private Method applyMethod;
    private Optional<Method> rollbackMethod;
    private boolean isRunAlways;
    private boolean isTransactional;
    private boolean isSystem;
    private TargetSystemDescriptor targetSystem;
    private RecoveryDescriptor recovery;
    private boolean legacy;

    private CodeLoadedTaskBuilder() {
    }

    static CodeLoadedTaskBuilder getInstance() {
        return new CodeLoadedTaskBuilder();
    }

    static CodeLoadedTaskBuilder getInstanceFromPreview(CodePreviewChange preview) {
        return getInstance().setPreview(preview);
    }

    static CodeLoadedTaskBuilder getInstanceFromClass(Class<?> sourceClass) {
        return getInstance().setSourceClass(sourceClass);
    }

    public static boolean supportsPreview(AbstractPreviewTask previewTask) {
        return CodePreviewChange.class.isAssignableFrom(previewTask.getClass());
    }

    public static boolean supportsSourceClass(Class<?> sourceClass) {
        return sourceClass.isAnnotationPresent(Change.class);
    }


    private CodeLoadedTaskBuilder setPreview(CodePreviewChange preview) {
        setId(preview.getId());
        setOrder(preview.getOrder().orElse(null));
        setAuthor(preview.getAuthor());
        setChangeClassName(preview.getSource());
        setConstructor(getConstructorFromPreview(preview));
        setApplyMethod(getApplyMethodFromPreview(preview));
        setRollbackMethod(getRollbackMethodFromPreview(preview));
        setRunAlways(preview.isRunAlways());
        setTransactional(preview.isTransactional());
        setSystem(preview.isSystem());
        setTargetSystem(preview.getTargetSystem());
        setRecovery(preview.getRecovery());
        setLegacy(preview.isLegacy());
        return this;
    }

    private CodeLoadedTaskBuilder setSourceClass(Class<?> sourceClass) {
        if (sourceClass.isAnnotationPresent(Change.class)) {
            setFromFlamingockChangeAnnotation(sourceClass, sourceClass.getAnnotation(Change.class));
            return this;

        } else {
            throw new IllegalArgumentException(String.format(
                    "Change class[%s] should be annotate with %s",
                    sourceClass.getName(),
                    Change.class.getName()
            ));
        }
    }

    public CodeLoadedTaskBuilder setId(String id) {
        this.id = id;
        return this;
    }

    @Override
    public CodeLoadedTaskBuilder setTargetSystem(TargetSystemDescriptor targetSystem) {
        this.targetSystem = targetSystem;
        return this;
    }

    @Override
    public CodeLoadedTaskBuilder setRecovery(RecoveryDescriptor recovery) {
        this.recovery = recovery;
        return this;
    }

    public CodeLoadedTaskBuilder setOrder(String orderInContent) {
        this.orderInContent = orderInContent;
        return this;
    }

    public CodeLoadedTaskBuilder setAuthor(String author) {
        this.author = author;
        return this;
    }

    public CodeLoadedTaskBuilder setChangeClassName(String changeClassName) {
        this.changeClassName = changeClassName;
        return this;
    }

    public CodeLoadedTaskBuilder setConstructor(Constructor<?> constructor) {
        this.constructor = constructor;
        return this;
    }

    public CodeLoadedTaskBuilder setRunAlways(boolean runAlways) {
        this.isRunAlways = runAlways;
        return this;
    }

    public CodeLoadedTaskBuilder setTransactional(boolean transactional) {
        this.isTransactional = transactional;
        return this;
    }

    public CodeLoadedTaskBuilder setSystem(boolean system) {
        this.isSystem = system;
        return this;
    }

    public CodeLoadedTaskBuilder setApplyMethod(Method applyMethod) {
        this.applyMethod = applyMethod;
        return this;
    }

    public CodeLoadedTaskBuilder setRollbackMethod(Optional<Method> rollbackMethod) {
        this.rollbackMethod = rollbackMethod;
        return this;
    }

    public CodeLoadedTaskBuilder setLegacy(boolean legacy) {
        this.legacy = legacy;
        return this;
    }

    @Override
    public CodeLoadedChange build() {

        Class<?> changeClass = getClassForName(changeClassName);
        String order = ChangeOrderUtil.getMatchedOrderFromClassName(id, orderInContent, changeClassName);

        return new CodeLoadedChange(
                id,
                order,
                author,
                changeClass,
                constructor,
                applyMethod,
                rollbackMethod,
                isRunAlways,
                isTransactional,
                isSystem,
                targetSystem,
                recovery,
                legacy
        );
    }

    private void setFromFlamingockChangeAnnotation(Class<?> sourceClass, Change annotation) {
        String changeId = annotation.id();
        setId(changeId);
        setOrder(ChangeOrderExtractor.extractOrderFromClassName(changeId, sourceClass.getName()));
        setAuthor(annotation.author());
        setChangeClassName(sourceClass.getName());
        setConstructor(getConstructor(sourceClass));
        setApplyMethod(getApplyMethodFromAnnotation(sourceClass));
        setRollbackMethod(getRollbackMethodFromAnnotation(sourceClass));
        setTransactional(annotation.transactional());
        setSystem(false);
        setRecoveryFromClass(sourceClass);
        setLegacy(false);
    }

    private void setRecoveryFromClass(Class<?> sourceClass) {
        if (sourceClass.isAnnotationPresent(Recovery.class)) {
            Recovery recoveryAnnotation = sourceClass.getAnnotation(Recovery.class);
            setRecovery(RecoveryDescriptor.fromStrategy(recoveryAnnotation.strategy()));
        } else {
            setRecovery(RecoveryDescriptor.getDefault());
        }
    }

    private Class<?> getClassForName(String clazzName) {
        try {
            return Class.forName(clazzName);
        }
        catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private Constructor<?> getConstructorFromPreview(CodePreviewChange preview) {
        Class<?> sourceClass = getClassForName(preview.getSource());
        return ReflectionUtil
                .getConstructorFromParameterTypeNames(sourceClass, preview.getPreviewConstructor().getParameterTypes());
    }

    private Method getApplyMethodFromPreview(CodePreviewChange preview) {
        try {
            return getMethodFromNameAndParameters(preview.getSource(), preview.getApplyPreviewMethod().getName(), preview.getApplyPreviewMethod().getParameterTypes());
        } catch (NullPointerException ex) {
            throw ex;
        }
    }

    private Optional<Method> getRollbackMethodFromPreview(CodePreviewChange preview) {
        if (preview.getRollbackPreviewMethod() == null) {
            return Optional.empty();
        }
        else {
            return Optional.ofNullable(getMethodFromNameAndParameters(preview.getSource(), preview.getRollbackPreviewMethod().getName(), preview.getRollbackPreviewMethod().getParameterTypes()));
        }
    }

    private Method getMethodFromNameAndParameters(String sourceClassName, String methodName, List<String> methodParametersTypesNames) {
        Class<?> sourceClass = getClassForName(sourceClassName);
        return ReflectionUtil.getDeclaredMethodFromParameterTypeNames(sourceClass, methodName, methodParametersTypesNames);
    }

    private Method getApplyMethodFromAnnotation(Class<?> sourceClass) {
        Optional<Method> firstAnnotatedMethod = ReflectionUtil.findFirstAnnotatedMethod(sourceClass, Apply.class);
        return firstAnnotatedMethod
                .orElseThrow(() -> new IllegalArgumentException(String.format(
                        "Executable change[%s] without %s method",
                        sourceClass.getName(),
                        Apply.class.getName())));
    }

    private Optional<Method> getRollbackMethodFromAnnotation(Class<?> sourceClass) {
        return ReflectionUtil.findFirstAnnotatedMethod(sourceClass, Rollback.class);
    }

    private Constructor<?> getConstructor(Class<?> sourceClass) {
        try {
            return ReflectionUtil.getConstructorWithAnnotationPreference(sourceClass, FlamingockConstructor.class);
        } catch (ReflectionUtil.MultipleAnnotatedConstructorsFound ex) {
            throw new FlamingockException("Found multiple constructors for class[%s] annotated with %s." +
                    " Annotate the one you want Flamingock to use to instantiate your change",
                    sourceClass.getName(),
                    FlamingockConstructor.class.getName());
        } catch (ReflectionUtil.MultipleConstructorsFound ex) {
            throw new FlamingockException("Found multiple constructors, please provide at least one for class[%s].\n" +
                    "When more than one constructor, exactly one of them must be annotated with %s, and it will be taken as default "
                    , sourceClass.getName()
                    , FlamingockConstructor.class.getSimpleName()
            );
        } catch (ReflectionUtil.ConstructorNotFound ex) {
            throw new FlamingockException("Cannot find a valid constructor for class[%s]", sourceClass.getName());
        }
    }

}
