/*
 * Copyright 2025 Flamingock (https://www.flamingock.io)
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
package io.flamingock.common.test.pipeline;

import io.flamingock.api.annotations.Recovery;
import io.flamingock.api.annotations.TargetSystem;
import io.flamingock.internal.common.core.preview.PreviewConstructor;
import io.flamingock.internal.core.task.loaded.ChangeOrderUtil;
import io.flamingock.internal.util.CollectionUtil;
import io.flamingock.api.annotations.Change;
import io.flamingock.internal.common.core.preview.AbstractPreviewTask;
import io.flamingock.internal.common.core.preview.CodePreviewChange;
import io.flamingock.internal.common.core.preview.PreviewMethod;
import io.flamingock.internal.common.core.task.RecoveryDescriptor;
import io.flamingock.internal.common.core.task.TargetSystemDescriptor;

import java.util.List;

public class CodeChangeTestDefinition extends ChangeTestDefinition {


    private final String className;
    private final String targetSystem;
    private final RecoveryDescriptor recovery;

    private final List<Class<?>> executionParameters;
    private final List<Class<?>> rollbackParameters;
    private final String author;

    public CodeChangeTestDefinition(Class<?> changeClass,
                                    List<Class<?>> executionParameters,
                                    List<Class<?>> rollbackParameters) {
        this(
                changeClass.getAnnotation(Change.class),
                changeClass.getAnnotation(TargetSystem.class),
                changeClass.getAnnotation(Recovery.class),
                changeClass.getName(),
                executionParameters,
                rollbackParameters
        );
    }

    public CodeChangeTestDefinition(Class<?> changeClass,
                                    List<Class<?>> executionParameters) {
        this(
                changeClass.getAnnotation(Change.class),
                changeClass.getAnnotation(TargetSystem.class),
                changeClass.getAnnotation(Recovery.class),
                changeClass.getName(),
                executionParameters,
                null
        );
    }

    private CodeChangeTestDefinition(Change changeAnn,
                                     TargetSystem targetSystemAnn,
                                     Recovery recoveryAnn,
                                     String className,
                                     List<Class<?>> executionParameters,
                                     List<Class<?>> rollbackParameters) {
        this(changeAnn.id(),
                ChangeOrderUtil.getMatchedOrderFromClassName(changeAnn.id(), null, className),
                changeAnn.author(),
                className,
                targetSystemAnn != null ? targetSystemAnn.id() : null,
                changeAnn.transactional(),
                RecoveryDescriptor.fromStrategy(recoveryAnn != null ? recoveryAnn.strategy() : null),
                executionParameters,
                rollbackParameters);
    }

    public CodeChangeTestDefinition(String id,
                                    String order,
                                    String author,
                                    String className,
                                    String targetSystem,
                                    boolean transactional,
                                    RecoveryDescriptor recovery,
                                    List<Class<?>> executionParameters,
                                    List<Class<?>> rollbackParameters) {
        super(id, order, transactional);
        this.targetSystem = targetSystem;
        this.recovery = recovery;
        this.className = className;
        this.author = author;
        this.executionParameters = executionParameters;
        this.rollbackParameters = rollbackParameters;
    }


    @Override
    public AbstractPreviewTask toPreview() {
        PreviewMethod rollback = null;
        if (rollbackParameters != null) {
            List<String> rollbackParameterNames = CollectionUtil.getClassNames(rollbackParameters);
            rollback = new PreviewMethod("rollback", rollbackParameterNames);
        }

        List<String> executionParameterNames = CollectionUtil.getClassNames(executionParameters);
        return new CodePreviewChange(
                getId(),
                getOrder(),
                author, // Default author for tests
                className,
                PreviewConstructor.getDefault(),
                new PreviewMethod("apply", executionParameterNames),
                rollback,
                false,
                isTransactional(),
                false,
                TargetSystemDescriptor.fromId(targetSystem),
                recovery,
                false
        );
    }

}
