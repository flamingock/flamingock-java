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
import io.flamingock.internal.common.core.error.validation.ValidationError;
import io.flamingock.internal.common.core.task.RecoveryDescriptor;
import io.flamingock.internal.common.core.task.TargetSystemDescriptor;

import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.List;

/**
 * Loaded change for simple templates (single apply/rollback step).
 * Used for templates annotated with {@code @ChangeTemplate(steppable = false)} or without annotation.
 * <p>
 * The payloads are converted from raw YAML data (Object/Map) to typed values
 * at load time, enabling early validation and cleaner executable tasks.
 *
 * @param <CONFIG>   the configuration type for the template
 * @param <APPLY>    the apply payload type
 * @param <ROLLBACK> the rollback payload type
 */
public class SimpleTemplateLoadedChange<CONFIG, APPLY, ROLLBACK>
        extends AbstractTemplateLoadedChange<CONFIG, APPLY, ROLLBACK> {

    // Already converted to typed payload (no longer raw Object from YAML)
    private final APPLY applyPayload;
    private final ROLLBACK rollbackPayload;

    SimpleTemplateLoadedChange(String changeFileName,
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
                               APPLY applyPayload,
                               ROLLBACK rollbackPayload,
                               TargetSystemDescriptor targetSystem,
                               RecoveryDescriptor recovery,
                               boolean rollbackPayloadRequired) {
        super(changeFileName, id, order, author, templateClass, constructor, profiles, transactional, runAlways, systemTask, configuration, targetSystem, recovery, rollbackPayloadRequired);
        this.applyPayload = applyPayload;
        this.rollbackPayload = rollbackPayload;
    }

    public APPLY getApplyPayload() {
        return applyPayload;
    }

    public ROLLBACK getRollbackPayload() {
        return rollbackPayload;
    }

    public boolean hasRollbackPayload() {
        return rollbackPayload != null;
    }

    @Override
    protected List<ValidationError> validateConfigurationPayload() {
        return Collections.emptyList();
    }

    @Override
    protected List<ValidationError> validateApplyPayload() {
        if (applyPayload == null) {
            return Collections.singletonList(new ValidationError(
                    String.format("Template '%s' requires 'apply' payload", getSource()),
                    getId(), "change"));
        }
        return Collections.emptyList();
    }

    @Override
    protected List<ValidationError> validateRollbackPayload() {
        if (rollbackPayloadRequired && rollbackPayload == null) {
            return Collections.singletonList(new ValidationError(
                    String.format("Template '%s' requires 'rollback' payload (rollbackPayloadRequired=true)", getSource()),
                    getId(), "change"));
        }
        return Collections.emptyList();
    }
}
