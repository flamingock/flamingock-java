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
package io.flamingock.common.test.cloud.mock;

import io.flamingock.common.test.cloud.prototype.PrototypeClientSubmission;
import io.flamingock.common.test.cloud.prototype.PrototypeTask;
import io.flamingock.common.test.cloud.execution.ExecutionAwaitRequestResponseMock;
import io.flamingock.common.test.cloud.execution.ExecutionBaseRequestResponseMock;
import io.flamingock.common.test.cloud.execution.ExecutionPlanRequestResponseMock;
import io.flamingock.internal.common.cloud.planner.request.ExecutionPlanRequest;
import io.flamingock.internal.common.cloud.planner.request.StageRequest;
import io.flamingock.internal.common.cloud.planner.request.TaskRequest;
import io.flamingock.internal.common.cloud.planner.response.ExecutionPlanResponse;
import io.flamingock.internal.common.cloud.planner.response.LockResponse;
import io.flamingock.internal.common.cloud.planner.response.StageResponse;
import io.flamingock.internal.common.cloud.planner.response.TaskResponse;
import io.flamingock.internal.common.cloud.vo.ActionResponse;
import io.flamingock.internal.common.cloud.vo.TargetSystemAuditMarkType;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.flamingock.internal.common.cloud.planner.response.RequiredActionTask.PENDING_EXECUTION;

public class MockExecutionPlanBuilder {

    private final PrototypeClientSubmission clientSubmission;
    private final String runnerId;
    private final String serviceId;

    public MockExecutionPlanBuilder(String runnerId,
                                    String serviceId,
                                    PrototypeClientSubmission clientSubmission) {
        this.runnerId = runnerId;
        this.serviceId = serviceId;
        this.clientSubmission = clientSubmission;
    }


    public ExecutionPlanRequest getRequest(ExecutionBaseRequestResponseMock requestResponse ) {
        List<StageRequest> stages = clientSubmission
                .getStages()
                .stream()
                .map(stagePrototype -> new StageRequest(
                        stagePrototype.getName(),
                        stagePrototype.getOrder(),
                        transformTaskRequests(stagePrototype.getTasks(), requestResponse))
                ).collect(Collectors.toList());

        return new ExecutionPlanRequest(requestResponse.getAcquiredForMillis(), stages);
    }

    public ExecutionPlanResponse getResponse(ExecutionBaseRequestResponseMock mockRequestResponse) {
        String executionId = mockRequestResponse.getExecutionId();
        if (mockRequestResponse instanceof ExecutionPlanRequestResponseMock) {
            List<StageResponse> stages = clientSubmission
                    .getStages()
                    .stream()
                    .map(stagePrototype -> new StageResponse(
                            stagePrototype.getName(),
                            stagePrototype.getOrder(),
                            transformTaskResponses(stagePrototype.getTasks(), mockRequestResponse))
                    ).collect(Collectors.toList());

            LockResponse lock = new LockResponse();
            lock.setAcquisitionId(mockRequestResponse.getAcquisitionId());
            lock.setKey(serviceId);
            lock.setOwner(runnerId);
            return new ExecutionPlanResponse(ActionResponse.EXECUTE, executionId, lock, stages);

        } else if (mockRequestResponse instanceof ExecutionAwaitRequestResponseMock) {
            LockResponse lock = new LockResponse();
            lock.setAcquisitionId(mockRequestResponse.getAcquisitionId());
            lock.setKey(serviceId);
            lock.setOwner(runnerId);
            lock.setAcquiredForMillis(mockRequestResponse.getAcquiredForMillis());
            return new ExecutionPlanResponse(ActionResponse.AWAIT, executionId, lock);
        } else {
            //IT'S CONTINUE
            ExecutionPlanResponse executionPlanResponse = new ExecutionPlanResponse();
            executionPlanResponse.setAction(ActionResponse.CONTINUE);
            return executionPlanResponse;
        }

    }

    private List<TaskRequest> transformTaskRequests(List<PrototypeTask> prototypeTasks,
                                                    ExecutionBaseRequestResponseMock requestResponse) {
        return prototypeTasks.stream()
                .map(prototypeTask -> {
                            Optional<MockRequestResponseTask> requestResponseTask = requestResponse.getTaskById(prototypeTask.getTaskId());
                            return prototypeTask.toExecutionPlanTaskRequest(
                                    requestResponseTask.map(MockRequestResponseTask::getOngoingStatus).orElse(TargetSystemAuditMarkType.NONE));
                        }
                ).collect(Collectors.toList());
    }

    private List<TaskResponse> transformTaskResponses(List<PrototypeTask> prototypeTasks,
                                                      ExecutionBaseRequestResponseMock responseExecutionPlan) {
        return prototypeTasks.stream()
                .map(prototypeTask -> {
                            Optional<MockRequestResponseTask> requestResponseTask = responseExecutionPlan.getTaskById(prototypeTask.getTaskId());
                            return prototypeTask.toExecutionPlanTaskResponse(
                                    requestResponseTask.map(MockRequestResponseTask::getRequiredAction).orElse(PENDING_EXECUTION));
                        }
                ).collect(Collectors.toList());
    }



}
