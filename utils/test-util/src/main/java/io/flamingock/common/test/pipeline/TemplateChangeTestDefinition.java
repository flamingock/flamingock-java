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
package io.flamingock.common.test.pipeline;

import io.flamingock.internal.common.core.preview.AbstractPreviewTask;
import io.flamingock.internal.common.core.preview.TemplatePreviewChange;
import io.flamingock.internal.common.core.task.RecoveryDescriptor;
import io.flamingock.internal.common.core.task.TargetSystemDescriptor;

import java.util.Collections;

public class TemplateChangeTestDefinition extends ChangeTestDefinition {


    private final String fileName;
    private final String templateName;
    private final Object configuration;
    private final Object apply;
    private final Object rollback;
    private final String targetSystem;


    public TemplateChangeTestDefinition(String fileName,
                                        String id,
                                        String order,
                                        String templateName,
                                        boolean transactional,
                                        Object configuration,
                                        Object apply,
                                        Object rollback) {
        this(fileName, id, order, templateName, transactional, configuration, apply, rollback, null);
    }

    public TemplateChangeTestDefinition(String fileName,
                                        String id,
                                        String order,
                                        String templateName,
                                        boolean transactional,
                                        Object configuration,
                                        Object apply,
                                        Object rollback,
                                        String targetSystem) {
        super(id, order, transactional);
        this.fileName = fileName;
        this.templateName = templateName;
        this.configuration = configuration;
        this.apply = apply;
        this.rollback = rollback;
        this.targetSystem = targetSystem;
    }


    @Override
    public AbstractPreviewTask toPreview() {
        return new TemplatePreviewChange(
                fileName,
                getId(),
                getOrder(),
                "test-author", // Default author for tests
                templateName,
                Collections.emptyList(),
                isTransactional(),
                false,
                false,
                configuration,
                apply,
                rollback,
                TargetSystemDescriptor.fromId(targetSystem),
                RecoveryDescriptor.getDefault()
        );
    }

}
