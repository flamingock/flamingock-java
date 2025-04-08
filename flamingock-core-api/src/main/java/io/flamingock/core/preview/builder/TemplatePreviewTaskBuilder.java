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

package io.flamingock.core.preview.builder;

import io.flamingock.core.preview.TemplatePreviewChangeUnit;
import io.flamingock.core.api.template.ChangeFileDescriptor;

import java.util.Map;


//TODO how to set transactional and runAlways
class TemplatePreviewTaskBuilder implements PreviewTaskBuilder<TemplatePreviewChangeUnit> {

    private String id;
    private String order;
    private String templateClassPath;
    private boolean runAlways;
    private Boolean transactional;
    private Map<String, Object> templateConfiguration;

    private TemplatePreviewTaskBuilder() {
    }

    static TemplatePreviewTaskBuilder builder() {
        return new TemplatePreviewTaskBuilder();
    }

    static TemplatePreviewTaskBuilder builder(ChangeFileDescriptor templateTaskDefinition) {
        return new TemplatePreviewTaskBuilder().setFromDefinition(templateTaskDefinition);
    }


    public TemplatePreviewTaskBuilder setId(String id) {
        this.id = id;
        return this;
    }

    public TemplatePreviewTaskBuilder setOrder(String order) {
        this.order = order;
        return this;
    }

    public void setTemplate(String templateClassPath) {
        this.templateClassPath = templateClassPath;
    }


    public TemplatePreviewTaskBuilder setRunAlways(boolean runAlways) {
        this.runAlways = runAlways;
        return this;
    }

    public TemplatePreviewTaskBuilder setTransactional(Boolean transactional) {
        this.transactional = transactional;
        return this;
    }

    public TemplatePreviewTaskBuilder setTemplateConfiguration(Map<String, Object> templateConfiguration) {
        this.templateConfiguration = templateConfiguration;
        return this;
    }

    @Override
    public TemplatePreviewChangeUnit build() {
        return new TemplatePreviewChangeUnit(
                id,
                order,
                templateClassPath,
                transactional,
                runAlways,
                false,
                templateConfiguration);
    }


    TemplatePreviewTaskBuilder setFromDefinition(ChangeFileDescriptor templateTaskDefinition) {
        setId(templateTaskDefinition.getId());
        setOrder(templateTaskDefinition.getOrder());
        setTemplate(templateTaskDefinition.getTemplate());
        setTemplateConfiguration(templateTaskDefinition.getTemplateConfiguration());
        setTransactional(templateTaskDefinition.getTransactional());
        setRunAlways(false);
        return this;
    }

}
