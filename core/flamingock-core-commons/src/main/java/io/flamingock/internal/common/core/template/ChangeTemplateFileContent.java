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
package io.flamingock.internal.common.core.template;

import io.flamingock.internal.common.core.task.RecoveryDescriptor;
import io.flamingock.internal.common.core.task.TargetSystemDescriptor;

public class ChangeTemplateFileContent {
    private String id;
    private String order;
    private String template;
    private String profiles; //colon-separated list of profiles
    private Boolean transactional;
    private Object configuration;
    private Object apply;
    private Object rollback;
    private TargetSystemDescriptor targetSystem;
    private RecoveryDescriptor recovery;

    public ChangeTemplateFileContent() {
    }

    public ChangeTemplateFileContent(String id,
                                     String order,
                                     String template,
                                     String profiles,
                                     Boolean transactional,
                                     Object configuration,
                                     Object apply,
                                     Object rollback,
                                     TargetSystemDescriptor targetSystem,
                                     RecoveryDescriptor recovery) {
        this.id = id;
        this.order = order;
        this.template = template;
        this.profiles = profiles;
        this.transactional = transactional;
        this.configuration = configuration;
        this.apply = apply;
        this.rollback = rollback;
        this.targetSystem = targetSystem;
        this.recovery = recovery;
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


    public Boolean getTransactional() {
        return transactional;
    }

    public void setTransactional(Boolean transactional) {
        this.transactional = transactional;
    }

    public Object getConfiguration() {
        return configuration;
    }

    public void setConfiguration(Object configuration) {
        this.configuration = configuration;
    }

    public Object getApply() {
        return apply;
    }

    public void setApply(Object apply) {
        this.apply = apply;
    }

    public Object getRollback() {
        return rollback;
    }

    public void setRollback(Object rollback) {
        this.rollback = rollback;
    }

    public TargetSystemDescriptor getTargetSystem() {
        return targetSystem;
    }

    public void setTargetSystem(TargetSystemDescriptor targetSystem) {
        this.targetSystem = targetSystem;
    }

    public RecoveryDescriptor getRecovery() {
        return recovery;
    }

    public void setRecovery(RecoveryDescriptor recovery) {
        this.recovery = recovery;
    }
}
