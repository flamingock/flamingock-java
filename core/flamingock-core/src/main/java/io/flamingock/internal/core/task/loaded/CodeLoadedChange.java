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
package io.flamingock.internal.core.task.loaded;

import io.flamingock.api.task.ChangeCategory;
import io.flamingock.internal.common.core.task.RecoveryDescriptor;
import io.flamingock.internal.common.core.task.TargetSystemDescriptor;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.StringJoiner;

public class CodeLoadedChange extends AbstractLoadedChange {

    private final Method applyMethod;
    private final Optional<Method> rollbackMethod;

    CodeLoadedChange(String id,
                     String order,
                     String author,
                     Class<?> changeClass,
                     Constructor<?> constructor,
                     Method applyMethod,
                     Optional<Method> rollbackMethod,
                     boolean runAlways,
                     boolean transactional,
                     boolean systemTask,
                     TargetSystemDescriptor targetSystem,
                     RecoveryDescriptor recovery,
                     boolean legacy) {
        super(changeClass.getSimpleName(), id, order, author, changeClass, constructor, runAlways, transactional, systemTask, targetSystem, recovery, legacy);
        this.applyMethod = applyMethod;
        this.rollbackMethod = rollbackMethod;
    }

    @Override
    public Method getApplyMethod() {
        return this.applyMethod;
    }

    @Override
    public Optional<Method> getRollbackMethod() {
        return this.rollbackMethod;
    }

    @Override
    public boolean hasCategory(ChangeCategory property) {
        return false;
    }

    @Override
    public String pretty() {
        String fromParent = super.pretty();
        return fromParent + String.format("\n\t\t[class: %s]", getSource());
    }


    @Override
    public String toString() {
        return new StringJoiner(", ", CodeLoadedChange.class.getSimpleName() + "[", "]")
                .add("source=" + source)
                .add("sourceClass=" + getSource())
                .add("sourceName='" + getSource() + "'")
                .add("id='" + getId() + "'")
                .add("runAlways=" + isRunAlways())
                .add("transactional=" + isTransactional())
                .add("order=" + getOrder())
                .add("sortable=" + isSortable())
                .toString();
    }
}
