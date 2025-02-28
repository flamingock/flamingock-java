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

package io.flamingock.core.task.descriptor;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.StringJoiner;

public abstract class ReflectionLoadedTask extends AbstractLoadedTask {

    protected final Class<?> source;

    public ReflectionLoadedTask(String id, String order, Class<?> source, boolean runAlways, boolean transactional) {
        super(id, order, runAlways, transactional);
        this.source = source;
    }

    public Class<?> getSourceClass() {
        return source;
    }

    @Override
    public String getSourceName() {
        return source.getName();
    }

    public abstract Constructor<?> getConstructor();

    public abstract Method getExecutionMethod();

    public abstract Optional<Method> getRollbackMethod();

    @Override
    public String toString() {
        return new StringJoiner(", ", ReflectionLoadedTask.class.getSimpleName() + "[", "]")
                .add("source=" + source)
                .add("sourceClass=" + getSourceClass())
                .add("sourceName='" + getSourceName() + "'")
                .add("id='" + getId() + "'")
                .add("runAlways=" + isRunAlways())
                .add("transactional=" + isTransactional())
                .add("order=" + getOrder())
                .add("sortable=" + isSortable())
                .toString();
    }
}
