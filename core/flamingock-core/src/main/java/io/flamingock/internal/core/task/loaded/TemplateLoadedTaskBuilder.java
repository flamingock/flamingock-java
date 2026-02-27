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

import io.flamingock.api.template.AbstractChangeTemplate;
import io.flamingock.api.template.TemplatePayload;
import io.flamingock.api.template.TemplateStep;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.common.core.error.validation.ValidationResult;
import io.flamingock.internal.common.core.preview.AbstractPreviewTask;
import io.flamingock.internal.common.core.preview.TemplatePreviewChange;
import io.flamingock.internal.common.core.task.RecoveryDescriptor;
import io.flamingock.internal.common.core.task.TargetSystemDescriptor;
import io.flamingock.internal.common.core.template.ChangeTemplateDefinition;
import io.flamingock.internal.common.core.template.ChangeTemplateManager;
import io.flamingock.internal.common.core.template.TemplateValidator;
import io.flamingock.internal.util.FileUtil;
import io.flamingock.internal.util.Pair;
import io.flamingock.internal.util.ReflectionUtil;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;


//TODO how to set transactional and runAlways
public class TemplateLoadedTaskBuilder implements LoadedTaskBuilder<AbstractTemplateLoadedChange<?, ?, ?>> {

    private static final TemplateValidator DEFAULT_VALIDATOR = new TemplateValidator();

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
    private Object applyPayload;
    private Object rollbackPayload;
    private Object steps;
    private TargetSystemDescriptor targetSystem;
    private RecoveryDescriptor recovery;
    private TemplatePreviewChange preview;
    private final TemplateValidator templateValidator;

    private TemplateLoadedTaskBuilder() {
        this(DEFAULT_VALIDATOR);
    }

    private TemplateLoadedTaskBuilder(TemplateValidator templateValidator) {
        this.templateValidator = templateValidator;
    }

    static TemplateLoadedTaskBuilder getInstance() {
        return new TemplateLoadedTaskBuilder();
    }

    static TemplateLoadedTaskBuilder getInstance(TemplateValidator templateValidator) {
        return new TemplateLoadedTaskBuilder(templateValidator);
    }

    static TemplateLoadedTaskBuilder getInstanceFromPreview(TemplatePreviewChange preview) {
        return getInstance().setPreview(preview);
    }

