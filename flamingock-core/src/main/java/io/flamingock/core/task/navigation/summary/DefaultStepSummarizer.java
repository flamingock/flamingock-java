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

package io.flamingock.core.task.navigation.summary;

import io.flamingock.core.task.navigation.step.afteraudit.AfterExecutionAuditStep;
import io.flamingock.core.task.navigation.step.complete.CompletedAlreadyAppliedStep;
import io.flamingock.core.task.navigation.step.complete.failed.CompletedFailedManualRollback;
import io.flamingock.core.task.navigation.step.execution.ExecutionStep;
import io.flamingock.core.task.navigation.step.rolledback.RolledBackStep;
import io.flamingock.core.task.descriptor.TaskDescriptor;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

//NO thread-safe
public class DefaultStepSummarizer implements StepSummarizer {

    private List<StepSummaryLine> lines = new LinkedList<>();

    @Override
    public void clear() {
        lines = new LinkedList<>();
    }

    @Override
    public StepSummarizer add(StepSummaryLine line) {
        lines.add(line);
        return this;
    }

    @Override
    public StepSummarizer add(ExecutionStep step) {
        return addStep(step.getTask().getDescriptor(), new StepSummaryLine.ExecutedSummaryLine(step));
    }

    @Override
    public StepSummarizer add(AfterExecutionAuditStep step) {
        return addStep(step.getTask().getDescriptor(), new StepSummaryLine.AfterExecutionAuditSummaryLine(step));
    }

    @Override
    public StepSummarizer add(RolledBackStep step) {
        return addStep(step.getTask().getDescriptor(), new StepSummaryLine.RolledBackSummaryLine(step));
    }

    @Override
    public StepSummarizer add(CompletedFailedManualRollback step) {
        return addStep(step.getTask().getDescriptor(), new StepSummaryLine.FailedCompletedManualRollbackSummaryLine(step));
    }

    @Override
    public StepSummarizer add(CompletedAlreadyAppliedStep step) {
        return addStep(step.getTask().getDescriptor(), new StepSummaryLine.AlreadyAppliedSummaryLine(step));
    }

    @Override
    public StepSummary getSummary() {
        return () -> new CopyOnWriteArrayList<>(lines);
    }

    private StepSummarizer addStep(TaskDescriptor taskDescriptor, StepSummaryLine step) {
        if (lines.isEmpty()) {
            add(new StepSummaryLine.InitialSummaryLine(taskDescriptor));
        }
        return add(step);
    }
}
