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
import io.flamingock.cloud.api.request.ExecutionPlanRequest;
import io.flamingock.cloud.api.request.StageRequest;
import io.flamingock.cloud.api.request.ChangeRequest;
import io.flamingock.cloud.api.response.ExecutionPlanResponse;
import io.flamingock.cloud.api.response.LockInfoResponse;
import io.flamingock.cloud.api.response.StageResponse;
import io.flamingock.cloud.api.response.ChangeResponse;
import io.flamingock.cloud.api.vo.CloudExecutionAction;
import io.flamingock.internal.common.core.targets.TargetSystemAuditMarkType;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.flamingock.cloud.api.vo.CloudChangeAction.APPLY;

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
                        transformChangeRequests(stagePrototype.getTasks(), requestResponse))
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
                            transformChangeResponses(stagePrototype.getTasks(), mockRequestResponse))
                    ).collect(Collectors.toList());

            LockInfoResponse lock = new LockInfoResponse();
            lock.setAcquisitionId(mockRequestResponse.getAcquisitionId());
            lock.setKey(serviceId);
            lock.setOwner(runnerId);
            return new ExecutionPlanResponse(CloudExecutionAction.EXECUTE, executionId, lock, stages);

        } else if (mockRequestResponse instanceof ExecutionAwaitRequestResponseMock) {
            LockInfoResponse lock = new LockInfoResponse();
            lock.setAcquisitionId(mockRequestResponse.getAcquisitionId());
            lock.setKey(serviceId);
            lock.setOwner(runnerId);
            lock.setAcquiredForMillis(mockRequestResponse.getAcquiredForMillis());
            return new ExecutionPlanResponse(CloudExecutionAction.AWAIT, executionId, lock);
        } else {
            //IT'S CONTINUE
            ExecutionPlanResponse executionPlanResponse = new ExecutionPlanResponse();
            executionPlanResponse.setAction(CloudExecutionAction.CONTINUE);
            return executionPlanResponse;
        }

    }

    private List<ChangeRequest> transformChangeRequests(List<PrototypeTask> prototypeTasks,
                                                    ExecutionBaseRequestResponseMock requestResponse) {
        return prototypeTasks.stream()
                .map(prototypeTask -> {
                            Optional<MockRequestResponseTask> requestResponseTask = requestResponse.getTaskById(prototypeTask.getTaskId());
                            return prototypeTask.toExecutionPlanChangeRequest(
                                    requestResponseTask.map(MockRequestResponseTask::getOngoingStatus).orElse(TargetSystemAuditMarkType.NONE));
                        }
                ).collect(Collectors.toList());
    }

    private List<ChangeResponse> transformChangeResponses(List<PrototypeTask> prototypeTasks,
                                                      ExecutionBaseRequestResponseMock responseExecutionPlan) {
        return prototypeTasks.stream()
                .map(prototypeTask -> {
                            Optional<MockRequestResponseTask> requestResponseTask = responseExecutionPlan.getTaskById(prototypeTask.getTaskId());
                            return prototypeTask.toExecutionPlanChangeResponse(
                                    requestResponseTask.map(MockRequestResponseTask::getRequiredAction).orElse(APPLY));
                        }
                ).collect(Collectors.toList());
    }



}
