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

package io.flamingock.internal.common.core.preview;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.flamingock.internal.common.core.task.AbstractTaskDescriptor;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = CodePreviewChangeUnit.class, name = "codePreviewChangeUnit"),
        @JsonSubTypes.Type(value = TemplatePreviewChangeUnit.class, name = "templatePreviewChangeUnit")
})
public abstract class AbstractPreviewTask extends AbstractTaskDescriptor {

    public AbstractPreviewTask() {
        super();
    }

    public AbstractPreviewTask(String id,
                               String order,
                               String source,
                               boolean runAlways,
                               boolean transactional,
                               boolean system) {
        super(id, order, source, runAlways, transactional, system);
    }


}
