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
package io.flamingock.internal.common.core.preview.builder;

import io.flamingock.internal.common.core.preview.ChangeOrderExtractor;
import io.flamingock.internal.common.core.template.ChangeTemplateFileContent;
import io.flamingock.internal.common.core.preview.TemplatePreviewChange;
import io.flamingock.internal.common.core.task.RecoveryDescriptor;
import io.flamingock.internal.common.core.task.TargetSystemDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;


class TemplatePreviewTaskBuilder implements PreviewTaskBuilder<TemplatePreviewChange> {

    private String fileName;
    private String id;
    private String author;
    private String templateClassPath;
    private String profilesString;
    private boolean runAlways;
    private Boolean transactional;
    private Object configuration;
    private Object apply;
    private Object rollback;
    private Object steps;
    private TargetSystemDescriptor targetSystem;
    private RecoveryDescriptor recovery;


    private TemplatePreviewTaskBuilder() {
    }

    static TemplatePreviewTaskBuilder builder() {
        return new TemplatePreviewTaskBuilder();
    }

    static TemplatePreviewTaskBuilder builder(ChangeTemplateFileContent templateFileContent) {
        return new TemplatePreviewTaskBuilder().setFromDefinition(templateFileContent);
    }

    public TemplatePreviewTaskBuilder setFileName(String fileName) {
        this.fileName = fileName;
        return this;
    }

    public TemplatePreviewTaskBuilder setId(String id) {
        this.id = id;
        return this;
    }

    public TemplatePreviewTaskBuilder setAuthor(String author) {
        this.author = author;
        return this;
    }

    public void setTemplate(String templateClassPath) {
        this.templateClassPath = templateClassPath;
    }

    public void setProfilesString(String profilesString) {
        this.profilesString = profilesString;
    }

    public TemplatePreviewTaskBuilder setRunAlways(boolean runAlways) {
        this.runAlways = runAlways;
        return this;
    }

    public TemplatePreviewTaskBuilder setTransactional(Boolean transactional) {
        this.transactional = transactional;
        return this;
    }

    public void setRollback(Object rollback) {
        this.rollback = rollback;
    }

    public void setApply(Object apply) {
        this.apply = apply;
    }

    public void setConfiguration(Object configuration) {
        this.configuration = configuration;
    }

    public void setTargetSystem(TargetSystemDescriptor targetSystem) {
        this.targetSystem = targetSystem;
    }

    public void setRecovery(RecoveryDescriptor recovery) {
        this.recovery = recovery;
    }

    public void setSteps(Object steps) {
        this.steps = steps;
    }

    @Override
    public TemplatePreviewChange build() {

        List<String> profiles = getProfiles();
        String order = ChangeOrderExtractor.extractOrderFromFile(id, fileName);

        return new TemplatePreviewChange(
                fileName,
                id,
                order,
                author,
                templateClassPath,
                profiles,
                transactional,
                runAlways,
                false,
                configuration,
                apply,
                rollback,
                steps,
                targetSystem,
                recovery);
    }

    @NotNull
    private List<String> getProfiles() {
        if(profilesString == null) {
            return Collections.emptyList();
        }

        return Arrays.stream(profilesString.trim().split(","))
                .map(String::trim)
                .collect(Collectors.toList());
    }


    TemplatePreviewTaskBuilder setFromDefinition(ChangeTemplateFileContent templateTaskDescriptor) {
        //fileName is set in "setFileName" method
        //order is extract from the fileName in the "build" method
        setId(templateTaskDescriptor.getId());
        setAuthor(templateTaskDescriptor.getAuthor());
        setTemplate(templateTaskDescriptor.getTemplate());
        setProfilesString(templateTaskDescriptor.getProfiles());
        setConfiguration(templateTaskDescriptor.getConfiguration());
        setApply(templateTaskDescriptor.getApply());
        setRollback(templateTaskDescriptor.getRollback());
        setSteps(templateTaskDescriptor.getSteps());
        setTransactional(templateTaskDescriptor.getTransactional() != null ? templateTaskDescriptor.getTransactional() : true);
        setRunAlways(false);
        setTargetSystem(templateTaskDescriptor.getTargetSystem());
        setRecovery(templateTaskDescriptor.getRecovery() != null ? templateTaskDescriptor.getRecovery() : RecoveryDescriptor.getDefault());
        return this;
    }

}
