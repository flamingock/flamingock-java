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

package io.flamingock.core.task.executable;

import io.flamingock.core.api.annotations.BeforeExecution;
import io.flamingock.core.api.annotations.Execution;
import io.flamingock.core.api.annotations.RollbackBeforeExecution;
import io.flamingock.core.api.annotations.RollbackExecution;
import io.flamingock.core.engine.audit.writer.AuditEntryStatus;
import io.flamingock.core.task.descriptor.ReflectionTaskDescriptor;
import io.flamingock.core.task.descriptor.TaskDescriptor;
import io.flamingock.core.util.ReflectionUtil;
import io.flamingock.core.util.StringUtil;

import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;


/**
 * Factory for ChangeUnit classes
 */
public class ChangeUnitFactory implements ExecutableTaskFactory {

    @Override
    public List<ReflectionExecutableTask<ReflectionTaskDescriptor>> extractTasks(String stageName, TaskDescriptor descriptor, AuditEntryStatus initialState) {
        //It assumes "matchesDescriptor" was previously called for this descriptor.
        if (ReflectionTaskDescriptor.class.equals(descriptor.getClass())) {
            return getTasksFromReflection(stageName, (ReflectionTaskDescriptor) descriptor, initialState);
        }

        throw new IllegalArgumentException(String.format("%s not able to process: %s", this.getClass().getSimpleName(), descriptor.getClass().getSimpleName()));

    }

    private List<ReflectionExecutableTask<ReflectionTaskDescriptor>> getTasksFromReflection(String stageName,
                                                                                            ReflectionTaskDescriptor taskDescriptor,
                                                                                            AuditEntryStatus initialState) {

        Method executionMethod = ReflectionUtil.findFirstMethodAnnotated(taskDescriptor.getSourceClass(), Execution.class)
                .orElseThrow(() -> new IllegalArgumentException(String.format(
                        "ExecutableChangeUnit[%s] without %s method",
                        taskDescriptor.getSourceClass().getName(),
                        Execution.class.getSimpleName())));

        Optional<Method> rollbackMethodOpt = ReflectionUtil.findFirstMethodAnnotated(taskDescriptor.getSourceClass(), RollbackExecution.class);
        ReflectionExecutableTask<ReflectionTaskDescriptor> task = new ReflectionExecutableTask<>(
                stageName,
                taskDescriptor,
                AuditEntryStatus.isRequiredExecution(initialState),
                executionMethod,
                rollbackMethodOpt.orElse(null));


            /*
            If there is any rollback dependency(@BeforeExecution in ChangeUnits, legacy ChangeSet, etc.) they are added as normal task
            before the main task and, if it also provides @BeforeExecutionRollback, it's also added to the main task's rollbackChain,
            so they  are rolled back in case the main task fails.
             */
        List<ReflectionExecutableTask<ReflectionTaskDescriptor>> tasks = new LinkedList<>();
        getBeforeExecutionOptional(stageName, task, initialState).ifPresent(beforeExecutionTask -> {
            tasks.add(beforeExecutionTask);
            beforeExecutionTask.getRollbackChain().forEach(task::addRollback);
        });
        tasks.add(task);
        return tasks;
    }


    private Optional<ReflectionExecutableTask<ReflectionTaskDescriptor>> getBeforeExecutionOptional(String stageName,
                                                                                                    ReflectionExecutableTask<ReflectionTaskDescriptor> baseTask,
                                                                                                    AuditEntryStatus initialState) {
        //Creates a new TaskDescriptor, based on the main one, but with the "beforeExecution id, also based on the main one"
        ReflectionTaskDescriptor taskDescriptor = new ReflectionTaskDescriptor(
                StringUtil.getBeforeExecutionId(baseTask.getDescriptor().getId()),
                baseTask.getDescriptor().getOrder().orElse(null),
                baseTask.getDescriptor().getSourceClass(),
                baseTask.getDescriptor().isRunAlways(),
                false//A beforeExecution task will never be transactional
        );

        Optional<Method> beforeExecutionMethodOptional = ReflectionUtil.findFirstMethodAnnotated(taskDescriptor.getSourceClass(), BeforeExecution.class);
        if (!beforeExecutionMethodOptional.isPresent()) {
            return Optional.empty();
        }
        Method beforeExecutionMethod = beforeExecutionMethodOptional.get();
        Optional<Method> rollbackBeforeExecution = ReflectionUtil.findFirstMethodAnnotated(taskDescriptor.getSourceClass(), RollbackBeforeExecution.class);


        return Optional.of(new ReflectionExecutableTask<>(
                stageName,
                taskDescriptor,
                AuditEntryStatus.isRequiredExecution(initialState),
                beforeExecutionMethod,
                rollbackBeforeExecution.orElse(null)));

    }

}