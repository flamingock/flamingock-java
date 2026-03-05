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

import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.Rollback;
import io.flamingock.api.template.ChangeTemplate;
import io.flamingock.api.template.TemplatePayload;
import io.flamingock.api.template.TemplateValidationContext;
import io.flamingock.internal.common.core.error.validation.ValidationError;
import io.flamingock.internal.common.core.task.RecoveryDescriptor;
import io.flamingock.internal.common.core.task.TargetSystemDescriptor;
import io.flamingock.internal.core.pipeline.loaded.stage.StageValidationContext;
import io.flamingock.internal.util.ReflectionUtil;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Abstract base class for template-based loaded changes.
 * Contains common fields and methods shared by both SimpleTemplateLoadedChange
 * and SteppableTemplateLoadedChange.
 *
 * @param <CONFIG>   the configuration type for the template
 * @param <APPLY>    the apply payload type
 * @param <ROLLBACK> the rollback payload type
 */
public abstract class AbstractTemplateLoadedChange<CONFIG extends TemplatePayload, APPLY extends TemplatePayload, ROLLBACK extends TemplatePayload> extends AbstractLoadedChange {

    protected static final Logger logger = FlamingockLoggerFactory.getLogger(AbstractTemplateLoadedChange.class);

    private final List<String> profiles;
    private final CONFIG configurationPayload;
    protected final boolean rollbackPayloadRequired;

    protected AbstractTemplateLoadedChange(String changeFileName,
                                           String id,
                                           String order,
                                           String author,
                                           Class<? extends ChangeTemplate<CONFIG, APPLY, ROLLBACK>> templateClass,
                                           Constructor<?> constructor,
                                           List<String> profiles,
                                           boolean transactional,
                                           boolean runAlways,
                                           boolean systemTask,
                                           CONFIG configurationPayload,
                                           TargetSystemDescriptor targetSystem,
                                           RecoveryDescriptor recovery,
                                           boolean rollbackPayloadRequired) {
        super(changeFileName, id, order, author, templateClass, constructor, runAlways, transactional, systemTask, targetSystem, recovery, false);
        this.profiles = profiles;
        this.transactional = transactional;
        this.configurationPayload = configurationPayload;
        this.rollbackPayloadRequired = rollbackPayloadRequired;
    }


    public CONFIG getConfigurationPayload() {
        return configurationPayload;
    }

    public List<String> getProfiles() {
        return profiles;
    }

    @SuppressWarnings("unchecked")
    public Class<? extends ChangeTemplate<CONFIG, APPLY, ROLLBACK>> getTemplateClass() {
        return (Class<? extends ChangeTemplate<CONFIG, APPLY, ROLLBACK>>) this.getImplementationClass();
    }

    @Override
    public Method getApplyMethod() {
        return ReflectionUtil.findFirstAnnotatedMethod(getImplementationClass(), Apply.class)
                .orElseThrow(() -> new IllegalArgumentException(String.format(
                        "Templated[%s] without %s method",
                        getSource(),
                        Apply.class.getSimpleName())));
    }

    @Override
    public Optional<Method> getRollbackMethod() {
        return ReflectionUtil.findFirstAnnotatedMethod(getImplementationClass(), Rollback.class);
    }

    @Override
    public List<ValidationError> getValidationErrors(StageValidationContext context) {
        List<ValidationError> errors = super.getValidationErrors(context);
        errors.addAll(validateConfigurationPayload());
        errors.addAll(validateApplyPayload());
        errors.addAll(validateRollbackPayload());
        warnIfAllPayloadsSupportTransactions();
        return errors;
    }

    protected TemplateValidationContext buildValidationContext() {
        TemplateValidationContext ctx = new TemplateValidationContext();
        ctx.setTransactional(isTransactional());
        return ctx;
    }

    protected List<ValidationError> checkPayloadTransactionSupport(TemplatePayload payload, String payloadLabel) {
        if (payload == null) {
            return Collections.emptyList();
        }
        Optional<Boolean> supports = payload.getInfo().getSupportsTransactions();
        if (isTransactional() && supports.isPresent() && !supports.get()) {
            return Collections.singletonList(new ValidationError(
                    String.format("Template '%s' is transactional but %s payload does not support transactions",
                            getSource(), payloadLabel),
                    getId(), "change"));
        }
        return Collections.emptyList();
    }

    protected static Boolean getExplicitTransactionSupport(TemplatePayload payload) {
        if (payload == null) {
            return null;
        }
        return payload.getInfo().getSupportsTransactions().orElse(null);
    }

    abstract protected List<ValidationError> validateConfigurationPayload();

    abstract protected List<ValidationError> validateApplyPayload();

    abstract protected List<ValidationError> validateRollbackPayload();

    abstract protected void warnIfAllPayloadsSupportTransactions();
}
