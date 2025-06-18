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

import java.util.List;
import java.util.Map;

public class TemplatePreviewChangeUnit extends AbstractPreviewTask {

    private List<String> profiles;
    private Map<String, Object> templateConfiguration;

    public TemplatePreviewChangeUnit() {}

    //TODO add execution and rollbackMethod
    //TODO add configurationSetter and validation method
    public TemplatePreviewChangeUnit(String id,
                                     String order,
                                     String templateName,
                                     List<String> profiles,
                                     boolean transactional,
                                     boolean runAlways,
                                     boolean system,
                                     Map<String, Object> templateConfiguration) {
        super(id, order, templateName, runAlways, transactional, system);
        this.profiles = profiles;
        this.templateConfiguration = templateConfiguration;
    }

    public Map<String, Object> getTemplateConfiguration() {
        return templateConfiguration;
    }

    public void setTemplateConfiguration(Map<String, Object> templateConfiguration) {
        this.templateConfiguration = templateConfiguration;
    }

    public String getTemplateName() {
        return getSource();
    }

    public List<String> getProfiles() {
        return profiles;
    }

    public void setProfiles(List<String> profiles) {
        this.profiles = profiles;
    }

    @Override
    public String toString() {
        return "TemplatePreviewChangeUnit{" + "profiles=" + profiles +
                ", templateConfiguration=" + templateConfiguration +
                ", id='" + id + '\'' +
                ", order='" + order + '\'' +
                ", source='" + source + '\'' +
                ", runAlways=" + runAlways +
                ", transactional=" + transactional +
                ", system=" + system +
                '}';
    }
}
