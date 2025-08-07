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
package io.flamingock.core.annotations;

import io.flamingock.core.utils.EmptyTransactionWrapper;
import io.flamingock.core.utils.TaskExecutionChecker;
import io.flamingock.core.utils.TestTaskExecution;
import io.flamingock.internal.common.core.context.Context;
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.core.cloud.transaction.CloudTransactioner;
import io.flamingock.internal.core.context.PriorityContextResolver;
import io.flamingock.internal.core.context.SimpleContext;
import io.flamingock.internal.core.engine.audit.ExecutionAuditWriter;
import io.flamingock.internal.core.engine.audit.domain.ExecutionAuditContextBundle;
import io.flamingock.internal.core.engine.audit.domain.RollbackAuditContextBundle;
import io.flamingock.internal.core.engine.audit.domain.StartExecutionAuditContextBundle;
import io.flamingock.internal.core.engine.lock.Lock;
import io.flamingock.internal.core.pipeline.execution.ExecutionContext;
import io.flamingock.internal.core.pipeline.execution.TaskSummarizer;
import io.flamingock.internal.core.runtime.RuntimeManager;
import io.flamingock.internal.core.targets.AbstractTargetSystem;
import io.flamingock.internal.core.targets.ContextDecoratorTargetSystem;
import io.flamingock.internal.core.targets.NoOpOnGoingTaskStatusRepository;
import io.flamingock.internal.core.targets.OngoingTaskStatusRepository;
import io.flamingock.internal.core.targets.TransactionalTargetSystem;
import io.flamingock.internal.core.task.executable.ExecutableTask;
import io.flamingock.internal.core.task.executable.builder.ExecutableTaskBuilder;
import io.flamingock.internal.core.task.loaded.AbstractLoadedTask;
import io.flamingock.internal.core.task.loaded.LoadedTaskBuilder;
import io.flamingock.internal.core.task.navigation.navigator.StepNavigator;
import io.flamingock.internal.core.task.navigation.navigator.StepNavigatorBuilder;
import io.flamingock.internal.core.task.navigation.navigator.operations.AuditStoreStepOperations;
import io.flamingock.internal.core.task.navigation.navigator.operations.TargetSystemStepOperations;
import io.flamingock.internal.core.transaction.TransactionWrapper;
import io.flamingock.internal.util.Result;
import org.junit.jupiter.api.Assertions;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
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

        ExecutableTask changeUnitMock = Mockito.mock(ExecutableTask.class);
        when(changeUnitMock.getId()).thenReturn("taskId");

        TaskSummarizer stepSummarizerMock = new TaskSummarizer(changeUnitMock);

        //AND
        AbstractLoadedTask loadedTask = LoadedTaskBuilder.getCodeBuilderInstance(changeUnitClass).build();
        List<? extends ExecutableTask> executableChangeUnits = ExecutableTaskBuilder
                .getInstance(loadedTask)
                .setStageName("stage_name")
                .setInitialState(null)
                .build();

        ExecutionContext stageExecutionContext = new ExecutionContext(
                "executionId", "hostname", "author", new HashMap<>()
        );

        EmptyTransactionWrapper transactionWrapper = useTransactionWrapper ? new EmptyTransactionWrapper() : null;



        executableChangeUnits.forEach(changeUnit -> {

            ContextDecoratorTargetSystem targetSystem;
            if(transactionWrapper != null) {
                targetSystem = new TransactionalTargetSystem("default-target-system-id") {
                    @Override
                    public OngoingTaskStatusRepository getOnGoingTaskStatusRepository() {
                        return new NoOpOnGoingTaskStatusRepository("default-target-system-id");
                    }

                    @Override
                    public TransactionWrapper getTxWrapper() {
                        return transactionWrapper;
                    }

                    @Override
                    public void initialize(ContextResolver baseContext) {

                    }

                    @Override
                    protected AbstractTargetSystem getSelf() {
                        return this;
                    }
                };
            } else {
                targetSystem = new ContextDecoratorTargetSystem() {
                    @Override
                    public String getId() {
                        return "default-target-system-id";
                    }

                    @Override
                    public ContextResolver decorateOnTop(ContextResolver baseContext) {
                        return new PriorityContextResolver(new SimpleContext(), baseContext);
                    }
                };
            }

            TargetSystemStepOperations targetSystemOps = StepNavigatorBuilder.buildTargetSystemOperations(
                    targetSystem,
                    null,
                    new SimpleContext(),
                    mock(Lock.class),
                    Collections.emptySet());
            

            new StepNavigator(changeUnit, stageExecutionContext, targetSystemOps, new AuditStoreStepOperations(auditWriterMock), stepSummarizerMock)
                    .start();

        });


        Assertions.assertEquals(expectedNumberOfExecutableTasks, executableChangeUnits.size());
        checker.checkOrderStrict(Arrays.asList(executionSteps));
        if (useTransactionWrapper) {
            Assertions.assertTrue(transactionWrapper.isCalled());
        }
    }
}
