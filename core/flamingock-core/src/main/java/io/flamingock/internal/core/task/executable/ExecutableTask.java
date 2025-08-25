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

import io.flamingock.internal.core.engine.audit.recovery.RecoveryIssue;
import io.flamingock.internal.core.runtime.ExecutionRuntime;
import io.flamingock.internal.common.core.task.TaskDescriptor;
import io.flamingock.internal.core.pipeline.actions.ChangeAction;

import java.util.List;

public interface ExecutableTask extends TaskDescriptor {

    TaskDescriptor getDescriptor();

    String getStageName();

    void execute(ExecutionRuntime runtimeHelper);

    String getExecutionMethodName();

    boolean isAlreadyExecuted();

    ChangeAction getAction();

    void addRollback(Rollback rollback);

    List<? extends Rollback> getRollbackChain();

}