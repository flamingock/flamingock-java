/*
 * Copyright 2025 Flamingock (https://www.flamingock.io)
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

import io.flamingock.internal.util.ReflectionUtil;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;


/**
 * Base class for creating Flamingock change templates.
 *
 * <p>Extend this class and annotate with {@link io.flamingock.api.annotations.ChangeTemplate}
 * to create custom templates. The annotation's {@code steppable} attribute determines the YAML structure.
 *
 * <p>Use {@code @ChangeTemplate} (default {@code steppable = false}) for simple templates with
 * single apply/rollback fields.
 *
 * <p>Use {@code @ChangeTemplate(steppable = true)} for steppable templates with multiple steps.
 *
 * @param <SHARED_CONFIGURATION_FIELD> shared configuration type (use {@code Void} if none)
 * @param <APPLY_FIELD> apply payload type
 * @param <ROLLBACK_FIELD> rollback payload type
 * @see io.flamingock.api.annotations.ChangeTemplate
 */
public abstract class AbstractChangeTemplate<SHARED_CONFIGURATION_FIELD, APPLY_FIELD extends TemplatePayload, ROLLBACK_FIELD extends TemplatePayload> implements ChangeTemplate<SHARED_CONFIGURATION_FIELD, APPLY_FIELD, ROLLBACK_FIELD> {

    private final Class<SHARED_CONFIGURATION_FIELD> configurationClass;
    private final Class<APPLY_FIELD> applyPayloadClass;
    private final Class<ROLLBACK_FIELD> rollbackPayloadClass;
    protected String changeId;
    protected boolean isTransactional;

    protected SHARED_CONFIGURATION_FIELD configuration;
    protected APPLY_FIELD applyPayload;
    protected ROLLBACK_FIELD rollbackPayload;

    private final Set<Class<?>> additionalReflectiveClasses;


    @SuppressWarnings("unchecked")
    public AbstractChangeTemplate(Class<?>... additionalReflectiveClass) {
        // Store additional classes - reflective classes set is built on-demand in getReflectiveClasses()
        this.additionalReflectiveClasses = new HashSet<>(Arrays.asList(additionalReflectiveClass));

        try {
            Class<?>[] typeArgs = ReflectionUtil.resolveTypeArgumentsAsClasses(this.getClass(), AbstractChangeTemplate.class);

            if (typeArgs.length < 3) {
                throw new IllegalStateException("Expected 3 generic type arguments for a Template, but found " + typeArgs.length);
            }

            this.configurationClass = (Class<SHARED_CONFIGURATION_FIELD>) typeArgs[0];
            this.applyPayloadClass = (Class<APPLY_FIELD>) typeArgs[1];
            this.rollbackPayloadClass = (Class<ROLLBACK_FIELD>) typeArgs[2];
        } catch (ClassCastException e) {
            throw new IllegalStateException("Generic type arguments for a Template must be concrete types (classes, interfaces, or primitive wrappers like String, Integer, etc.): " + e.getMessage(), e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize template: " + e.getMessage(), e);
        }
    }

    /**
     * Returns the collection of classes that need reflection registration for GraalVM native images.
     * <p>
     * This method builds the reflective classes set on-demand, including:
     * <ul>
     *   <li>The configuration class (generic type argument 0)</li>
     *   <li>The apply payload class (generic type argument 1)</li>
     *   <li>The rollback payload class (generic type argument 2)</li>
     *   <li>{@link TemplateStep} class</li>
     *   <li>Any additional classes passed to the constructor</li>
     * </ul>
     * <p>
     * This method is only called by GraalVM's {@code RegistrationFeature} at build-time,
     * so there is no performance concern from building the set on each call.
     *
     * @return collection of classes requiring reflection registration
     */
    @Override
    public final Collection<Class<?>> getReflectiveClasses() {
        Set<Class<?>> reflectiveClasses = new HashSet<>(additionalReflectiveClasses);
        reflectiveClasses.add(configurationClass);
        reflectiveClasses.add(applyPayloadClass);
        reflectiveClasses.add(rollbackPayloadClass);
        reflectiveClasses.add(TemplateStep.class);
        return reflectiveClasses;
    }

    @Override
    public void setChangeId(String changeId) {
        this.changeId = changeId;
    }

    @Override
    public void setTransactional(boolean isTransactional) {
        this.isTransactional = isTransactional;
    }

    @Override
    public void setConfiguration(SHARED_CONFIGURATION_FIELD configuration) {
        this.configuration = configuration;
    }

    @Override
    public void setApplyPayload(APPLY_FIELD applyPayload) {
        this.applyPayload = applyPayload;
    }

    @Override
    public void setRollbackPayload(ROLLBACK_FIELD rollbackPayload) {
        this.rollbackPayload = rollbackPayload;
    }

    @Override
    public Class<SHARED_CONFIGURATION_FIELD> getConfigurationClass() {
        return configurationClass;
    }

    @Override
    public Class<APPLY_FIELD> getApplyPayloadClass() {
        return applyPayloadClass;
    }

    @Override
    public Class<ROLLBACK_FIELD> getRollbackPayloadClass() {
        return rollbackPayloadClass;
    }

}
