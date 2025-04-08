package io.flamingock.core.preview.builder;

import io.flamingock.core.preview.AbstractPreviewTask;
import io.flamingock.core.preview.CodePreviewChangeUnit;
import io.flamingock.core.preview.TemplatePreviewChangeUnit;
import io.flamingock.core.api.template.ChangeFileDescriptor;

import javax.lang.model.element.TypeElement;

public interface PreviewTaskBuilder<A extends AbstractPreviewTask> {

    static PreviewTaskBuilder<TemplatePreviewChangeUnit> getTemplateBuilder(ChangeFileDescriptor templatedTaskDefinition) {
        return TemplatePreviewTaskBuilder.builder(templatedTaskDefinition);
    }

    static PreviewTaskBuilder<CodePreviewChangeUnit> getCodeBuilder(TypeElement typeElement) {
        return CodePreviewTaskBuilder.builder(typeElement);
    }

    static CodePreviewTaskBuilder getCodeBuilder() {
        return CodePreviewTaskBuilder.builder();
    }

    A build();

}
