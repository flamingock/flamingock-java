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

import io.flamingock.internal.common.core.change.RecoveryDescriptor;
import io.flamingock.internal.common.core.change.TargetSystemDescriptor;
import io.flamingock.internal.common.core.recovery.action.ChangeAction;
import io.flamingock.internal.core.change.loaded.AbstractLoadedChange;

import java.util.Objects;
import java.util.Optional;

public abstract class AbstractExecutableChange<LOADED_CHANGE extends AbstractLoadedChange> implements ExecutableChange {

    private final String stageName;

    protected final LOADED_CHANGE loadedChange;

    protected final ChangeAction action;

    public AbstractExecutableChange(String stageName,
                                  LOADED_CHANGE loadedChange,
                                  ChangeAction action) {
        if (loadedChange == null) {
            throw new IllegalArgumentException("loaded change cannot be null");
        }
        if (action == null) {
            throw new IllegalArgumentException("change action cannot be null");
        }
        this.stageName = stageName;
        this.loadedChange = loadedChange;
        this.action = action;
    }
    @Override
    public String getId() {
        return loadedChange.getId();
    }

    @Override
    public String getAuthor() {
        return loadedChange.getAuthor();
    }

    @Override
    public boolean isRunAlways() {
        return loadedChange.isRunAlways();
    }

    @Override
    public boolean isTransactional() {
        return loadedChange.isTransactional();
    }

    @Override
    public Optional<Boolean> getTransactionalFlag() {
        return loadedChange.getTransactionalFlag();
    }

    @Override
    public boolean isSystem() {
        return loadedChange.isSystem();
    }

    @Override
    public boolean isLegacy() {
        return loadedChange.isLegacy();
    }

    @Override
    public String getSource() {
        return loadedChange.getSource();
    }

    @Override
    public String getSourceFile() {
        return loadedChange.getSourceFile();
    }

    @Override
    public Optional<String> getOrder() {
        return loadedChange.getOrder();
    }

    @Override
    public String getStageName() {
        return stageName;
    }

    @Override
    public LOADED_CHANGE getLoadedChange() {
        return loadedChange;
    }

    @Override
    public TargetSystemDescriptor getTargetSystem() {
        return loadedChange.getTargetSystem();
    }

    @Override
    public RecoveryDescriptor getRecovery() {
        return loadedChange.getRecovery();
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
        if (!(o instanceof AbstractExecutableChange)) return false;
        AbstractExecutableChange<?> that = (AbstractExecutableChange<?>) o;
        return loadedChange.equals(that.loadedChange);
    }

    @Override
    public int hashCode() {
        return Objects.hash(loadedChange);
    }

    @Override
    public String toString() {
        return "ExecutableChange{" +
                "id='" + loadedChange.getId() + '\'' +
                ", action=" + action +
                ", targetSystem='" + (getTargetSystem() != null ? getTargetSystem().getId() : null) + '\'' +
                ", recovery='" + (getRecovery() != null ? getRecovery().getStrategy() : null) + '\'' +
                "} ";
    }
}
