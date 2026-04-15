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
package io.flamingock.internal.core.change.executable;

import io.flamingock.api.template.ChangeTemplate;
import io.flamingock.api.template.TemplateField;
import io.flamingock.api.template.TemplatePayload;
import io.flamingock.api.template.wrappers.TemplateVoid;
import io.flamingock.internal.common.core.recovery.action.ChangeAction;
import io.flamingock.internal.core.change.loaded.AbstractTemplateLoadedChange;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Abstract base class for template executable changes.
 * Contains common logic for executing templates, with type-specific data setting
 * delegated to subclasses.
 *
 * @param <CONFIG>   the configuration type for the template
 * @param <APPLY>    the apply payload type
 * @param <ROLLBACK> the rollback payload type
 * @param <LOADED_CHANGE>        the type of template loaded change
 */
public abstract class AbstractTemplateExecutableChange<CONFIG extends TemplateField, APPLY extends TemplatePayload, ROLLBACK extends TemplatePayload,
        LOADED_CHANGE extends AbstractTemplateLoadedChange<CONFIG, APPLY, ROLLBACK>> extends ReflectionExecutableChange<LOADED_CHANGE> {
    protected final Logger logger = FlamingockLoggerFactory.getLogger("TemplateExecutableChange");

    public AbstractTemplateExecutableChange(String stageName,
                                            LOADED_CHANGE loadedChange,
                                            ChangeAction action,
                                            Method executionMethod,
                                            Method rollbackMethod) {
        super(stageName, loadedChange, action, executionMethod, rollbackMethod);
    }

    protected void setConfigurationData(ChangeTemplate<CONFIG, ?, ?> instance) {
        Class<CONFIG> parameterClass = instance.getConfigurationClass();
        CONFIG data = loadedChange.getConfigurationPayload();

        if (data != null && TemplateVoid.class != parameterClass) {
            instance.setConfiguration(data);
        } else if (TemplateVoid.class != parameterClass) {
            logger.warn("No 'Configuration' section provided for template-based change[{}] of type[{}]",
                    loadedChange.getId(), loadedChange.getTemplateClass().getName());
        }
    }

    protected Method getSetterMethod(Class<?> changeTemplateClass, String methodName) {
        return Arrays.stream(changeTemplateClass.getMethods())
                .filter(m -> methodName.equals(m.getName()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Not found config setter for template: " + changeTemplateClass.getSimpleName()));
    }


}
