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

package io.flamingock.core.task.navigation.navigator;

import io.flamingock.core.engine.audit.AuditWriter;
import io.flamingock.core.engine.audit.domain.AuditItem;
import io.flamingock.core.pipeline.execution.ExecutionContext;
import io.flamingock.core.task.executable.ParentExecutableTaskFactory;
import io.flamingock.core.task.navigation.navigator.beforeExecution_1.TaskWithBeforeExecution;
import io.flamingock.core.task.navigation.summary.StepSummarizer;
import io.flamingock.core.engine.lock.Lock;
import io.flamingock.core.runtime.RuntimeManager;
import io.flamingock.core.runtime.dependency.DependencyInjectableContext;
import io.flamingock.core.task.descriptor.TaskDescriptor;
import io.flamingock.core.task.descriptor.ReflectionTaskDescriptor;
import io.flamingock.core.task.executable.ExecutableTask;
import io.flamingock.commons.utils.Result;
import io.utils.EmptyTransactionWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static io.utils.TestTaskExecution.BEFORE_EXECUTION;
import static io.utils.TestTaskExecution.EXECUTION;
import static io.utils.TestTaskExecution.ROLLBACK_BEFORE_EXECUTION;
import static io.utils.TestTaskExecution.ROLLBACK_EXECUTION;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StepNavigatorTest {

    public static final ExecutionContext EXECUTION_CONTEXT = new ExecutionContext(
            "executionId", "hsotname", "author", new HashMap<>()
    );

    @BeforeEach
    void beforeEach() {
        TaskWithBeforeExecution.checker.reset();
    }


    @Test
    public void testasda() {
        System.out.println(UUID.randomUUID());
    }


    @Test
    @DisplayName("SHOULD run beforeExecution.Rollback IF task contains beforeExecution WHEN task fails")
    void test1() {
        //GIVEN
        AuditWriter auditWriterMock = mock(AuditWriter.class);
        when(auditWriterMock.writeStep(any(AuditItem.class))).thenReturn(Result.OK());

        StepSummarizer stepSummarizerMock = mock(StepSummarizer.class);
        RuntimeManager runtimeManagerMock = RuntimeManager.builder()
                .setDependencyContext(mock(DependencyInjectableContext.class))
                .setLock(mock(Lock.class))
                .build();

        //AND
        TaskDescriptor taskDescriptor = new ReflectionTaskDescriptor(
                "task-with-before-execution",
                "1",
                TaskWithBeforeExecution.class,
                false,
                false
        );
        List<? extends ExecutableTask> executableTasks = ParentExecutableTaskFactory.INSTANCE
                .extractTasks("stage_name", taskDescriptor, null);

        ExecutionContext stageExecutionContext = new ExecutionContext(
                "executionId", "hsotname", "author", new HashMap<>()
        );

        StepNavigator stepNavigator = new StepNavigator(auditWriterMock, stepSummarizerMock, runtimeManagerMock, null);
        //WHEN

        stepNavigator.executeTask(executableTasks.get(0), stageExecutionContext);
        stepNavigator.executeTask(executableTasks.get(1), stageExecutionContext);

        //THEN
        TaskWithBeforeExecution.checker.checkOrderStrict(
                BEFORE_EXECUTION,
                EXECUTION,
                ROLLBACK_EXECUTION,
                ROLLBACK_BEFORE_EXECUTION
        );
    }


    @Test
    @DisplayName("SHOULD run beforeExecution.Rollback " +
            "AND not execution.rollback " +
            "IF provided transactionWrapper " +
            "WHEN task fails")
    void test2() {
        //GIVEN
        AuditWriter auditWriterMock = mock(AuditWriter.class);
        when(auditWriterMock.writeStep(any(AuditItem.class))).thenReturn(Result.OK());

        StepSummarizer stepSummarizerMock = mock(StepSummarizer.class);
        RuntimeManager runtimeManagerMock = RuntimeManager.builder()
                .setDependencyContext(mock(DependencyInjectableContext.class))
                .setLock(mock(Lock.class))
                .build();

        //AND
        TaskDescriptor taskDescriptor = new ReflectionTaskDescriptor(
                "task-with-before-execution",
                "1",
                TaskWithBeforeExecution.class,
                false,
                true
        );
        List<? extends ExecutableTask> executableTasks = ParentExecutableTaskFactory.INSTANCE
                .extractTasks("stage-name", taskDescriptor, null);

        EmptyTransactionWrapper transactionWrapper = new EmptyTransactionWrapper();
        StepNavigator stepNavigator = new StepNavigator(auditWriterMock, stepSummarizerMock, runtimeManagerMock, transactionWrapper);
        //WHEN
        stepNavigator.executeTask(executableTasks.get(0), EXECUTION_CONTEXT);
        stepNavigator.executeTask(executableTasks.get(1), EXECUTION_CONTEXT);

        //THEN
        TaskWithBeforeExecution.checker.checkOrderStrict(
                BEFORE_EXECUTION,
                EXECUTION,
                ROLLBACK_BEFORE_EXECUTION
        );

        assertTrue(transactionWrapper.isCalled());
    }

}