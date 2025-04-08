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

package io.flamingock.core.task.loaded;

import io.flamingock.commons.utils.ReflectionUtil;
import io.flamingock.core.api.annotations.Execution;
import io.flamingock.core.api.annotations.RollbackExecution;
import io.mongock.api.annotations.BeforeExecution;
import io.mongock.api.annotations.RollbackBeforeExecution;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.StringJoiner;

public class CodeLoadedChangeUnit extends AbstractLoadedChangeUnit {


    CodeLoadedChangeUnit(String id,
                         String order,
                         Class<?> source,
                         boolean runAlways,
                         boolean transactional,
                         boolean newChangeUnit,
                         boolean systemTask) {
        super(id, order, source, runAlways, transactional, newChangeUnit, systemTask);
    }

    @Override
    public Method getExecutionMethod() {
        if (isNewChangeUnit()) {
            return ReflectionUtil.findFirstAnnotatedMethod(getSourceClass(), Execution.class)
                    .orElseThrow(() -> new IllegalArgumentException(String.format(
                            "Executable changeUnit[%s] without %s method",
                            getSource(),
                            Execution.class.getName())));
        } else {
            Optional<Method> legacyExecutionMethod = ReflectionUtil.findFirstAnnotatedMethod(getSourceClass(), io.mongock.api.annotations.Execution.class);
            if (!legacyExecutionMethod.isPresent()) {
                if (ReflectionUtil.findFirstAnnotatedMethod(getSourceClass(), Execution.class).isPresent()) {
                    throw new IllegalArgumentException(String.format(
                            "You are using new API for Execution annotation in your changeUnit class[%s], however your class is annotated with legacy ChangeUnit annotation[%s]. " +
                                    "It's highly recommended to use the new API[in package %s], unless it's a legacy changeUnit created with Mongock",
                            getSource(),
                            io.mongock.api.annotations.Execution.class.getName(),
                            "io.flamingock.core.api.annotations"));
                }
            }
            return legacyExecutionMethod
                    .orElseThrow(() -> new IllegalArgumentException(String.format(
                            "Your changeUnit class[%s] doesn't contain execution method.\n" +
                                    "It's highly recommended to use the new API[in package %s].\n" +
                                    "In case it's an legacy changeUnit created with Mongock, please add the execution method annotated with legacy API[%s] ",
                            getSource(),
                            "io.flamingock.core.api.annotations",
                            io.mongock.api.annotations.Execution.class.getName())));
        }
    }

    @Override
    public Optional<Method> getRollbackMethod() {
        if (isNewChangeUnit()) {
            Optional<Method> firstAnnotatedMethod = ReflectionUtil.findFirstAnnotatedMethod(getSourceClass(), RollbackExecution.class);
            if (!firstAnnotatedMethod.isPresent()) {
                if (ReflectionUtil.findFirstAnnotatedMethod(getSourceClass(), io.mongock.api.annotations.RollbackExecution.class).isPresent()) {
                    throw new IllegalArgumentException(String.format(
                            "Executable changeUnit[%s] rollback method should be annotated with new API[%s], instead of legacy API[%s] ",
                            getSource(),
                            RollbackExecution.class.getName(),
                            io.mongock.api.annotations.RollbackExecution.class.getName()));
                }
            }
            return firstAnnotatedMethod;
        } else {
            Optional<Method> firstAnnotatedMethod = ReflectionUtil.findFirstAnnotatedMethod(getSourceClass(), io.mongock.api.annotations.RollbackExecution.class);
            if (!firstAnnotatedMethod.isPresent()) {
                if (ReflectionUtil.findFirstAnnotatedMethod(getSourceClass(), RollbackExecution.class).isPresent()) {
                    throw new IllegalArgumentException(String.format(
                            "You are using new API for RollbackExecution annotation in your changeUnit class[%s], however your class is annotated with legacy ChangeUnit annotation[%s]. " +
                                    "It's highly recommended to use the new API[in package %s], unless it's a legacy changeUnit created with Mongock",
                            getSource(),
                            io.mongock.api.annotations.RollbackExecution.class.getName(),
                            "io.flamingock.core.api.annotations"));
                }
            }
            return firstAnnotatedMethod;
        }
    }

    public Optional<Method> getBeforeExecutionMethod() {
        return getMethodFromDeprecatedAnnotation(BeforeExecution.class);
    }

    public Optional<Method> getRollbackBeforeExecutionMethod() {
        return getMethodFromDeprecatedAnnotation(RollbackBeforeExecution.class);
    }

    private Optional<Method> getMethodFromDeprecatedAnnotation(Class<? extends Annotation> annotation) {
        Optional<Method> rollbackBeforeExecution = ReflectionUtil.findFirstAnnotatedMethod(getSourceClass(), annotation);
        if (isNewChangeUnit() && rollbackBeforeExecution.isPresent()) {
            throw new IllegalArgumentException(String.format("You are using legacy annotation [%s] with new API. You should create an independent ChangeUnit for it",
                    annotation.getName()));
        }
        return isNewChangeUnit() ? Optional.empty() : rollbackBeforeExecution;
    }


    @Override
    public String pretty() {
        String fromParent = super.pretty();
        return fromParent + String.format("\n\t\t[class: %s]", getSource());
    }


    @Override
    public String toString() {
        return new StringJoiner(", ", CodeLoadedChangeUnit.class.getSimpleName() + "[", "]")
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
