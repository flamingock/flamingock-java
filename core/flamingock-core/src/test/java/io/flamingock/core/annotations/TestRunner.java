package io.flamingock.core.annotations;

import io.flamingock.internal.util.Result;
import io.flamingock.internal.core.cloud.transaction.CloudTransactioner;
import io.flamingock.internal.core.engine.audit.ExecutionAuditWriter;
import io.flamingock.internal.core.engine.audit.domain.ExecutionAuditContextBundle;
import io.flamingock.internal.core.engine.audit.domain.RollbackAuditContextBundle;
import io.flamingock.internal.core.engine.audit.domain.StartExecutionAuditContextBundle;
import io.flamingock.internal.core.engine.lock.Lock;
import io.flamingock.internal.core.task.executable.builder.ExecutableTaskBuilder;
import io.flamingock.internal.core.task.loaded.AbstractLoadedTask;
import io.flamingock.internal.core.task.loaded.LoadedTaskBuilder;
import io.flamingock.core.utils.EmptyTransactionWrapper;
import io.flamingock.core.utils.TaskExecutionChecker;
import io.flamingock.core.utils.TestTaskExecution;
import io.flamingock.internal.core.pipeline.execution.ExecutionContext;
import io.flamingock.internal.core.pipeline.execution.TaskSummarizer;
import io.flamingock.internal.core.runtime.RuntimeManager;
import io.flamingock.internal.common.core.context.Context;
import io.flamingock.internal.core.task.executable.ExecutableTask;
import io.flamingock.internal.core.task.navigation.navigator.StepNavigator;
import org.junit.jupiter.api.Assertions;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestRunner {

    public static void runTest(Class<?> changeUnitClass,
                                int expectedNumberOfExecutableTasks,
                                TaskExecutionChecker checker,
                                TestTaskExecution... executionSteps
    ) {
        runTestInternal(changeUnitClass, expectedNumberOfExecutableTasks, checker, false, executionSteps);
    }

    public static void runTestWithTransaction(Class<?> changeUnitClass,
                               int expectedNumberOfExecutableTasks,
                               TaskExecutionChecker checker,
                               TestTaskExecution... executionSteps
    ) {
        runTestInternal(changeUnitClass, expectedNumberOfExecutableTasks, checker, true, executionSteps);
    }


    private static void runTestInternal(Class<?> changeUnitClass,
                        int expectedNumberOfExecutableTasks,
                        TaskExecutionChecker checker,
                        boolean useTransactionWrapper,
                        TestTaskExecution... executionSteps
    ) {
        checker.reset();
        ExecutionAuditWriter auditWriterMock = mock(ExecutionAuditWriter.class);
        when(auditWriterMock.writeStartExecution(any(StartExecutionAuditContextBundle.class))).thenReturn(Result.OK());
        when(auditWriterMock.writeExecution(any(ExecutionAuditContextBundle.class))).thenReturn(Result.OK());
        when(auditWriterMock.writeRollback(any(RollbackAuditContextBundle.class))).thenReturn(Result.OK());

        TaskSummarizer stepSummarizerMock = new TaskSummarizer("taskId");
        RuntimeManager runtimeManagerMock = RuntimeManager.builder()
                .setDependencyContext(mock(Context.class))
                .setLock(mock(Lock.class))
                .build();

        //AND
        AbstractLoadedTask loadedTask = LoadedTaskBuilder.getCodeBuilderInstance(changeUnitClass).build();
        List<? extends ExecutableTask> executableTasks = ExecutableTaskBuilder
                .getInstance(loadedTask)
                .setStageName("stage_name")
                .setInitialState(null)
                .build();

        ExecutionContext stageExecutionContext = new ExecutionContext(
                "executionId", "hostname", "author", new HashMap<>()
        );

        EmptyTransactionWrapper transactionWrapper = useTransactionWrapper ? new EmptyTransactionWrapper(): null;
        if (transactionWrapper != null) {
            CloudTransactioner.class.isAssignableFrom(transactionWrapper.getClass());
        }

        StepNavigator stepNavigator = new StepNavigator(auditWriterMock, stepSummarizerMock, runtimeManagerMock, transactionWrapper, null);

        executableTasks.forEach(executableTask -> stepNavigator.executeTask(executableTask, stageExecutionContext));

        Assertions.assertEquals(expectedNumberOfExecutableTasks, executableTasks.size());
        checker.checkOrderStrict(Arrays.asList(executionSteps));
        if(useTransactionWrapper) {
            Assertions.assertTrue(transactionWrapper.isCalled());
        }
    }
}
