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
import io.flamingock.internal.core.change.executable.CodeExecutableChange;
import io.flamingock.internal.core.change.executable.ExecutableChange;
import io.flamingock.internal.core.change.executable.ReflectionExecutableChange;
import io.flamingock.internal.core.change.loaded.AbstractLoadedChange;
import io.flamingock.internal.core.change.loaded.AbstractReflectionLoadedChange;
import io.flamingock.internal.core.change.loaded.CodeLoadedChange;

import java.lang.reflect.Method;
import java.util.Optional;


/**
 * Factory for Change classes
 */
public class CodeExecutableChangeBuilder implements ExecutableChangeBuilder<CodeLoadedChange> {
    private static final CodeExecutableChangeBuilder instance = new CodeExecutableChangeBuilder();

    private String stageName;
    private ChangeAction changeAction;
    private CodeLoadedChange loadedChange;

    static CodeExecutableChangeBuilder getInstance() {
        return instance;
    }

    public static boolean supports(AbstractLoadedChange loadedChange) {
        return CodeLoadedChange.class.isAssignableFrom(loadedChange.getClass());
    }


    @Override
    public CodeLoadedChange cast(AbstractLoadedChange loadedChange) {
        return (CodeLoadedChange) loadedChange;
    }

    @Override
    public CodeExecutableChangeBuilder setLoadedChange(CodeLoadedChange loadedChange) {
        this.loadedChange = loadedChange;
        return this;
    }

    @Override
    public CodeExecutableChangeBuilder setStageName(String stageName) {
        this.stageName = stageName;
        return this;
    }

    @Override
    public CodeExecutableChangeBuilder setChangeAction(ChangeAction action) {
        this.changeAction = action;
        return this;
    }

    @Override
    public ExecutableChange build() {
        return getChangesFromReflection(stageName, loadedChange, changeAction);
    }

    /**
     * New ChangeAction-based method for building changes.
     */
    private ReflectionExecutableChange<AbstractReflectionLoadedChange> getChangesFromReflection(String stageName,
                                                                                              CodeLoadedChange loadedChange,
                                                                                              ChangeAction action) {
        return buildChangesInternal(stageName, loadedChange, action);
    }

    private ReflectionExecutableChange<AbstractReflectionLoadedChange> buildChangesInternal(String stageName,
                                                                                          CodeLoadedChange loadedChange,
                                                                                          ChangeAction action) {
        Method executionMethod = loadedChange.getApplyMethod();
        Optional<Method> rollbackMethodOpt = loadedChange.getRollbackMethod();

        return new CodeExecutableChange<>(
                stageName,
                loadedChange,
                action,
                executionMethod,
                rollbackMethodOpt.orElse(null));
    }
}