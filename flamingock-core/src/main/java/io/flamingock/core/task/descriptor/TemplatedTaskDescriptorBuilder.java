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

package io.flamingock.core.task.descriptor;

import io.flamingock.core.api.exception.FlamingockException;
import io.flamingock.template.TemplateSpec;
import io.flamingock.template.TemplatedTaskDefinition;
import io.flamingock.template.TemplateFactory;
import io.flamingock.template.TransactionalTemplateSpec;

import java.util.Map;


//TODO how to set transactional and runAlways
public class TemplatedTaskDescriptorBuilder {
    private static final TemplatedTaskDescriptorBuilder instance = new TemplatedTaskDescriptorBuilder();

    public static TemplatedTaskDescriptorBuilder recycledBuilder() {
        return instance;
    }

    private String id;

    private String order;

    private boolean runAlways;

    private Boolean transactional;

    private String templateName;

    private Map<String, Object> templateConfiguration;

    private TemplatedTaskDescriptorBuilder() {
    }

    public TemplatedTaskDescriptorBuilder setId(String id) {
        this.id = id;
        return this;
    }

    public TemplatedTaskDescriptorBuilder setOrder(String order) {
        this.order = order;
        return this;
    }

    public TemplatedTaskDescriptorBuilder setRunAlways(boolean runAlways) {
        this.runAlways = runAlways;
        return this;
    }

    public TemplatedTaskDescriptorBuilder setTransactional(Boolean transactional) {
        this.transactional = transactional;
        return this;
    }

    public TemplatedTaskDescriptorBuilder setTemplateName(String templateName) {
        this.templateName = templateName;
        return this;
    }

    public TemplatedTaskDescriptorBuilder setTemplateConfiguration(Map<String, Object> templateConfiguration) {
        this.templateConfiguration = templateConfiguration;
        return this;
    }

    public TemplatedChangeUnitTaskDescriptor build() {

        TemplateSpec templateSpec = TemplateFactory.getTemplate(templateName)
                .orElseThrow(() -> new FlamingockException("Template not found: " + templateName));

        boolean isTaskTransactional = isTaskTransactionalAccordingTemplate(templateSpec);
        return new TemplatedChangeUnitTaskDescriptor(id, order, templateSpec.getTemplateClass(), isTaskTransactional, runAlways, templateConfiguration);
    }

    public TemplatedTaskDescriptorBuilder setFromDefinition(TemplatedTaskDefinition templatedTaskDefinition) {
        setId(templatedTaskDefinition.getId());
        setOrder(templatedTaskDefinition.getOrder());
        setTemplateName(templatedTaskDefinition.getTemplateName());
        setTemplateConfiguration(templatedTaskDefinition.getTemplateConfiguration());
        setTransactional(templatedTaskDefinition.getTransactional());
//        setRunAlways(templateYaml.getRunAlways());
        return this;
    }

    private boolean isTaskTransactionalAccordingTemplate(TemplateSpec templateSpec) {
        boolean isTemplateTransactional = TransactionalTemplateSpec.class.isAssignableFrom(templateSpec.getClass());
        if(!isTemplateTransactional) {
            return false;
        } else {
            return transactional != null ? transactional : true;
        }
    }
}
