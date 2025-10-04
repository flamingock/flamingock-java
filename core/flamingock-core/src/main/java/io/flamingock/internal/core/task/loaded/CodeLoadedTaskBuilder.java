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

import io.flamingock.internal.common.core.preview.ChangeOrderExtractor;
import io.flamingock.internal.util.StringUtil;
import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.Recovery;
import io.flamingock.internal.common.core.preview.AbstractPreviewTask;
import io.flamingock.internal.common.core.preview.CodePreviewChange;
import io.flamingock.internal.common.core.task.RecoveryDescriptor;
import io.flamingock.internal.common.core.task.TargetSystemDescriptor;

public class CodeLoadedTaskBuilder implements LoadedTaskBuilder<CodeLoadedChange> {

    private String id;
    private String orderInContent;
    private String author;
    private String changeClass;
    private boolean isRunAlways;
    private boolean isTransactional;
    private boolean isSystem;
    private TargetSystemDescriptor targetSystem;
    private RecoveryDescriptor recovery;
    private boolean isBeforeExecution;//only for legacy ChangeUnits

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
        setChangeClass(preview.getSource());
        setRunAlways(preview.isRunAlways());
        setTransactional(preview.isTransactional());
        setSystem(preview.isSystem());
        setTargetSystem(preview.getTargetSystem());
        setRecovery(preview.getRecovery());
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

    public CodeLoadedTaskBuilder setChangeClass(String changeClass) {
        this.changeClass = changeClass;
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

    public CodeLoadedTaskBuilder setBeforeExecution(boolean beforeExecution) {
        isBeforeExecution = beforeExecution;
        return this;
    }

    @Override
    public CodeLoadedChange build() {

        try {

            String order = ChangeOrderUtil.getMatchedOrderFromClassName(id, orderInContent, changeClass);
            return new CodeLoadedChange(
                    isBeforeExecution ? StringUtil.getBeforeExecutionId(id) : id,
                    order,
                    author,
                    Class.forName(changeClass),
                    isRunAlways,
                    isTransactional,
                    isSystem,
                    targetSystem,
                    recovery
            );
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private void setFromFlamingockChangeAnnotation(Class<?> sourceClass, Change annotation) {
        String changeId = annotation.id();
        setId(changeId);
        setOrder(ChangeOrderExtractor.extractOrderFromClassName(changeId, sourceClass.getName()));
        setAuthor(annotation.author());
        setChangeClass(sourceClass.getName());
        setTransactional(annotation.transactional());
        setSystem(false);
        setRecoveryFromClass(sourceClass);
    }

    private void setRecoveryFromClass(Class<?> sourceClass) {
        if (sourceClass.isAnnotationPresent(Recovery.class)) {
            Recovery recoveryAnnotation = sourceClass.getAnnotation(Recovery.class);
            setRecovery(RecoveryDescriptor.fromStrategy(recoveryAnnotation.strategy()));
        } else {
            setRecovery(RecoveryDescriptor.getDefault());
        }
    }

}
