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
import io.flamingock.internal.common.core.change.RecoveryDescriptor;
import io.flamingock.internal.common.core.change.TargetSystemDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;


class TemplatePreviewChangeBuilder implements PreviewChangeBuilder<TemplatePreviewChange> {

    private String fileName;
    private String id;
    private String author;
    private String templateClassPath;
    private String profilesString;
    private boolean runAlways;
    private Boolean transactionalFlag;
    private Object configuration;
    private Object apply;
    private Object rollback;
    private Object steps;
    private TargetSystemDescriptor targetSystem;
    private RecoveryDescriptor recovery;


    private TemplatePreviewChangeBuilder() {
    }

    static TemplatePreviewChangeBuilder builder() {
        return new TemplatePreviewChangeBuilder();
    }

    static TemplatePreviewChangeBuilder builder(ChangeTemplateFileContent templateFileContent) {
        return new TemplatePreviewChangeBuilder().setFromDefinition(templateFileContent);
    }

    public TemplatePreviewChangeBuilder setFileName(String fileName) {
        this.fileName = fileName;
        return this;
    }

    public TemplatePreviewChangeBuilder setId(String id) {
        this.id = id;
        return this;
    }

    public TemplatePreviewChangeBuilder setAuthor(String author) {
        this.author = author;
        return this;
    }

    public void setTemplate(String templateClassPath) {
        this.templateClassPath = templateClassPath;
    }

    public void setProfilesString(String profilesString) {
        this.profilesString = profilesString;
    }

    public TemplatePreviewChangeBuilder setRunAlways(boolean runAlways) {
        this.runAlways = runAlways;
        return this;
    }

    public TemplatePreviewChangeBuilder setTransactionalFlag(Boolean transactionalFlag) {
        this.transactionalFlag = transactionalFlag;
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
                transactionalFlag,
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


    TemplatePreviewChangeBuilder setFromDefinition(ChangeTemplateFileContent templateChangeDescriptor) {
        //fileName is set in "setFileName" method
        //order is extract from the fileName in the "build" method
        setId(templateChangeDescriptor.getId());
        setAuthor(templateChangeDescriptor.getAuthor());
        setTemplate(templateChangeDescriptor.getTemplate());
        setProfilesString(templateChangeDescriptor.getProfiles());
        setConfiguration(templateChangeDescriptor.getConfiguration());
        setApply(templateChangeDescriptor.getApply());
        setRollback(templateChangeDescriptor.getRollback());
        setSteps(templateChangeDescriptor.getSteps());
        setTransactionalFlag(templateChangeDescriptor.getTransactional());
        setRunAlways(false);
        setTargetSystem(templateChangeDescriptor.getTargetSystem());
        setRecovery(templateChangeDescriptor.getRecovery() != null ? templateChangeDescriptor.getRecovery() : RecoveryDescriptor.getDefault());
        return this;
    }

}
