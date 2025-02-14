/*
 * Copyright 2023 Flamingock ("https://oss.flamingock.io")
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.flamingock.core.cloud.api.planner.response;

import io.flamingock.core.cloud.api.vo.ActionResponse;

import java.util.Collections;
import java.util.List;

public class ExecutionPlanResponse {


    private ActionResponse action;

    private String executionId;

    private LockResponse lock;

    private List<StageResponse> stages;


    public ExecutionPlanResponse() {
    }

    public ExecutionPlanResponse(ActionResponse action,
                                 String executionId,
                                 LockResponse lock) {
        this(action, executionId, lock, Collections.emptyList());
    }

    public ExecutionPlanResponse(ActionResponse action,
                                 String executionId,
                                 LockResponse lock,
                                 List<StageResponse> stages) {
        this.action = action;
        this.executionId = executionId;
        this.lock = lock;
        this.stages = stages;
    }

    public void setAction(ActionResponse action) {
        this.action = action;
    }

    public String getExecutionId() {
        return executionId;
    }

    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    public LockResponse getLock() {
        return lock;
    }

    public void setLock(LockResponse lock) {
        this.lock = lock;
    }

    public List<StageResponse> getStages() {
        return stages;
    }

    public void setStages(List<StageResponse> stages) {
        this.stages = stages;
    }

    public boolean isContinue() {
        return action == ActionResponse.CONTINUE;
    }

    public ActionResponse getAction() {
        return action;
    }

    public boolean isExecute() {
        return action == ActionResponse.EXECUTE;
    }

    public boolean isAwait() {
        return action == ActionResponse.AWAIT;
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
    }

}
