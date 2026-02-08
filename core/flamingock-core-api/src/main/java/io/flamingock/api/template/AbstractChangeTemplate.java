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
 * Abstract base class for change templates providing common functionality.
 *
 * <p>This class handles generic type resolution and provides the common fields
 * needed by all templates: changeId, isTransactional, and configuration.
 *
 * <p>For new templates, extend one of the specialized abstract classes:
 * <ul>
 *   <li>{@link AbstractSimpleTemplate} - for templates with a single apply/rollback step</li>
 *   <li>{@link AbstractSteppableTemplate} - for templates with multiple steps</li>
 * </ul>
 */
public abstract class AbstractChangeTemplate<SHARED_CONFIGURATION_FIELD, APPLY_FIELD, ROLLBACK_FIELD> implements ChangeTemplate<SHARED_CONFIGURATION_FIELD, APPLY_FIELD, ROLLBACK_FIELD> {

    private final Class<SHARED_CONFIGURATION_FIELD> configurationClass;
    private final Class<APPLY_FIELD> applyPayloadClass;
    private final Class<ROLLBACK_FIELD> rollbackPayloadClass;
    protected String changeId;
    protected boolean isTransactional;
    protected SHARED_CONFIGURATION_FIELD configuration;

    private final Set<Class<?>> reflectiveClasses;


    @SuppressWarnings("unchecked")
    public AbstractChangeTemplate(Class<?>... additionalReflectiveClass) {
        reflectiveClasses = new HashSet<>(Arrays.asList(additionalReflectiveClass));

        try {
            Class<?>[] typeArgs = ReflectionUtil.resolveTypeArgumentsAsClasses(this.getClass(), AbstractChangeTemplate.class);

            if (typeArgs.length < 3) {
                throw new IllegalStateException("Expected 3 generic type arguments for a Template, but found " + typeArgs.length);
            }

            this.configurationClass = (Class<SHARED_CONFIGURATION_FIELD>) typeArgs[0];
            this.applyPayloadClass = (Class<APPLY_FIELD>) typeArgs[1];
            this.rollbackPayloadClass = (Class<ROLLBACK_FIELD>) typeArgs[2];

            reflectiveClasses.add(configurationClass);
            reflectiveClasses.add(applyPayloadClass);
            reflectiveClasses.add(rollbackPayloadClass);
            reflectiveClasses.add(TemplateStep.class);
        } catch (ClassCastException e) {
            throw new IllegalStateException("Generic type arguments for a Template must be concrete types (classes, interfaces, or primitive wrappers like String, Integer, etc.): " + e.getMessage(), e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize template: " + e.getMessage(), e);
        }
    }

    @Override
    public final Collection<Class<?>> getReflectiveClasses() {
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
