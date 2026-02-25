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
import io.flamingock.api.template.TemplateStep;
import io.flamingock.internal.common.core.error.validation.ValidationError;
import io.flamingock.internal.common.core.task.RecoveryDescriptor;
import io.flamingock.internal.common.core.task.TargetSystemDescriptor;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Loaded change for steppable templates (multiple steps).
 * Used for templates annotated with {@code @ChangeTemplate(steppable = true)}.
 * <p>
 * The steps are converted from raw YAML data (List of Maps) to typed TemplateStep objects
 * at load time, enabling early validation and cleaner executable tasks.
 *
 * @param <CONFIG>   the configuration type for the template
 * @param <APPLY>    the apply payload type
 * @param <ROLLBACK> the rollback payload type
 */
public class MultiStepTemplateLoadedChange<CONFIG, APPLY, ROLLBACK>
        extends AbstractTemplateLoadedChange<CONFIG, APPLY, ROLLBACK> {

    private final List<TemplateStep<APPLY, ROLLBACK>> steps;

    MultiStepTemplateLoadedChange(String changeFileName,
                                  String id,
                                  String order,
                                  String author,
                                  Class<? extends AbstractChangeTemplate<CONFIG, APPLY, ROLLBACK>> templateClass,
                                  Constructor<?> constructor,
                                  List<String> profiles,
                                  boolean transactional,
                                  boolean runAlways,
                                  boolean systemTask,
                                  CONFIG configuration,
                                  List<TemplateStep<APPLY, ROLLBACK>> steps,
                                  TargetSystemDescriptor targetSystem,
                                  RecoveryDescriptor recovery,
                                  boolean rollbackPayloadRequired) {
        super(changeFileName, id, order, author, templateClass, constructor, profiles, transactional, runAlways, systemTask, configuration, targetSystem, recovery, rollbackPayloadRequired);
        this.steps = steps;
    }

    public List<TemplateStep<APPLY, ROLLBACK>> getSteps() {
        return steps;
    }

    @Override
    protected List<ValidationError> validateConfigurationPayload() {
        return Collections.emptyList();
    }

    @Override
    protected List<ValidationError> validateApplyPayload() {
        List<ValidationError> errors = new ArrayList<>();
        if (steps != null) {
            for (int i = 0; i < steps.size(); i++) {
                if (steps.get(i).getApplyPayload() == null) {
                    errors.add(new ValidationError(
                            String.format("Template '%s', step %d: missing required 'apply' payload", getSource(), i + 1),
                            getId(), "change"));
                }
            }
        }
        return errors;
    }

    @Override
    protected List<ValidationError> validateRollbackPayload() {
        List<ValidationError> errors = new ArrayList<>();
        if (rollbackPayloadRequired && steps != null) {
            for (int i = 0; i < steps.size(); i++) {
                if (!steps.get(i).hasRollbackPayload()) {
                    errors.add(new ValidationError(
                            String.format("Template '%s', step %d: missing required 'rollback' payload (rollbackPayloadRequired=true)", getSource(), i + 1),
                            getId(), "change"));
                }
            }
        }
        return errors;
    }
}
