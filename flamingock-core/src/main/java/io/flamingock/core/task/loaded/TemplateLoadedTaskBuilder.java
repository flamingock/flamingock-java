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
import io.flamingock.core.preview.TemplatePreviewChangeUnit;

import java.util.Map;


//TODO how to set transactional and runAlways
public class TemplateLoadedTaskBuilder implements LoadedTaskBuilder<TemplateLoadedChangeUnit> {

    private static final String TEMPLATE_NOT_FOUND_MSG = "Template [%s] not found. Ensure that the template's library is correctly imported and that the template class field in your templated ChangeUnit specifies the correct class path";
    private String id;
    private String order;
    private String source;
    private boolean runAlways;
    private boolean transactional;
    private boolean system;
    private Map<String, Object> templateConfiguration;

    private TemplateLoadedTaskBuilder() {
    }

    static TemplateLoadedTaskBuilder getInstance() {
        return new TemplateLoadedTaskBuilder();
    }

    static TemplateLoadedTaskBuilder getInstanceFromPreview(TemplatePreviewChangeUnit preview) {
        return getInstance().setPreview(preview);
    }

    public static boolean supportsPreview(AbstractPreviewTask previewTask) {
        return TemplatePreviewChangeUnit.class.isAssignableFrom(previewTask.getClass());
    }


    public TemplateLoadedTaskBuilder setId(String id) {
        this.id = id;
        return this;
    }

    public TemplateLoadedTaskBuilder setOrder(String order) {
        this.order = order;
        return this;
    }

    public TemplateLoadedTaskBuilder setSource(String source) {
        this.source = source;
        return this;
    }

    public TemplateLoadedTaskBuilder setRunAlways(boolean runAlways) {
        this.runAlways = runAlways;
        return this;
    }

    public TemplateLoadedTaskBuilder setTransactional(boolean transactional) {
        this.transactional = transactional;
        return this;
    }

    public TemplateLoadedTaskBuilder setSystem(boolean system) {
        this.system = system;
        return this;
    }

    public TemplateLoadedTaskBuilder setTemplateConfiguration(Map<String, Object> templateConfiguration) {
        this.templateConfiguration = templateConfiguration;
        return this;
    }

    @Override
    public TemplateLoadedChangeUnit build() {
        try {
//            boolean isTaskTransactional = true;//TODO implement this. isTaskTransactionalAccordingTemplate(templateSpec);
            return new TemplateLoadedChangeUnit(
                    id,
                    order,
                    Class.forName(source),
                    transactional,
                    runAlways,
                    system,
                    templateConfiguration);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(String.format(TEMPLATE_NOT_FOUND_MSG, source));
        }

    }

    private TemplateLoadedTaskBuilder setPreview(TemplatePreviewChangeUnit preview) {
        setId(preview.getId());
        setOrder(preview.getOrder().orElse(null));
        setSource(preview.getSource());
        setRunAlways(preview.isRunAlways());
        setTransactional(preview.isTransactional());
        setSystem(preview.isSystem());
        setTemplateConfiguration(preview.getTemplateConfiguration());
        return this;
    }

}
