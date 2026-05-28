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

import io.flamingock.internal.core.runtime.ExecutionRuntime;
import io.flamingock.internal.common.core.change.ChangeDescriptor;
import io.flamingock.internal.common.core.recovery.action.ChangeAction;

public interface ExecutableChange extends ChangeDescriptor {

    boolean isTransactional();

    ChangeDescriptor getLoadedChange();

    String getStageName();

    void apply(ExecutionRuntime executionRuntime);

    String getApplyMethodName();

    void rollback(ExecutionRuntime executionRuntime);

    /**
     * Returns {@code true} when this change declares a rollback (annotated method on a code-based
     * change, or {@code @Rollback}-bearing template). Callers must gate any invocation of
     * {@link #rollback(ExecutionRuntime)} on this — invoking rollback when no rollback method is
     * declared is an invariant violation. Every implementation must answer it explicitly so the
     * decision is never left to a silent default.
     */
    boolean hasRollback();

    String getRollbackMethodName();

    boolean isAlreadyApplied();

    ChangeAction getAction();

}