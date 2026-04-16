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
package io.flamingock.internal.core.change.executable.builder;

import io.flamingock.internal.common.core.recovery.action.ChangeAction;
import io.flamingock.internal.core.change.executable.ExecutableChange;
import io.flamingock.internal.core.change.loaded.AbstractLoadedChange;
import io.flamingock.internal.core.change.loaded.AbstractTemplateLoadedChange;
import io.flamingock.internal.core.change.loaded.CodeLoadedChange;

public interface ExecutableChangeBuilder<LOADED_CHANGE extends AbstractLoadedChange> {

    /**
     * Builds executable changes based on a ChangeAction - the new action-based approach.
     * 
     * @param loadedChange the loaded change to build executable changes from
     * @param stageName the name of the stage containing the change
     * @param action the change action to apply to the change
     * @return executable change
     */
    static ExecutableChange build(AbstractLoadedChange loadedChange, String stageName, ChangeAction action) {
        return getInstance(loadedChange)
                .setStageName(stageName)
                .setChangeAction(action)
                .build();
    }


    static ExecutableChangeBuilder<?> getInstance(AbstractLoadedChange loadedChange) {

        if(TemplateExecutableChangeBuilder.supports(loadedChange)) {
            TemplateExecutableChangeBuilder templateBuilder = TemplateExecutableChangeBuilder.getInstance();
            AbstractTemplateLoadedChange<?, ?, ?> castedChange = templateBuilder.cast(loadedChange);
            return templateBuilder.setLoadedChange(castedChange);

        } else if(CodeExecutableChangeBuilder.supports(loadedChange)) {
            CodeExecutableChangeBuilder codeBuilder = CodeExecutableChangeBuilder.getInstance();
            CodeLoadedChange castedChange = codeBuilder.cast(loadedChange);
            return codeBuilder.setLoadedChange(castedChange);

        } else {
            throw new IllegalArgumentException(String.format("ExecutableChange type not recognised[%s]", loadedChange.getClass().getName()));

        }
    }


    LOADED_CHANGE cast(AbstractLoadedChange loadedChange);

    ExecutableChangeBuilder<?> setLoadedChange(LOADED_CHANGE change);

    ExecutableChangeBuilder<?> setStageName(String stageName);

    /**
     * Sets the change action that determines how the change should be handled.
     * This is the new action-based approach.
     * 
     * @param action the change action to set
     * @return this builder instance for method chaining
     */
    ExecutableChangeBuilder<?> setChangeAction(ChangeAction action);

    ExecutableChange build();
}
