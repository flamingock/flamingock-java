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

package io.flamingock.api.template;

import java.util.Map;

public class ChangeFileDescriptor {
    private String id;

    private String order;

    private String template;

    private String profiles; //colon-separated list of profiles

    private Boolean transactional;

    private Map<String, Object> templateConfiguration;

    public ChangeFileDescriptor() {
    }

    public ChangeFileDescriptor(String id,
                                String order,
                                String template,
                                String profiles,
                                Boolean transactional,
                                Map<String, Object> templateConfiguration) {
        this.id = id;
        this.order = order;
        this.template = template;
        this.profiles = profiles;
        this.transactional = transactional;
        this.templateConfiguration = templateConfiguration;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOrder() {
        return order;
    }

    public void setOrder(String order) {
        this.order = order;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    public String getProfiles() {
        return profiles;
    }

    public void setProfiles(String profiles) {
        this.profiles = profiles;
    }

    public Map<String, Object> getTemplateConfiguration() {
        return templateConfiguration;
    }

    public Boolean getTransactional() {
        return transactional;
    }

    public void setTransactional(Boolean transactional) {
        this.transactional = transactional;
    }

    public void setTemplateConfiguration(Map<String, Object> templateConfiguration) {
        this.templateConfiguration = templateConfiguration;
    }
}
