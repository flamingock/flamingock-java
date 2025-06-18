package io.flamingock.internal.common.core.preview.builder;

import io.flamingock.internal.common.core.preview.AbstractPreviewTask;
import io.flamingock.internal.common.core.preview.CodePreviewChangeUnit;
import io.flamingock.internal.common.core.preview.TemplatePreviewChangeUnit;
import io.flamingock.api.template.ChangeFileDescriptor;

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
