package io.flamingock.internal.common.core.preview.builder;

import io.flamingock.internal.common.core.preview.AbstractPreviewTask;
import io.flamingock.internal.common.core.preview.CodePreviewChangeUnit;
import io.flamingock.internal.common.core.preview.TemplatePreviewChangeUnit;
import io.flamingock.internal.common.core.template.ChangeTemplateFileContent;

import javax.lang.model.element.TypeElement;

public interface PreviewTaskBuilder<A extends AbstractPreviewTask> {

    static PreviewTaskBuilder<TemplatePreviewChangeUnit> getTemplateBuilder(String fileName,
                                                                            ChangeTemplateFileContent templatedTaskDefinition) {
        return TemplatePreviewTaskBuilder.builder(templatedTaskDefinition).setFileName(fileName);
    }

    static PreviewTaskBuilder<CodePreviewChangeUnit> getCodeBuilder(TypeElement typeElement) {
        return CodePreviewTaskBuilder.builder(typeElement);
    }

    static CodePreviewTaskBuilder getCodeBuilder() {
        return CodePreviewTaskBuilder.builder();
    }

    A build();

}
