package io.flamingock.common.test.pipeline;

import io.flamingock.internal.util.CollectionUtil;
import io.flamingock.api.annotations.ChangeUnit;
import io.flamingock.internal.common.core.preview.AbstractPreviewTask;
import io.flamingock.internal.common.core.preview.CodePreviewChangeUnit;
import io.flamingock.internal.common.core.preview.PreviewMethod;

import java.util.List;

public class CodeChangeUnitTestDefinition extends ChangeUnitTestDefinition {


    private final String className;

    private final List<Class<?>> executionParameters;
    private final List<Class<?>> rollbackParameters;

    public CodeChangeUnitTestDefinition(Class<?> changeUnitClass,
                                        List<Class<?>> executionParameters,
                                        List<Class<?>> rollbackParameters) {
        this(
                changeUnitClass.getAnnotation(ChangeUnit.class),
                changeUnitClass.getName(),
                executionParameters,
                rollbackParameters
        );
    }

    public CodeChangeUnitTestDefinition(Class<?> changeUnitClass,
                                        List<Class<?>> executionParameters) {
        this(
                changeUnitClass.getAnnotation(ChangeUnit.class),
                changeUnitClass.getName(),
                executionParameters,
                null
        );
    }

    private CodeChangeUnitTestDefinition(ChangeUnit ann,
                                         String className,
                                         List<Class<?>> executionParameters,
                                         List<Class<?>> rollbackParameters) {
        this(ann.id(), ann.order(), className, ann.transactional(), executionParameters, rollbackParameters);
    }

    public CodeChangeUnitTestDefinition(String id,
                                        String order,
                                        String className,
                                        boolean transactional,
                                        List<Class<?>> executionParameters,
                                        List<Class<?>> rollbackParameters) {
        super(id, order, transactional);
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
                false
        );
    }

}
