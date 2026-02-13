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
package io.flamingock.internal.core.task.executable.builder;

import io.flamingock.internal.common.core.recovery.action.ChangeAction;
import io.flamingock.internal.core.task.executable.CodeExecutableTask;
import io.flamingock.internal.core.task.executable.ExecutableTask;
import io.flamingock.internal.core.task.executable.ReflectionExecutableTask;
import io.flamingock.internal.core.task.loaded.AbstractLoadedTask;
import io.flamingock.internal.core.task.loaded.AbstractReflectionLoadedTask;
import io.flamingock.internal.core.task.loaded.CodeLoadedChange;

import java.lang.reflect.Method;
import java.util.Optional;


/**
 * Factory for Change classes
 */
public class CodeExecutableTaskBuilder implements ExecutableTaskBuilder<CodeLoadedChange> {
    private static final CodeExecutableTaskBuilder instance = new CodeExecutableTaskBuilder();

    private String stageName;
    private ChangeAction changeAction;
    private CodeLoadedChange loadedTask;

    static CodeExecutableTaskBuilder getInstance() {
        return instance;
    }

    public static boolean supports(AbstractLoadedTask loadedTask) {
        return CodeLoadedChange.class.isAssignableFrom(loadedTask.getClass());
    }


    @Override
    public CodeLoadedChange cast(AbstractLoadedTask loadedTask) {
        return (CodeLoadedChange) loadedTask;
    }

    @Override
    public CodeExecutableTaskBuilder setLoadedTask(CodeLoadedChange loadedTask) {
        this.loadedTask = loadedTask;
        return this;
    }

    @Override
    public CodeExecutableTaskBuilder setStageName(String stageName) {
        this.stageName = stageName;
        return this;
    }

    @Override
    public CodeExecutableTaskBuilder setChangeAction(ChangeAction action) {
        this.changeAction = action;
        return this;
    }

    @Override
    public ExecutableTask build() {
        return getTasksFromReflection(stageName, loadedTask, changeAction);
    }

    /**
     * New ChangeAction-based method for building tasks.
     */
    private ReflectionExecutableTask<AbstractReflectionLoadedTask> getTasksFromReflection(String stageName,
                                                                                          CodeLoadedChange loadedTask,
                                                                                          ChangeAction action) {
        return buildTasksInternal(stageName, loadedTask, action);
    }

    private ReflectionExecutableTask<AbstractReflectionLoadedTask> buildTasksInternal(String stageName,
                                                                                      CodeLoadedChange loadedTask,
                                                                                      ChangeAction action) {
        Method executionMethod = loadedTask.getApplyMethod();
        Optional<Method> rollbackMethodOpt = loadedTask.getRollbackMethod();

        return new CodeExecutableTask<>(
                stageName,
                loadedTask,
                action,
                executionMethod,
                rollbackMethodOpt.orElse(null));
    }
}