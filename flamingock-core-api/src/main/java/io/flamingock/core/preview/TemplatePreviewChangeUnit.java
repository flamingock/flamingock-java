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

package io.flamingock.core.preview;

import java.util.Map;

public class TemplatePreviewChangeUnit extends CodePreviewChangeUnit {

    private Map<String, Object> templateConfiguration;

    public TemplatePreviewChangeUnit() {}

    //TODO add execution and rollbackMethod
    //TODO add configurationSetter and validation method
    public TemplatePreviewChangeUnit(String id,
                                     String order,
                                     String templateClassPath,
                                     boolean transactional,
                                     boolean runAlways,
                                     boolean system,
                                     Map<String, Object> templateConfiguration) {
        super(id, order, templateClassPath, null, null, runAlways, transactional, true, system);
        this.templateConfiguration = templateConfiguration;
    }

    public Map<String, Object> getTemplateConfiguration() {
        return templateConfiguration;
    }

    public void setTemplateConfiguration(Map<String, Object> templateConfiguration) {
        this.templateConfiguration = templateConfiguration;
    }

    @Override
    public String toString() {
        return "TemplatedPreviewChangeUnit{" + "templateConfiguration=" + templateConfiguration +
                ", isNewChangeUnit=" + isNewChangeUnit +
                ", id='" + id + '\'' +
                ", order='" + order + '\'' +
                ", source='" + source + '\'' +
                ", runAlways=" + runAlways +
                ", transactional=" + transactional +
                '}';
    }
}
