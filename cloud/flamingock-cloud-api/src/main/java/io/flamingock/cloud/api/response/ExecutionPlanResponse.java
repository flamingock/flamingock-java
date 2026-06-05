/*
 * Copyright 2023 Flamingock (https://www.flamingock.io)
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
package io.flamingock.cloud.api.response;

import io.flamingock.cloud.api.vo.CloudExecutionAction;

import java.util.Collections;
import java.util.List;

public class ExecutionPlanResponse {

    private String executionId;

    private CloudExecutionAction action;

    private LockInfoResponse lock;

    /**
     * Operation side: the stages the executor should act on this round. Sparse — only
     * stages with actionable changes appear here.
     */
    private List<StageResponse> stages;

    /**
     * Result side: server's synthesised per-stage verdict + per-change status for the
     * <strong>entire</strong> submitted pipeline (not just stages with work). The client
     * iterates this uniformly to feed {@code PipelineRun.markStageVerdict} and
     * {@code PipelineRun.markStageAlreadyAppliedFromAudit}. Required on {@code EXECUTE}
     * and {@code CONTINUE} responses; absent on {@code AWAIT} / {@code ABORT}.
     */
    private PipelineResultResponse pipelineResult;

    private boolean synchronizedMarks;


    public ExecutionPlanResponse() {
    }

    public ExecutionPlanResponse(CloudExecutionAction action,
                                 String executionId,
                                 LockInfoResponse lock) {
        this(action, executionId, lock, Collections.emptyList());
    }

    public ExecutionPlanResponse(CloudExecutionAction action,
                                 String executionId,
                                 LockInfoResponse lock,
                                 List<StageResponse> stages) {
        this(action, executionId, lock, stages, false);
    }

    public ExecutionPlanResponse(CloudExecutionAction action,
                                 String executionId,
                                 LockInfoResponse lock,
                                 List<StageResponse> stages,
                                 boolean synchronizedMarks) {
        this(action, executionId, lock, stages, null, synchronizedMarks);
    }

    public ExecutionPlanResponse(CloudExecutionAction action,
                                 String executionId,
                                 LockInfoResponse lock,
                                 List<StageResponse> stages,
                                 PipelineResultResponse pipelineResult,
                                 boolean synchronizedMarks) {
        this.action = action;
        this.executionId = executionId;
        this.lock = lock;
        this.stages = stages;
        this.pipelineResult = pipelineResult;
        this.synchronizedMarks = synchronizedMarks;
    }

    public void setAction(CloudExecutionAction action) {
        this.action = action;
    }

    public String getExecutionId() {
        return executionId;
    }

    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    public LockInfoResponse getLock() {
        return lock;
    }

    public void setLock(LockInfoResponse lock) {
        this.lock = lock;
    }

    public List<StageResponse> getStages() {
        return stages;
    }

    public void setStages(List<StageResponse> stages) {
        this.stages = stages;
    }

    public PipelineResultResponse getPipelineResult() {
        return pipelineResult;
    }

    public void setPipelineResult(PipelineResultResponse pipelineResult) {
        this.pipelineResult = pipelineResult;
    }

    public boolean isContinue() {
        return action == CloudExecutionAction.CONTINUE;
    }

    public CloudExecutionAction getAction() {
        return action;
    }

    public boolean isExecute() {
        return action == CloudExecutionAction.EXECUTE;
    }

    public boolean isAwait() {
        return action == CloudExecutionAction.AWAIT;
    }

    public boolean isAbort() {
        return action == CloudExecutionAction.ABORT;
    }

    public boolean isSynchronizedMarks() {
        return synchronizedMarks;
    }

    public void setSynchronizedMarks(boolean synchronizedMarks) {
        this.synchronizedMarks = synchronizedMarks;
    }

    public void validate() {
        if (isExecute() && executionId == null) {
            throw new RuntimeException("ExecutionPlan must contain a valid executionId");
        }
        if (isExecute() && getStages() == null) {
            throw new RuntimeException("ExecutionPlan is execute, but not body returned");
        }

        if (isAwait() && getLock() == null) {
            throw new RuntimeException("ExecutionPlan is await, but not lock information returned");
        }

        // pipelineResult is required on EXECUTE and CONTINUE so the client can write
        // per-stage verdict and per-change records uniformly. AWAIT / ABORT carry none.
        if ((isExecute() || isContinue()) && pipelineResult == null) {
            throw new RuntimeException(
                    "ExecutionPlan is " + action
                            + ", but no pipelineResult returned — the server must populate the result side"
                            + " so the client can write verdict and per-change status into PipelineRun.");
        }
    }

}
