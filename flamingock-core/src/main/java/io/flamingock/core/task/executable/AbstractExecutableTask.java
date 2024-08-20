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

import io.flamingock.core.task.descriptor.TaskDescriptor;

import java.util.Objects;

public abstract class AbstractExecutableTask<DESCRIPTOR extends TaskDescriptor> implements ExecutableTask {

    private final String stageName;

    protected final DESCRIPTOR descriptor;

    protected final boolean alreadyExecuted;

    public AbstractExecutableTask(String stageName,
                                  DESCRIPTOR descriptor,
                                  boolean executionRequired) {
        if (descriptor == null) {
            throw new IllegalArgumentException("task descriptor cannot be null");
        }
        this.stageName = stageName;
        this.descriptor = descriptor;
        this.alreadyExecuted = !executionRequired;
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
    public boolean isAlreadyExecuted() {
        return alreadyExecuted;
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
        return "ReflectionExecutableTask{" +
                ", id='" + descriptor + '\'' +
                ", state=" + alreadyExecuted +
                "} ";
    }
}
