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
package io.flamingock.internal.core.task.executable;

import io.flamingock.internal.core.runtime.ExecutionRuntime;
import io.flamingock.internal.core.runtime.MissingInjectedParameterException;
import io.flamingock.internal.core.task.loaded.AbstractReflectionLoadedTask;
import io.flamingock.internal.common.core.recovery.action.ChangeAction;

import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.List;

/**
 * This class is a reflection version of the ExecutableTask.
 * <p>
 * It creates a new instance on demand in every execution(execution and rollback), because it's intended to be applied
 * just once. The only case it will be potentially applied twice is if it fails, and in that case will only happen
 * once(in case of sequential execution) or very few times(in case or parallel execution and happen to fail multiple
 * concurrent tasks at the same time),because after that the process will abort.
 * <p>
 * For this reason it's more optimal to do it on demand, that articulate some synchronisation mechanism.
 * <p>
 * However, the methods are extracted in advance, so we can spot wrong configuration before starting the process and
 * fail fast.
 */
public class ReflectionExecutableTask<REFLECTION_TASK_DESCRIPTOR extends AbstractReflectionLoadedTask> extends AbstractExecutableTask<REFLECTION_TASK_DESCRIPTOR> implements ExecutableTask {

    protected final Method executionMethod;

    protected final List<Rollback> rollbackChain;


    public ReflectionExecutableTask(String stageName,
                                    REFLECTION_TASK_DESCRIPTOR descriptor,
                                    ChangeAction action,
                                    Method executionMethod,
                                    Method rollbackMethod) {
        super(stageName, descriptor, action);
        this.executionMethod = executionMethod;
        rollbackChain = new LinkedList<>();
        if(rollbackMethod != null) {
            rollbackChain.add(buildRollBack(rollbackMethod));
        }
    }

    @Override
    public void addRollback(Rollback rollback) {
        rollbackChain.add(rollback);

    }

    @Override
    public List<? extends Rollback> getRollbackChain() {
        return rollbackChain;
    }

    @Override
    public void execute(ExecutionRuntime executionRuntime) {
        executeInternal(executionRuntime, executionMethod);

    }

    protected void executeInternal(ExecutionRuntime executionRuntime, Method method ) {
        Object instance = executionRuntime.getInstance(descriptor.getConstructor());
        try {
            executionRuntime.executeMethodWithInjectedDependencies(instance, method);
        } catch (Throwable ex) {
            throw new ChangeExecutionException(this.getId(), ex);
        }
    }

    @Override
    public String getExecutionMethodName() {
        return executionMethod.getName();
    }

    private Rollback buildRollBack(Method rollbackMethod) {
        return new Rollback() {
            @Override
            public ExecutableTask getTask() {
                return ReflectionExecutableTask.this;
            }

            @Override
            public void rollback(ExecutionRuntime executionRuntime) {
                executeInternal(executionRuntime, rollbackMethod);
            }

            @Override
            public String getRollbackMethodName() {
                return rollbackMethod.getName();
            }
        };
    }

}
