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

package io.flamingock.core.pipeline.execution;

import io.flamingock.core.task.navigation.step.ExecutableStep;
import io.flamingock.core.task.navigation.step.afteraudit.AfterExecutionAuditStep;
import io.flamingock.core.task.navigation.step.complete.CompletedAlreadyAppliedStep;
import io.flamingock.core.task.navigation.step.complete.failed.CompletedFailedManualRollback;
import io.flamingock.core.task.navigation.step.execution.ExecutionStep;
import io.flamingock.core.task.navigation.step.rolledback.RolledBackStep;
import io.flamingock.core.task.descriptor.TaskDescriptor;
import io.flamingock.core.task.navigation.summary.AbstractTaskStepSummaryLine;
import io.flamingock.core.task.navigation.summary.StepSummarizer;
import io.flamingock.core.task.navigation.summary.StepSummaryLine;

import java.util.LinkedList;
import java.util.List;

//NO thread-safe
public class TaskSummarizer implements StepSummarizer<TaskSummarizer> {

    private final String taskId;

    private boolean success = false;

    private List<StepSummaryLine> lines = new LinkedList<>();

    public TaskSummarizer(String taskId) {
        this.taskId = taskId;
    }

    @Override
    public void clear() {
        lines = new LinkedList<>();
    }

    @Override
    public TaskSummarizer add(StepSummaryLine line) {
        lines.add(line);
        return this;
    }

    @Override
    public TaskSummarizer add(ExecutableStep step) {
        return addStep(step.getTask().getDescriptor(), new AbstractTaskStepSummaryLine.StartedTaskSummaryLine(step));
    }

    @Override
    public TaskSummarizer add(ExecutionStep step) {
        return addStep(step.getTask().getDescriptor(), new AbstractTaskStepSummaryLine.ExecutedTaskSummaryLine(step));
    }

    @Override
    public TaskSummarizer add(AfterExecutionAuditStep step) {
        return addStep(step.getTask().getDescriptor(), new AbstractTaskStepSummaryLine.AfterExecutionTaskAuditSummaryLine(step));
    }

    @Override
    public TaskSummarizer add(RolledBackStep step) {
        return addStep(step.getTask().getDescriptor(), new AbstractTaskStepSummaryLine.RolledBackTaskSummaryLine(step));
    }

    @Override
    public TaskSummarizer add(CompletedFailedManualRollback step) {
        return addStep(step.getTask().getDescriptor(), new AbstractTaskStepSummaryLine.FailedCompletedManualRollbackTaskSummaryLine(step));
    }

    @Override
    public TaskSummarizer add(CompletedAlreadyAppliedStep step) {
        return addStep(step.getTask().getDescriptor(), new AbstractTaskStepSummaryLine.AlreadyAppliedTaskSummaryLine(step));
    }

    @Override
    public TaskSummarizer addNotReachedTask(TaskDescriptor taskDescriptor) {
        add(new AbstractTaskStepSummaryLine.InitialTaskSummaryLine(taskDescriptor));
        add(new AbstractTaskStepSummaryLine.NotReachedTaskSummaryLine(taskDescriptor));
        return this;
    }

    public TaskSummarizer setSuccessful() {
        success = true;
        return this;
    }

    public TaskSummarizer setFailed() {
        success = false;
        return this;
    }

    @Override
    public TaskSummary getSummary() {
        TaskSummary taskSummary = new TaskSummary(taskId, success);
        lines.forEach(taskSummary::addLine);
        return taskSummary;
    }

    private TaskSummarizer addStep(TaskDescriptor taskDescriptor, StepSummaryLine step) {
        if (lines.isEmpty()) {
            add(new AbstractTaskStepSummaryLine.InitialTaskSummaryLine(taskDescriptor));
        }
        return add(step);
    }
}
