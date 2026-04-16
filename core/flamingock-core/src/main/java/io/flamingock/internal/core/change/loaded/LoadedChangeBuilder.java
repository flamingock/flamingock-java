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
package io.flamingock.internal.core.change.loaded;

import io.flamingock.internal.common.core.preview.AbstractPreviewChange;
import io.flamingock.internal.common.core.preview.CodePreviewChange;
import io.flamingock.internal.common.core.preview.TemplatePreviewChange;
import io.flamingock.internal.common.core.change.RecoveryDescriptor;
import io.flamingock.internal.common.core.change.TargetSystemDescriptor;

public interface LoadedChangeBuilder<LOADED_CHANGE extends AbstractLoadedChange> {

    static AbstractLoadedChange build(AbstractPreviewChange previewChange) {
        return getInstance(previewChange).build();
    }

    static LoadedChangeBuilder<?> getInstance(AbstractPreviewChange previewChange) {
        if (TemplateLoadedChangeBuilder.supportsPreview(previewChange)) {
            return  TemplateLoadedChangeBuilder.getInstanceFromPreview((TemplatePreviewChange) previewChange);

        } else if (CodeLoadedChangeBuilder.supportsPreview(previewChange)) {
            return CodeLoadedChangeBuilder.getInstanceFromPreview((CodePreviewChange) previewChange);

        }
        throw new RuntimeException("Not implemented build from preview to loaded");
    }

    static CodeLoadedChangeBuilder getCodeBuilderInstance(Class<?> sourceClass) {
        if (CodeLoadedChangeBuilder.supportsSourceClass(sourceClass)) {
            return CodeLoadedChangeBuilder.getInstanceFromClass(sourceClass);

        }
        throw new RuntimeException("Not implemented build from preview to loaded");
    }


    LoadedChangeBuilder<LOADED_CHANGE> setId(String id);

    LoadedChangeBuilder<LOADED_CHANGE> setTargetSystem(TargetSystemDescriptor targetSystem);

    LoadedChangeBuilder<LOADED_CHANGE> setRecovery(RecoveryDescriptor recovery);

    LoadedChangeBuilder<LOADED_CHANGE> setRunAlways(boolean runAlways);

    LoadedChangeBuilder<LOADED_CHANGE> setTransactionalFlag(Boolean transactionalFlag);

    LoadedChangeBuilder<LOADED_CHANGE> setSystem(boolean system);

    LOADED_CHANGE build();

}
