/*
 * Copyright 2023 Flamingock (https://oss.flamingock.io)
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

import io.flamingock.core.audit.AuditEntry;
import io.flamingock.internal.core.task.executable.ReflectionExecutableTask;
import io.flamingock.internal.core.task.loaded.AbstractLoadedTask;
import io.flamingock.internal.core.task.loaded.LoadedTaskBuilder;
import io.flamingock.internal.core.task.loaded.CodeLoadedChangeUnit;
import io.flamingock.internal.core.task.loaded.AbstractReflectionLoadedTask;

import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;


/**
 * Factory for ChangeUnit classes
 */
public class CodeExecutableTaskBuilder implements ExecutableTaskBuilder<CodeLoadedChangeUnit> {
    private static final CodeExecutableTaskBuilder instance = new CodeExecutableTaskBuilder();

    private String stageName;
    private AuditEntry.Status initialState;
    private CodeLoadedChangeUnit loadedTask;

    static CodeExecutableTaskBuilder getInstance() {
        return instance;
    }

    public static boolean supports(AbstractLoadedTask loadedTask) {
        return CodeLoadedChangeUnit.class.isAssignableFrom(loadedTask.getClass());
    }


    @Override
    public CodeLoadedChangeUnit cast(AbstractLoadedTask loadedTask) {
        return (CodeLoadedChangeUnit)loadedTask;
    }

    @Override
    public CodeExecutableTaskBuilder setLoadedTask(CodeLoadedChangeUnit loadedTask) {
        this.loadedTask = loadedTask;
        return this;
    }

    @Override
    public CodeExecutableTaskBuilder setStageName(String stageName) {
        this.stageName = stageName;
        return this;
    }

    @Override
    public CodeExecutableTaskBuilder setInitialState(AuditEntry.Status initialState) {
        this.initialState = initialState;
        return this;
    }

    @Override
    public List<ReflectionExecutableTask<AbstractReflectionLoadedTask>> build() {
        return getTasksFromReflection(stageName, loadedTask, initialState);
    }

    private List<ReflectionExecutableTask<AbstractReflectionLoadedTask>> getTasksFromReflection(String stageName,
                                                                                                CodeLoadedChangeUnit loadedTask,
                                                                                                AuditEntry.Status initialState) {

        Method executionMethod = loadedTask.getExecutionMethod();

        Optional<Method> rollbackMethodOpt = loadedTask.getRollbackMethod();
        ReflectionExecutableTask<AbstractReflectionLoadedTask> task = new ReflectionExecutableTask<>(
                stageName,
                loadedTask,
                AuditEntry.Status.isRequiredExecution(initialState),
                executionMethod,
                rollbackMethodOpt.orElse(null));


            /*
            If there is any rollback dependency(@BeforeExecution in ChangeUnits, legacy ChangeSet, etc.) they are added as normal task
            before the main task and, if it also provides @BeforeExecutionRollback, it's also added to the main task's rollbackChain,
            so they  are rolled back in case the main task fails.
             */
        List<ReflectionExecutableTask<AbstractReflectionLoadedTask>> tasks = new LinkedList<>();
        getBeforeExecutionOptional(stageName, task, initialState).ifPresent(beforeExecutionTask -> {
            tasks.add(beforeExecutionTask);
            beforeExecutionTask.getRollbackChain().forEach(task::addRollback);
        });
        tasks.add(task);
        return tasks;
    }

    private Optional<ReflectionExecutableTask<AbstractReflectionLoadedTask>> getBeforeExecutionOptional(String stageName,
                                                                                                        ReflectionExecutableTask<AbstractReflectionLoadedTask> baseTask,
                                                                                                        AuditEntry.Status initialState) {
        //Creates a new LoadedTask, based on the main one, but with the "beforeExecution id, also based on the main one"
        CodeLoadedChangeUnit loadedTask = LoadedTaskBuilder
                .getCodeBuilderInstance(baseTask.getDescriptor().getSourceClass())
                .setBeforeExecution(true)
                .setTransactional(false)
                .setSystem(baseTask.getDescriptor().isSystem())
                .build();
        Optional<Method> beforeExecutionMethodOptional = loadedTask.getBeforeExecutionMethod();
        if (!beforeExecutionMethodOptional.isPresent()) {
            return Optional.empty();
        }
        Method beforeExecutionMethod = beforeExecutionMethodOptional.get();
        Optional<Method> rollbackBeforeExecution = loadedTask.getRollbackBeforeExecutionMethod();

        return Optional.of(new ReflectionExecutableTask<>(
                stageName,
                loadedTask,
                AuditEntry.Status.isRequiredExecution(initialState),
                beforeExecutionMethod,
                rollbackBeforeExecution.orElse(null)));

    }


}