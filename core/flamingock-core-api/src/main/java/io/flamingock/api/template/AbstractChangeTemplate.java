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


public abstract class AbstractChangeTemplate<CONFIGURATION, EXECUTION, ROLLBACK> implements ChangeTemplate<CONFIGURATION, EXECUTION, ROLLBACK> {

    private final Class<CONFIGURATION> configurationClass;
    private final Class<EXECUTION> executionClass;
    private final Class<ROLLBACK> rollbackClass;
    protected String changeId;
    protected boolean isTransactional;
    protected CONFIGURATION configuration;
    protected EXECUTION execution;
    protected ROLLBACK rollback;


    private final Set<Class<?>> reflectiveClasses;


    @SuppressWarnings("unchecked")
    public AbstractChangeTemplate(Class<?>... additionalReflectiveClass) {
        reflectiveClasses = new HashSet<>(Arrays.asList(additionalReflectiveClass));

        try {
            Class<?>[] typeArgs = ReflectionUtil.getActualTypeArguments(this.getClass());

            if (typeArgs.length < 3) {
                throw new IllegalStateException("Expected 3 generic type arguments for a Template, but found " + typeArgs.length);
            }

            this.configurationClass = (Class<CONFIGURATION>) typeArgs[0];
            this.executionClass = (Class<EXECUTION>) typeArgs[1];
            this.rollbackClass = (Class<ROLLBACK>) typeArgs[2];

            reflectiveClasses.add(configurationClass);
            reflectiveClasses.add(executionClass);
            reflectiveClasses.add(rollbackClass);
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
    public void setConfiguration(CONFIGURATION configuration) {
        this.configuration = configuration;
    }

    @Override
    public void setExecution(EXECUTION execution) {
        this.execution = execution;
    }

    @Override
    public void setRollback(ROLLBACK rollback) {
        this.rollback = rollback;
    }

    @Override
    public Class<CONFIGURATION> getConfigurationClass() {
        return configurationClass;
    }

    @Override
    public Class<EXECUTION> getExecutionClass() {
        return executionClass;
    }

    @Override
    public Class<ROLLBACK> getRollbackClass() {
        return rollbackClass;
    }

}
