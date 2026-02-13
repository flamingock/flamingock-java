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

import io.flamingock.internal.common.core.error.ChangeExecutionException;
import io.flamingock.internal.common.core.recovery.action.ChangeAction;
import io.flamingock.internal.core.runtime.ExecutionRuntime;
import io.flamingock.internal.core.task.loaded.AbstractReflectionLoadedTask;

import java.lang.reflect.Method;
import java.util.List;

/**
 * This class is a reflection version of the ExecutableTask.
 * <p>
 * It creates a new instance on demand in every execution(apply and rollback), because it's intended to be applied
 * just once. The only case it will be potentially applied twice is if it fails, and in that case will only happen
 * once(in case of sequential execution) or very few times(in case or parallel execution and happen to fail multiple
 * concurrent tasks at the same time),because after that the process will abort.
 * <p>
 * For this reason it's more optimal to do it on demand, that articulate some synchronisation mechanism.
 * <p>
 * However, the methods are extracted in advance, so we can spot wrong configuration before starting the process and
 * fail fast.
 */
public class CodeExecutableTask<REFLECTION_TASK_DESCRIPTOR extends AbstractReflectionLoadedTask>
        extends ReflectionExecutableTask<REFLECTION_TASK_DESCRIPTOR> {


    public CodeExecutableTask(String stageName,
                              REFLECTION_TASK_DESCRIPTOR descriptor,
                              ChangeAction action,
                              Method executionMethod,
                              Method rollbackMethod) {
        super(stageName, descriptor, action, executionMethod, rollbackMethod);
    }



    @Override
    public void apply(ExecutionRuntime executionRuntime) {
        executeInternal(executionRuntime, executionMethod);
    }

    @Override
    public void rollback(ExecutionRuntime executionRuntime) {
        executeInternal(executionRuntime, rollbackMethod);
    }

    protected void executeInternal(ExecutionRuntime executionRuntime, Method method ) {
        Object instance = executionRuntime.getInstance(descriptor.getConstructor());
        try {
            executionRuntime.executeMethodWithInjectedDependencies(instance, method);
        } catch (Throwable ex) {
            throw new ChangeExecutionException(this.getId(), ex.getMessage(), ex);
        }
    }


}