    static TemplateLoadedTaskBuilder getInstanceFromPreview(TemplatePreviewChange preview, TemplateValidator templateValidator) {
        return getInstance(templateValidator).setPreview(preview);
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

    public TemplateLoadedTaskBuilder setApplyPayload(Object applyPayload) {
        this.applyPayload = applyPayload;
        return this;
    }

    public TemplateLoadedTaskBuilder setRollbackPayload(Object rollbackPayload) {
        this.rollbackPayload = rollbackPayload;
        return this;
    }

    public TemplateLoadedTaskBuilder setSteps(Object steps) {
        this.steps = steps;
        return this;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public AbstractTemplateLoadedChange<?, ?, ?> build() {
        //            boolean isTaskTransactional = true;//TODO implement this. isTaskTransactionalAccordingTemplate(templateSpec);
        ChangeTemplateDefinition definition = ChangeTemplateManager.getTemplate(templateName)
                .orElseThrow(()-> new FlamingockException(String.format("Template[%s] not found. This is probably because template's name is wrong or template's library not imported", templateName)));


        if (preview != null) {
            ValidationResult validationResult = templateValidator.validateStructure(definition, preview);
            if (validationResult.hasErrors()) {
                throw new FlamingockException(
                        "Template structure validation failed for change '" + id + "':\n" + validationResult.formatMessage());
            }
        }

        Constructor<?> constructor = ReflectionUtil.getDefaultConstructor(definition.getTemplateClass());

        // Determine template type from pre-resolved definition metadata.
        // Note: Due to type erasure, we use raw types at construction time.
        // The actual type safety comes from the conversion methods that use reflection
        // to determine the real types at runtime.
        boolean isMultiStep = definition.isMultiStep();
        boolean rollbackPayloadRequired = definition.isRollbackPayloadRequired();

        Class<? extends AbstractChangeTemplate> templateClass =
                definition.getTemplateClass().asSubclass(AbstractChangeTemplate.class);

        if (isMultiStep) {
            return getLoadedMultiStepTemplateChange(templateClass, constructor, rollbackPayloadRequired);
        } else {
            // Default to SimpleTemplateLoadedChange for simple templates (steppable=false or missing annotation)
            return getLoadedSimpleTemplateChange(templateClass, constructor, rollbackPayloadRequired);
        }
    }

    @NotNull
    @SuppressWarnings({"unchecked", "rawtypes"})
    private MultiStepTemplateLoadedChange getLoadedMultiStepTemplateChange(Class<? extends AbstractChangeTemplate> steppableTemplateClass, Constructor<?> constructor, boolean rollbackPayloadRequired) {
        List<TemplateStep<Object, Object>> convertedSteps = convertSteps(constructor, steps);// Convert apply/rollback to typed payloads at load time
        return new MultiStepTemplateLoadedChange(
                fileName,
                id,
                order,
                author,
                steppableTemplateClass,
                constructor,
                profiles,
                transactional,
                runAlways,
                system,
                configuration,
                convertedSteps,
                targetSystem,
                recovery,
                rollbackPayloadRequired);
    }

    @NotNull
    @SuppressWarnings({"unchecked", "rawtypes"})
    private SimpleTemplateLoadedChange getLoadedSimpleTemplateChange(Class<? extends AbstractChangeTemplate> simpleTemplateClass, Constructor<?> constructor, boolean rollbackPayloadRequired) {
        Pair<Object, Object> convertedPayloads = convertPayloads(constructor, applyPayload, rollbackPayload);// Convert apply/rollback to typed payloads at load time
        return new SimpleTemplateLoadedChange(
                fileName,
                id,
                order,
                author,
                simpleTemplateClass,
                constructor,
                profiles,
                transactional,
                runAlways,
                system,
                configuration,
                (TemplatePayload) convertedPayloads.getFirst(),
                (TemplatePayload) convertedPayloads.getSecond(),
                targetSystem,
                recovery,
                rollbackPayloadRequired);
    }

    /**
     * Converts raw apply/rollback data to typed payloads for simple templates.
     * Returns Pair<applyPayload, rollbackPayload>.
     */
    private Pair<Object, Object> convertPayloads(Constructor<?> constructor, Object applyData, Object rollbackData) {
        if (applyData == null) {
            return new Pair<>(null, null);
        }

        // Instantiate template temporarily to get payload types
        AbstractChangeTemplate<?, ?, ?> templateInstance;
        try {
            templateInstance = (AbstractChangeTemplate<?, ?, ?>) constructor.newInstance();
        } catch (Exception e) {
            throw new FlamingockException("Failed to instantiate template for type resolution: " + e.getMessage(), e);
        }

        Class<?> applyClass = templateInstance.getApplyPayloadClass();
        Class<?> rollbackClass = templateInstance.getRollbackPayloadClass();

        Object applyPayload = FileUtil.getFromMap(applyClass, applyData);
        Object rollbackPayload = null;

        if (rollbackData != null && Void.class != rollbackClass) {
            rollbackPayload = FileUtil.getFromMap(rollbackClass, rollbackData);
        }

        return new Pair<>(applyPayload, rollbackPayload);
    }

    /**
     * Converts raw steps data from YAML to typed TemplateStep objects.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<TemplateStep<Object, Object>> convertSteps(Constructor<?> constructor, Object stepsData) {
        if (stepsData == null) {
            return null;
        }

        if (!(stepsData instanceof List)) {
            throw new FlamingockException(String.format(
                "Steps must be a List for steppable template change[%s], but got: %s",
                id, stepsData.getClass().getSimpleName()));
        }

        List<?> stepsList = (List<?>) stepsData;
        if (stepsList.isEmpty()) {
            return Collections.emptyList();
        }

        // Instantiate template temporarily to get payload types
        AbstractChangeTemplate<?, ?, ?> templateInstance;
        try {
            templateInstance = (AbstractChangeTemplate<?, ?, ?>) constructor.newInstance();
        } catch (Exception e) {
            throw new FlamingockException("Failed to instantiate template for type resolution: " + e.getMessage(), e);
        }

        Class<?> applyClass = templateInstance.getApplyPayloadClass();
        Class<?> rollbackClass = templateInstance.getRollbackPayloadClass();

        List<TemplateStep<Object, Object>> result = new ArrayList<>();

        for (Object stepItem : stepsList) {
            if (stepItem instanceof Map) {
                Map<String, Object> stepMap = (Map<String, Object>) stepItem;
                TemplateStep<Object, Object> step = new TemplateStep<>();

                Object applyItemData = stepMap.get("apply");
                if (applyItemData != null && Void.class != applyClass) {
                    step.setApplyPayload(FileUtil.getFromMap(applyClass, applyItemData));
                }

                Object rollbackItemData = stepMap.get("rollback");
                if (rollbackItemData != null && Void.class != rollbackClass) {
                    step.setRollbackPayload(FileUtil.getFromMap(rollbackClass, rollbackItemData));
                }

                result.add(step);
            } else if (stepItem instanceof TemplateStep) {
                result.add((TemplateStep<Object, Object>) stepItem);
            }
        }

        return result;
    }

    private TemplateLoadedTaskBuilder setPreview(TemplatePreviewChange preview) {
        this.preview = preview;
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
        setApplyPayload(preview.getApply());
        setRollbackPayload(preview.getRollback());
        setSteps(preview.getSteps());
        setTargetSystem(preview.getTargetSystem());
        setRecovery(preview.getRecovery());
        return this;
    }

}
