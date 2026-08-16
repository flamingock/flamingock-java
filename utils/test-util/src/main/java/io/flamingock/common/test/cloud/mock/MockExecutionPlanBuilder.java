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
import io.flamingock.common.test.cloud.prototype.PrototypeChange;
import io.flamingock.common.test.cloud.execution.ExecutionAwaitRequestResponseMock;
import io.flamingock.common.test.cloud.execution.ExecutionBaseRequestResponseMock;
import io.flamingock.common.test.cloud.execution.ExecutionPlanRequestResponseMock;
import io.flamingock.api.StageType;
import io.flamingock.cloud.api.request.ExecutionPlanRequest;
import io.flamingock.cloud.api.request.StageBlockRequest;
import io.flamingock.cloud.api.request.StageRequest;
import io.flamingock.cloud.api.request.ChangeRequest;
import io.flamingock.cloud.api.response.ExecutionPlanResponse;
import io.flamingock.cloud.api.response.LockInfoResponse;
import io.flamingock.cloud.api.response.PipelineResultResponse;
import io.flamingock.cloud.api.response.StageResponse;
import io.flamingock.cloud.api.response.StageResultResponse;
import io.flamingock.cloud.api.response.ChangeResponse;
import io.flamingock.cloud.api.vo.CloudExecutionAction;
import io.flamingock.cloud.api.vo.CloudPlannerVerdict;

import java.util.Collections;
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
                        transformChangeRequests(stagePrototype.getChanges(), requestResponse))
                ).collect(Collectors.toList());

        // Wrap the prototype's flat stages into a single DEFAULT block. Existing tests that
        // don't model block structure preserve their behaviour; tests that need multi-block
        // scenarios should construct StageBlockRequest lists directly.
        List<StageBlockRequest> blocks = java.util.Collections.singletonList(
                new StageBlockRequest(StageType.DEFAULT, stages));
        return new ExecutionPlanRequest(requestResponse.getAcquiredForMillis(), blocks);
    }

    public ExecutionPlanResponse getResponse(ExecutionBaseRequestResponseMock mockRequestResponse) {
        Long executionId = mockRequestResponse.getExecutionId();
        if (mockRequestResponse instanceof ExecutionPlanRequestResponseMock) {
            List<StageResponse> stages = clientSubmission
                    .getStages()
                    .stream()
                    .map(stagePrototype -> new StageResponse(
                            stagePrototype.getName(),
                            stagePrototype.getOrder(),
                            transformChangeResponses(stagePrototype.getChanges(), mockRequestResponse))
                    ).collect(Collectors.toList());

            LockInfoResponse lock = new LockInfoResponse();
            lock.setAcquisitionId(mockRequestResponse.getAcquisitionId());
            lock.setKey(serviceId);
            lock.setOwner(runnerId);
            ExecutionPlanResponse executePlanResponse = new ExecutionPlanResponse(
                    CloudExecutionAction.EXECUTE, executionId, lock, stages);
            // pipelineResult required by ExecutionPlanResponse.validate() on EXECUTE. Minimal
            // shape: one NEEDS_WORK entry per stage with no per-change records — tests using
            // this builder don't assert on verdict/records; richer mocks should be built when
            // behaviour assertions need them.
            executePlanResponse.setPipelineResult(pipelineResultFromStages(stages));
            return executePlanResponse;

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
            // pipelineResult required by validate() on CONTINUE — empty stages list is fine
            // for this builder; tests don't assert on verdict/records.
            executionPlanResponse.setPipelineResult(new PipelineResultResponse(Collections.emptyList()));
            return executionPlanResponse;
        }

    }

    /**
     * Minimal pipelineResult mirroring the EXECUTE stages: one {@link StageResultResponse}
     * per stage, NEEDS_WORK verdict, no per-change records. Satisfies
     * {@link ExecutionPlanResponse#validate()} without forcing every test to think about the
     * result side.
     */
    private static PipelineResultResponse pipelineResultFromStages(List<StageResponse> stages) {
        List<StageResultResponse> stageResults = stages.stream()
                .map(s -> new StageResultResponse(s.getName(), CloudPlannerVerdict.NEEDS_WORK,
                        Collections.emptyList()))
                .collect(Collectors.toList());
        return new PipelineResultResponse(stageResults);
    }

    private List<ChangeRequest> transformChangeRequests(List<PrototypeChange> prototypeChanges,
                                                    ExecutionBaseRequestResponseMock requestResponse) {
        return prototypeChanges.stream()
                .map(prototypeChange -> {
                            Optional<MockRequestResponseChange> requestResponseChange = requestResponse.getChangeById(prototypeChange.getChangeId());
                            return prototypeChange.toExecutionPlanChangeRequest(
                                    requestResponseChange.map(MockRequestResponseChange::getOngoingStatus).orElse(TargetSystemAuditMarkType.NONE));
                        }
                ).collect(Collectors.toList());
    }

    private List<ChangeResponse> transformChangeResponses(List<PrototypeChange> prototypeChanges,
                                                      ExecutionBaseRequestResponseMock responseExecutionPlan) {
        return prototypeChanges.stream()
                .map(prototypeChange -> {
                            Optional<MockRequestResponseChange> requestResponseChange = responseExecutionPlan.getChangeById(prototypeChange.getChangeId());
                            return prototypeChange.toExecutionPlanChangeResponse(
                                    requestResponseChange.map(MockRequestResponseChange::getRequiredAction).orElse(APPLY));
                        }
                ).collect(Collectors.toList());
    }



}
