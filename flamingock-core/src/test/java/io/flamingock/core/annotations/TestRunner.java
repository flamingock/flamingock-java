package io.flamingock.core.annotations;

import io.flamingock.commons.utils.Result;
import io.flamingock.core.cloud.audit.CloudAuditWriter;
import io.flamingock.core.cloud.transaction.CloudTransactioner;
import io.flamingock.core.engine.audit.domain.ExecutionAuditItem;
import io.flamingock.core.engine.audit.domain.RollbackAuditItem;
import io.flamingock.core.engine.audit.domain.StartExecutionAuditItem;
import io.flamingock.core.engine.lock.Lock;
import io.flamingock.core.task.executable.builder.ExecutableTaskBuilder;
import io.flamingock.core.task.loaded.AbstractLoadedTask;
import io.flamingock.core.task.loaded.LoadedTaskBuilder;
import io.flamingock.core.utils.EmptyTransactionWrapper;
import io.flamingock.core.utils.TaskExecutionChecker;
import io.flamingock.core.utils.TestTaskExecution;
import io.flamingock.core.pipeline.execution.ExecutionContext;
import io.flamingock.core.pipeline.execution.TaskSummarizer;
import io.flamingock.core.runtime.RuntimeManager;
import io.flamingock.core.runtime.dependency.DependencyInjectableContext;
import io.flamingock.core.task.executable.ExecutableTask;
import io.flamingock.core.task.navigation.navigator.StepNavigator;
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
        CloudAuditWriter auditWriterMock = mock(CloudAuditWriter.class);
        when(auditWriterMock.writeStartExecution(any(StartExecutionAuditItem.class))).thenReturn(Result.OK());
        when(auditWriterMock.writeExecution(any(ExecutionAuditItem.class))).thenReturn(Result.OK());
        when(auditWriterMock.writeRollback(any(RollbackAuditItem.class))).thenReturn(Result.OK());

        TaskSummarizer stepSummarizerMock = new TaskSummarizer("taskId");
        RuntimeManager runtimeManagerMock = RuntimeManager.builder()
                .setDependencyContext(mock(DependencyInjectableContext.class))
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
