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

package io.flamingock.internal.core.task.loaded;

import io.flamingock.core.api.exception.FlamingockException;
import io.flamingock.core.api.template.ChangeTemplate;
import io.flamingock.core.api.template.TemplateFactory;
import io.flamingock.core.preview.AbstractPreviewTask;
import io.flamingock.core.preview.TemplatePreviewChangeUnit;

import java.util.List;
import java.util.Map;


//TODO how to set transactional and runAlways
public class TemplateLoadedTaskBuilder implements LoadedTaskBuilder<TemplateLoadedChangeUnit> {

    private static final String TEMPLATE_NOT_FOUND_MSG = "Template [%s] not found. Ensure that the template's library is correctly imported and that the template class field in your templated ChangeUnit specifies the correct class path";
    private String id;
    private String order;
    private String templateName;
    private List<String> profiles;
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

    public TemplateLoadedTaskBuilder setTemplateName(String templateName) {
        this.templateName = templateName;
        return this;
    }

    public void setProfiles(List<String> profiles) {
        this.profiles = profiles;
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
        //            boolean isTaskTransactional = true;//TODO implement this. isTaskTransactionalAccordingTemplate(templateSpec);
        Class<? extends ChangeTemplate<?>> templateClass = TemplateFactory.getTemplate(templateName)
                .orElseThrow(()-> new FlamingockException(String.format("Template[%s] not found. This is probably because template's name is wrong or template's library not imported", templateName)));
        return new TemplateLoadedChangeUnit(
                id,
                order,
                templateClass,
                profiles,
                transactional,
                runAlways,
                system,
                templateConfiguration);

    }

    private TemplateLoadedTaskBuilder setPreview(TemplatePreviewChangeUnit preview) {
        setId(preview.getId());
        setOrder(preview.getOrder().orElse(null));
        setTemplateName(preview.getTemplateName());
        setProfiles(preview.getProfiles());
        setRunAlways(preview.isRunAlways());
        setTransactional(preview.isTransactional());
        setSystem(preview.isSystem());
        setTemplateConfiguration(preview.getTemplateConfiguration());
        return this;
    }

}
