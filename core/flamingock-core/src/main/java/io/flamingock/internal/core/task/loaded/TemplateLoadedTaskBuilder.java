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
package io.flamingock.internal.core.task.loaded;

import io.flamingock.api.template.AbstractSimpleTemplate;
import io.flamingock.api.template.AbstractSteppableTemplate;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.api.template.ChangeTemplate;
import io.flamingock.internal.common.core.preview.PreviewConstructor;
import io.flamingock.internal.common.core.template.ChangeTemplateManager;
import io.flamingock.internal.common.core.preview.AbstractPreviewTask;
import io.flamingock.internal.common.core.preview.TemplatePreviewChange;
import io.flamingock.internal.common.core.task.RecoveryDescriptor;
import io.flamingock.internal.common.core.task.TargetSystemDescriptor;
import io.flamingock.internal.util.ReflectionUtil;

import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.List;


//TODO how to set transactional and runAlways
public class TemplateLoadedTaskBuilder implements LoadedTaskBuilder<AbstractTemplateLoadedChange> {

    private String fileName;
    private String id;
    private String order;
    private String author;
    private String templateName;
    private List<String> profiles;
    private boolean runAlways;
    private boolean transactional;
    private boolean system;
    private Object configuration;
    private Object apply;
    private Object rollback;
    private Object steps;
    private TargetSystemDescriptor targetSystem;
    private RecoveryDescriptor recovery;

    private TemplateLoadedTaskBuilder() {
    }

    static TemplateLoadedTaskBuilder getInstance() {
        return new TemplateLoadedTaskBuilder();
    }

    static TemplateLoadedTaskBuilder getInstanceFromPreview(TemplatePreviewChange preview) {
        return getInstance().setPreview(preview);
    }

    public static boolean supportsPreview(AbstractPreviewTask previewTask) {
        return TemplatePreviewChange.class.isAssignableFrom(previewTask.getClass());
    }

    public TemplateLoadedTaskBuilder setId(String id) {
        this.id = id;
        return this;
    }

    @Override
    public TemplateLoadedTaskBuilder setTargetSystem(TargetSystemDescriptor targetSystem) {
        this.targetSystem = targetSystem;
        return this;
    }

    @Override
    public TemplateLoadedTaskBuilder setRecovery(RecoveryDescriptor recovery) {
        this.recovery = recovery;
        return this;
    }

    public TemplateLoadedTaskBuilder setOrder(String order) {
        this.order = order;
        return this;
    }

    public TemplateLoadedTaskBuilder setAuthor(String author) {
        this.author = author;
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

    public TemplateLoadedTaskBuilder setFileName(String fileName) {
        this.fileName = fileName;
        return this;
    }

    public TemplateLoadedTaskBuilder setConfiguration(Object configuration) {
        this.configuration = configuration;
        return this;
    }

    public TemplateLoadedTaskBuilder setApply(Object apply) {
        this.apply = apply;
        return this;
    }

    public TemplateLoadedTaskBuilder setRollback(Object rollback) {
        this.rollback = rollback;
        return this;
    }

    public TemplateLoadedTaskBuilder setSteps(Object steps) {
        this.steps = steps;
        return this;
    }

    @Override
    public AbstractTemplateLoadedChange build() {
        //            boolean isTaskTransactional = true;//TODO implement this. isTaskTransactionalAccordingTemplate(templateSpec);
        Class<? extends ChangeTemplate<?, ?, ?>> templateClass = ChangeTemplateManager.getTemplate(templateName)
                .orElseThrow(()-> new FlamingockException(String.format("Template[%s] not found. This is probably because template's name is wrong or template's library not imported", templateName)));

        Constructor<?> constructor = ReflectionUtil.getDefaultConstructor(templateClass);

        // Determine template type and build appropriate loaded change
        if (AbstractSteppableTemplate.class.isAssignableFrom(templateClass)) {
            return new SteppableTemplateLoadedChange(
                    fileName,
                    id,
                    order,
                    author,
                    templateClass,
                    constructor,
                    profiles,
                    transactional,
                    runAlways,
                    system,
                    configuration,
                    steps,
                    targetSystem,
                    recovery);
        } else {
            // Default to SimpleTemplateLoadedChange for AbstractSimpleTemplate and unknown types
            Class<? extends AbstractSimpleTemplate> steppableTemplateClass =
                    templateClass.asSubclass(AbstractSimpleTemplate.class);
            return new SimpleTemplateLoadedChange(
                    fileName,
                    id,
                    order,
                    author,
                    templateClass,
                    constructor,
                    profiles,
                    transactional,
                    runAlways,
                    system,
                    configuration,
                    apply,
                    rollback,
                    targetSystem,
                    recovery);
        }

    }

    private TemplateLoadedTaskBuilder setPreview(TemplatePreviewChange preview) {

        setFileName(preview.getFileName());
        setId(preview.getId());
        setOrder(preview.getOrder().orElse(null));
        setAuthor(preview.getAuthor());
        setTemplateName(preview.getTemplateName());
        setProfiles(preview.getProfiles());
        setRunAlways(preview.isRunAlways());
        setTransactional(preview.isTransactional());
        setSystem(preview.isSystem());
        setConfiguration(preview.getConfiguration());
        setApply(preview.getApply());
        setRollback(preview.getRollback());
        setSteps(preview.getSteps());
        setTargetSystem(preview.getTargetSystem());
        setRecovery(preview.getRecovery());
        return this;
    }

}
