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
import io.flamingock.core.summary.SummaryLine;
import io.flamingock.core.task.descriptor.TaskDescriptor;

import java.util.Objects;

public abstract class StepSummaryLine implements SummaryLine {

    private enum SummaryResult {
        OK("OK"), FAILED("FAILED"), ALREADY_APPLIED("IGNORED - Already applied");

        private final String value;

        SummaryResult(String value) {
            this.value = value;
        }
    }

    private final String id;

    protected SummaryResult result;

    private StepSummaryLine(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public String getPrettyResult() {
        return result.value;
    }

    protected void setResultFromSuccess(boolean success) {
        result = success ? SummaryResult.OK : SummaryResult.FAILED;
    }


    public static class InitialSummaryLine extends StepSummaryLine {

        private final TaskDescriptor desc;

        public InitialSummaryLine(TaskDescriptor taskDescriptor) {
            super(taskDescriptor.getId());
            this.desc = taskDescriptor;
        }

        @Override
        public String getLine() {
            return ">> " + desc.pretty();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof InitialSummaryLine)) return false;
            InitialSummaryLine initial = (InitialSummaryLine) o;
            return desc.equals(initial.desc);
        }

        @Override
        public int hashCode() {
            return Objects.hash(desc);
        }


    }

    public static class ExecutedSummaryLine extends StepSummaryLine {

        public ExecutedSummaryLine(ExecutionStep step) {
            super(step.getTask().getDescriptor().getId());
            setResultFromSuccess(step.isSuccessStep());
        }


        @Override
        public String getLine() {
            return String.format("\tExecution\t\t[%s]", getPrettyResult());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ExecutedSummaryLine)) return false;
            ExecutedSummaryLine executed = (ExecutedSummaryLine) o;
            return result == executed.result;
        }

        @Override
        public int hashCode() {
            return Objects.hash(result);
        }
    }

    public static class AfterExecutionAuditSummaryLine extends StepSummaryLine {


        public AfterExecutionAuditSummaryLine(AfterExecutionAuditStep step) {
            super(step.getTask().getDescriptor().getId());
            setResultFromSuccess(step.isSuccessStep());
        }

        @Override
        public String getLine() {
            return String.format("\tAudit execution\t[%s]", getPrettyResult());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof AfterExecutionAuditSummaryLine)) return false;
            AfterExecutionAuditSummaryLine executed = (AfterExecutionAuditSummaryLine) o;
            return result == executed.result;
        }

        @Override
        public int hashCode() {
            return Objects.hash(result);
        }

    }

    public static class RolledBackSummaryLine extends StepSummaryLine {

        public RolledBackSummaryLine(RolledBackStep step) {
            super(step.getTask().getDescriptor().getId());
            setResultFromSuccess(step.isSuccessStep());
        }

        @Override
        public String getLine() {
            return String.format("\tRolled back\t\t[%s]", getPrettyResult());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RolledBackSummaryLine)) return false;
            RolledBackSummaryLine executed = (RolledBackSummaryLine) o;
            return result == executed.result;
        }

        @Override
        public int hashCode() {
            return Objects.hash(result);
        }


    }

    public static class FailedCompletedManualRollbackSummaryLine extends StepSummaryLine {

        public FailedCompletedManualRollbackSummaryLine(CompletedFailedManualRollback step) {
            super(step.getTask().getDescriptor().getId());
            setResultFromSuccess(step.isSuccessStep());
        }

        @Override
        public String getLine() {
            return String.format("\tAudit rollback\t[%s]", getPrettyResult());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof FailedCompletedManualRollbackSummaryLine)) return false;
            FailedCompletedManualRollbackSummaryLine executed = (FailedCompletedManualRollbackSummaryLine) o;
            return result == executed.result;
        }

        @Override
        public int hashCode() {
            return Objects.hash(result);
        }

    }


    public static class AlreadyAppliedSummaryLine extends StepSummaryLine {

        public AlreadyAppliedSummaryLine(CompletedAlreadyAppliedStep step) {
            super(step.getTask().getDescriptor().getId());
            this.result = SummaryResult.ALREADY_APPLIED;
        }

        @Override
        public String getLine() {
            return String.format("\tExecution\t\t[%s]", getPrettyResult());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof FailedCompletedManualRollbackSummaryLine)) return false;
            FailedCompletedManualRollbackSummaryLine executed = (FailedCompletedManualRollbackSummaryLine) o;
            return result == executed.result;
        }

        @Override
        public int hashCode() {
            return Objects.hash(result);
        }

    }
}