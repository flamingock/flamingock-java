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

import io.flamingock.api.targets.TargetSystem;
import io.flamingock.core.utils.EmptyTransactionWrapper;
import io.flamingock.core.utils.TaskExecutionChecker;
import io.flamingock.core.utils.TestTaskExecution;
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.core.context.SimpleContext;
import io.flamingock.internal.core.store.audit.LifecycleAuditWriter;
import io.flamingock.internal.core.store.audit.domain.ExecutionAuditContextBundle;
import io.flamingock.internal.core.store.audit.domain.RollbackAuditContextBundle;
import io.flamingock.internal.core.store.audit.domain.StartExecutionAuditContextBundle;
import io.flamingock.internal.core.store.lock.Lock;
import io.flamingock.internal.common.core.recovery.action.ChangeAction;
import io.flamingock.internal.core.pipeline.execution.ExecutionContext;
import io.flamingock.internal.core.pipeline.execution.TaskSummarizer;
import io.flamingock.internal.core.targets.AbstractTargetSystem;
import io.flamingock.internal.core.targets.TargetSystemManager;
import io.flamingock.internal.core.targets.mark.NoOpTargetSystemAuditMarker;
import io.flamingock.internal.core.targets.mark.TargetSystemAuditMarker;
import io.flamingock.internal.core.targets.TransactionalTargetSystem;
import io.flamingock.internal.core.task.executable.ExecutableTask;
import io.flamingock.internal.core.task.executable.builder.ExecutableTaskBuilder;
import io.flamingock.internal.core.task.loaded.AbstractLoadedTask;
import io.flamingock.internal.core.task.loaded.LoadedTaskBuilder;
import io.flamingock.internal.core.task.navigation.navigator.ChangeProcessStrategyFactory;
import io.flamingock.internal.core.transaction.TransactionWrapper;
import io.flamingock.internal.util.Result;
import org.junit.jupiter.api.Assertions;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
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
        LifecycleAuditWriter auditWriterMock = mock(LifecycleAuditWriter.class);
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
                .setChangeAction(ChangeAction.APPLY)
                .build();

        ExecutionContext stageExecutionContext = new ExecutionContext(
                "executionId", "hostname", "author", new HashMap<>()
        );

        EmptyTransactionWrapper transactionWrapper = useTransactionWrapper ? new EmptyTransactionWrapper() : null;


        executableChangeUnits.forEach(changeUnit -> {

            String targetSystemId = "default-target-system-id";
            TargetSystem targetSystem = transactionWrapper != null
                    ? new TestTransactionTargetSystem(targetSystemId, transactionWrapper)
                    : new TestTargetSystem(targetSystemId);
            TargetSystemManager targetSystemManager = new TargetSystemManager();
            targetSystemManager.add(targetSystem);
            targetSystemManager.setAuditStoreTargetSystem(targetSystem);

            new ChangeProcessStrategyFactory(targetSystemManager)
                    .setExecutionContext(stageExecutionContext)
                    .setAuditWriter(auditWriterMock)
                    .setDependencyContext(new SimpleContext())
                    .setLock(mock(Lock.class))
                    .setNonGuardedTypes(new HashSet<>())
                    .setChangeUnit(changeUnit)
                    .build()
                    .applyChange();
        });


        Assertions.assertEquals(expectedNumberOfExecutableTasks, executableChangeUnits.size());
        checker.checkOrderStrict(Arrays.asList(executionSteps));
        if (useTransactionWrapper) {
            Assertions.assertTrue(transactionWrapper.isCalled());
        }
    }

    private static class TestTransactionTargetSystem extends TransactionalTargetSystem<TestTransactionTargetSystem> {

        private final TransactionWrapper txWrapper;

        public TestTransactionTargetSystem(String id, TransactionWrapper txWrapper) {
            super(id);
            this.txWrapper = txWrapper;
        }

        @Override
        public TargetSystemAuditMarker getOnGoingTaskStatusRepository() {
            return new NoOpTargetSystemAuditMarker("default-target-system-id");
        }

        @Override
        public TransactionWrapper getTxWrapper() {
            return txWrapper;
        }

        @Override
        public boolean isSameTxResourceAs(TransactionalTargetSystem<?> txInstance) {
            return true;
        }

        @Override
        protected TestTransactionTargetSystem getSelf() {
            return this;
        }

        @Override
        public void initialize(ContextResolver baseContext) {

        }
    }

    private static class TestTargetSystem extends AbstractTargetSystem<TestTargetSystem> {

        public TestTargetSystem(String id) {
            super(id);
        }

        @Override
        protected TestTargetSystem getSelf() {
            return this;
        }

    }
}
