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

import io.flamingock.internal.common.core.task.RecoveryDescriptor;
import io.flamingock.internal.common.core.task.TaskDescriptor;
import io.flamingock.internal.common.core.task.TargetSystemDescriptor;
import io.flamingock.internal.common.core.recovery.action.ChangeAction;

import java.util.Objects;
import java.util.Optional;

public abstract class AbstractExecutableTask<DESCRIPTOR extends TaskDescriptor> implements ExecutableTask {

    private final String stageName;

    protected final DESCRIPTOR descriptor;

    protected final ChangeAction action;

    public AbstractExecutableTask(String stageName,
                                  DESCRIPTOR descriptor,
                                  ChangeAction action) {
        if (descriptor == null) {
            throw new IllegalArgumentException("task descriptor cannot be null");
        }
        if (action == null) {
            throw new IllegalArgumentException("change action cannot be null");
        }
        this.stageName = stageName;
        this.descriptor = descriptor;
        this.action = action;
    }
    @Override
    public String getId() {
        return descriptor.getId();
    }

    @Override
    public String getAuthor() {
        return descriptor.getAuthor();
    }

    @Override
    public boolean isRunAlways() {
        return descriptor.isRunAlways();
    }

    @Override
    public boolean isTransactional() {
        return descriptor.isTransactional();
    }

    @Override
    public boolean isSystem() {
        return descriptor.isSystem();
    }

    @Override
    public boolean isLegacy() {
        return descriptor.isLegacy();
    }

    @Override
    public String getSource() {
        return descriptor.getSource();
    }

    @Override
    public Optional<String> getOrder() {
        return descriptor.getOrder();
    }

    @Override
    public String getStageName() {
        return stageName;
    }

    @Override
    public DESCRIPTOR getDescriptor() {
        return descriptor;
    }

    @Override
    public TargetSystemDescriptor getTargetSystem() {
        return descriptor.getTargetSystem();
    }

    @Override
    public RecoveryDescriptor getRecovery() {
        return descriptor.getRecovery();
    }

    @Override
    public boolean isAlreadyApplied() {
        return action == ChangeAction.SKIP;
    }

    @Override
    public ChangeAction getAction() {
        return action;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AbstractExecutableTask)) return false;
        AbstractExecutableTask<?> that = (AbstractExecutableTask<?>) o;
        return descriptor.equals(that.descriptor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(descriptor);
    }

    @Override
    public String toString() {
        return "ExecutableTask{" +
                "id='" + descriptor.getId() + '\'' +
                ", action=" + action +
                ", targetSystem='" + (getTargetSystem() != null ? getTargetSystem().getId() : null) + '\'' +
                ", recovery='" + (getRecovery() != null ? getRecovery().getStrategy() : null) + '\'' +
                "} ";
    }
}
