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
