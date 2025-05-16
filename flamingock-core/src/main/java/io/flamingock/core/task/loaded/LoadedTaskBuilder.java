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

package io.flamingock.core.task.loaded;

import io.flamingock.core.preview.AbstractPreviewTask;
import io.flamingock.core.preview.CodePreviewChangeUnit;
import io.flamingock.core.preview.TemplatePreviewChangeUnit;

public interface LoadedTaskBuilder<LOADED_TASK extends AbstractLoadedTask> {

    static AbstractLoadedTask build(AbstractPreviewTask previewTask) {
        return getInstance(previewTask).build();
    }

    static LoadedTaskBuilder<?> getInstance(AbstractPreviewTask previewTask) {
        if (TemplateLoadedTaskBuilder.supportsPreview(previewTask)) {
            return  TemplateLoadedTaskBuilder.getInstanceFromPreview((TemplatePreviewChangeUnit) previewTask);

        } else if (CodeLoadedTaskBuilder.supportsPreview(previewTask)) {
            return CodeLoadedTaskBuilder.getInstanceFromPreview((CodePreviewChangeUnit) previewTask);

        }
        throw new RuntimeException("Not implemented build from preview to loaded");
    }

    static CodeLoadedTaskBuilder getCodeBuilderInstance(Class<?> sourceClass) {
        if (CodeLoadedTaskBuilder.supportsSourceClass(sourceClass)) {
            return CodeLoadedTaskBuilder.getInstanceFromClass(sourceClass);

        }
        throw new RuntimeException("Not implemented build from preview to loaded");
    }


    LoadedTaskBuilder<LOADED_TASK> setId(String id);

    LoadedTaskBuilder<LOADED_TASK> setOrder(String order);

    LoadedTaskBuilder<LOADED_TASK> setTemplateName(String templateName);

    LoadedTaskBuilder<LOADED_TASK> setRunAlways(boolean runAlways);

    LoadedTaskBuilder<LOADED_TASK> setTransactional(boolean transactional);

    LoadedTaskBuilder<LOADED_TASK> setSystem(boolean system);

    LOADED_TASK build();

}
