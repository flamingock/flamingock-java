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
import io.flamingock.internal.util.CollectionUtil;
import io.flamingock.api.annotations.ChangeUnit;
import io.flamingock.internal.common.core.preview.AbstractPreviewTask;
import io.flamingock.internal.common.core.preview.CodePreviewChangeUnit;
import io.flamingock.internal.common.core.preview.PreviewMethod;
import io.flamingock.internal.common.core.task.RecoveryDescriptor;
import io.flamingock.internal.common.core.task.TargetSystemDescriptor;

import java.util.List;

public class CodeChangeUnitTestDefinition extends ChangeUnitTestDefinition {


    private final String className;
    private final String targetSystem;
    private final RecoveryDescriptor recovery;

    private final List<Class<?>> executionParameters;
    private final List<Class<?>> rollbackParameters;

    public CodeChangeUnitTestDefinition(Class<?> changeUnitClass,
                                        List<Class<?>> executionParameters,
                                        List<Class<?>> rollbackParameters) {
        this(
                changeUnitClass.getAnnotation(ChangeUnit.class),
                changeUnitClass.getAnnotation(TargetSystem.class),
                changeUnitClass.getAnnotation(Recovery.class),
                changeUnitClass.getName(),
                executionParameters,
                rollbackParameters
        );
    }

    public CodeChangeUnitTestDefinition(Class<?> changeUnitClass,
                                        List<Class<?>> executionParameters) {
        this(
                changeUnitClass.getAnnotation(ChangeUnit.class),
                changeUnitClass.getAnnotation(TargetSystem.class),
                changeUnitClass.getAnnotation(Recovery.class),
                changeUnitClass.getName(),
                executionParameters,
                null
        );
    }

    private CodeChangeUnitTestDefinition(ChangeUnit changeUnitAnn,
                                         TargetSystem targetSystemAnn,
                                         Recovery recoveryAnn,
                                         String className,
                                         List<Class<?>> executionParameters,
                                         List<Class<?>> rollbackParameters) {
        this(changeUnitAnn.id(),
                changeUnitAnn.order(),
                className,
                targetSystemAnn != null ? targetSystemAnn.id() : null,
                changeUnitAnn.transactional(),
                RecoveryDescriptor.fromStrategy(recoveryAnn != null ? recoveryAnn.strategy() : null),
                executionParameters,
                rollbackParameters);
    }

    public CodeChangeUnitTestDefinition(String id,
                                        String order,
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
        this.executionParameters = executionParameters;
        this.rollbackParameters = rollbackParameters;
    }


    @Override
    public AbstractPreviewTask toPreview() {
        PreviewMethod rollback = null;
        PreviewMethod rollbackBeforeExecution = null;
        if (rollbackParameters != null) {
            List<String> rollbackParameterNames = CollectionUtil.getClassNames(rollbackParameters);
            rollback = new PreviewMethod("rollbackExecution", rollbackParameterNames);
            rollbackBeforeExecution = new PreviewMethod("rollbackBeforeExecution", rollbackParameterNames);
        }

        List<String> executionParameterNames = CollectionUtil.getClassNames(executionParameters);
        return new CodePreviewChangeUnit(
                getId(),
                getOrder(),
                className,
                new PreviewMethod("execution", executionParameterNames),
                rollback,
                new PreviewMethod("beforeExecution", executionParameterNames),
                rollbackBeforeExecution,
                false,
                isTransactional(),
                false,
                TargetSystemDescriptor.fromId(targetSystem),
                recovery
        );
    }

}
