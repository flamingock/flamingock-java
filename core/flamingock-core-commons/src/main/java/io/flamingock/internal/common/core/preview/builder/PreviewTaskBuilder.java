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
package io.flamingock.internal.common.core.preview.builder;

import io.flamingock.internal.common.core.preview.AbstractPreviewTask;
import io.flamingock.internal.common.core.preview.CodePreviewChange;
import io.flamingock.internal.common.core.preview.TemplatePreviewChange;
import io.flamingock.internal.common.core.template.ChangeTemplateFileContent;

import javax.lang.model.element.TypeElement;

public interface PreviewTaskBuilder<A extends AbstractPreviewTask> {

    static PreviewTaskBuilder<TemplatePreviewChange> getTemplateBuilder(String fileName,
                                                                        ChangeTemplateFileContent templatedTaskDefinition) {
        return TemplatePreviewTaskBuilder.builder(templatedTaskDefinition).setFileName(fileName);
    }

    A build();

}
