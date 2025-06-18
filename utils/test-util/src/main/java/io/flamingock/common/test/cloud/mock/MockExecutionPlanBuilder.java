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
import io.flamingock.internal.common.cloud.vo.OngoingStatus;

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
                                    requestResponseTask.map(MockRequestResponseTask::getOngoingStatus).orElse(OngoingStatus.NONE));
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
