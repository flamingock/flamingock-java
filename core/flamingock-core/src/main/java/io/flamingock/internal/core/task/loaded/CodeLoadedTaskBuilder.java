/*
 * Copyright 2023 Flamingock (https://oss.flamingock.io)
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

import io.flamingock.commons.utils.StringUtil;
import io.flamingock.core.api.annotations.ChangeUnit;
import io.flamingock.core.preview.AbstractPreviewTask;
import io.flamingock.core.preview.CodePreviewChangeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CodeLoadedTaskBuilder implements LoadedTaskBuilder<CodeLoadedChangeUnit> {

    private static final Logger logger = LoggerFactory.getLogger(CodeLoadedTaskBuilder.class);

    private String id;
    private String order;
    private String source;
    private boolean isRunAlways;
    private boolean isTransactional;
    private boolean isSystem;
    private boolean isBeforeExecution;//only for old change units

    private CodeLoadedTaskBuilder() {
    }

    static CodeLoadedTaskBuilder getInstance() {
        return new CodeLoadedTaskBuilder();
    }

    static CodeLoadedTaskBuilder getInstanceFromPreview(CodePreviewChangeUnit preview) {
        return getInstance().setPreview(preview);
    }

    static CodeLoadedTaskBuilder getInstanceFromClass(Class<?> sourceClass) {
        return getInstance().setSourceClass(sourceClass);
    }

    public static boolean supportsPreview(AbstractPreviewTask previewTask) {
        return CodePreviewChangeUnit.class.isAssignableFrom(previewTask.getClass());
    }

    public static boolean supportsSourceClass(Class<?> sourceClass) {
        return sourceClass.isAnnotationPresent(ChangeUnit.class);

    }


    private CodeLoadedTaskBuilder setPreview(CodePreviewChangeUnit preview) {
        setId(preview.getId());
        setOrder(preview.getOrder().orElse(null));
        setTemplateName(preview.getSource());
        setRunAlways(preview.isRunAlways());
        setTransactional(preview.isTransactional());
        setSystem(preview.isSystem());
        return this;
    }

    private CodeLoadedTaskBuilder setSourceClass(Class<?> sourceClass) {
        if (sourceClass.isAnnotationPresent(ChangeUnit.class)) {
            setFromFlamingockChangeAnnotation(sourceClass, sourceClass.getAnnotation(ChangeUnit.class));
            return this;

        } else {
            throw new IllegalArgumentException(String.format(
                    "Change unit class[%s] should be annotate with %s",
                    sourceClass.getName(),
                    ChangeUnit.class.getName()
            ));
        }
    }

    public CodeLoadedTaskBuilder setId(String id) {
        this.id = id;
        return this;
    }

    public CodeLoadedTaskBuilder setOrder(String order) {
        this.order = order;
        return this;
    }

    public CodeLoadedTaskBuilder setTemplateName(String templateName) {
        this.source = templateName;
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
    public CodeLoadedChangeUnit build() {

        try {
            return new CodeLoadedChangeUnit(
                    isBeforeExecution ? StringUtil.getBeforeExecutionId(id) : id,
                    order,
                    Class.forName(source),
                    isRunAlways,
                    isTransactional,
                    isSystem
            );
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private void setFromFlamingockChangeAnnotation(Class<?> sourceClass, ChangeUnit annotation) {
        setId(annotation.id());
        setOrder(annotation.order());
        setTemplateName(sourceClass.getName());
        setRunAlways(annotation.runAlways());
        setTransactional(annotation.transactional());
        setSystem(false);
    }

}
