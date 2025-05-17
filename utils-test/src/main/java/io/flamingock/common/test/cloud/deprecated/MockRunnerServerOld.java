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

package io.flamingock.common.test.cloud.deprecated;

import io.flamingock.core.cloud.api.planner.response.LockResponse;
import io.flamingock.core.cloud.api.planner.response.StageResponse;
import io.flamingock.core.cloud.api.planner.response.TaskResponse;
import io.flamingock.core.cloud.api.vo.ActionResponse;
import io.flamingock.core.cloud.api.vo.OngoingStatus;
import io.flamingock.internal.core.engine.audit.writer.AuditEntry;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ScenarioMappingBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.flamingock.core.cloud.api.auth.AuthRequest;
import io.flamingock.core.cloud.api.auth.AuthResponse;
import io.flamingock.core.cloud.api.planner.request.ExecutionPlanRequest;
import io.flamingock.core.cloud.api.planner.response.ExecutionPlanResponse;
import io.flamingock.core.cloud.api.planner.request.StageRequest; import io.flamingock.core.cloud.api.planner.request.TaskRequest;
import io.flamingock.internal.core.cloud.transaction.TaskWithOngoingStatus;

import java.util.*;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.flamingock.common.test.cloud.utils.JsonMapper.toJson;
import static io.flamingock.core.cloud.api.planner.response.RequiredActionTask.PENDING_EXECUTION;

@Deprecated
public final class MockRunnerServerOld {

    public static final String DEFAULT_LOCK_ACQUISITION_ID = UUID.randomUUID().toString();

    private final List<ExecutionPlanRequestResponse> executionRequestResponses = new LinkedList<>();

    private boolean importerCall = false;

    private List<AuditEntry> importerExecutionRequest = new LinkedList<>();

    private ExecutionExpectation executionExpectation = null;

    private static final long DEFAULT_ACQUIRED_FOR_MILLIS = 60000L;
    private WireMockServer wireMockServer;

    private int serverPort = 8888;

    private String organisationId = "default-organisation-id";
    private String organisationName = "default-organisation-name";

    private String projectId = "default-project-id";
    private String projectName = "default-project-name";

    private String serviceId = "default-service-id";
    private String serviceName = "default-service-name";

    private String environmentId = "default-environment-name";
    private String environmentName = "default-environment-name";

    private String runnerId = "default-runner-name";

    private String jwt = "default-jwt";

    private String apiToken = "default-api-token";

    private String credentialId = "default-credential-id";

    public MockRunnerServerOld setServerPort(int serverPort) {
        this.serverPort = serverPort;
        return this;
    }

    public MockRunnerServerOld setApiToken(String apiToken) {
        this.apiToken = apiToken;
        return this;
    }

    public MockRunnerServerOld setOrganisationId(String organisationId) {
        this.organisationId = organisationId;
        return this;
    }

    public MockRunnerServerOld setOrganisationName(String organisationName) {
        this.organisationName = organisationName;
        return this;
    }

    public MockRunnerServerOld setProjectId(String projectId) {
        this.projectId = projectId;
        return this;
    }

    public MockRunnerServerOld setProjectName(String projectName) {
        this.projectName = projectName;
        return this;
    }

    public MockRunnerServerOld setCredentialId(String credentialId) {
        this.credentialId = credentialId;
        return this;
    }

    public MockRunnerServerOld setServiceId(String serviceId) {
        this.serviceId = serviceId;
        return this;
    }

    public MockRunnerServerOld setServiceName(String serviceName) {
        this.serviceName = serviceName;
        return this;
    }

    public MockRunnerServerOld setEnvironmentId(String environmentId) {
        this.environmentId = environmentId;
        return this;
    }

    public MockRunnerServerOld setEnvironmentName(String environmentName) {
        this.environmentName = environmentName;
        return this;
    }

    public MockRunnerServerOld setRunnerId(String runnerId) {
        this.runnerId = runnerId;
        return this;
    }

    public MockRunnerServerOld addExecutionContinueRequestResponse() {
        return addExecutionContinueRequestResponse(DEFAULT_ACQUIRED_FOR_MILLIS);
    }

    public MockRunnerServerOld addExecutionContinueRequestResponse(long acquiredForMillis) {
        executionRequestResponses.add(new ContinuePlanRequestResponse(acquiredForMillis));
        return this;
    }


    public MockRunnerServerOld addExecutionAwaitRequestResponse(String executionId) {
        return addExecutionAwaitRequestResponse(executionId, DEFAULT_ACQUIRED_FOR_MILLIS, DEFAULT_LOCK_ACQUISITION_ID);
    }

    public MockRunnerServerOld addExecutionAwaitRequestResponse(String executionId, long acquiredForMillis, String acquisitionId) {
        executionRequestResponses.add(new AwaitPlanRequestResponse(executionId, acquiredForMillis, acquisitionId));
        return this;
    }


    public MockRunnerServerOld addExecutionWithAllTasksRequestResponse(String executionId) {
        executionRequestResponses.add(new ExecutePlanRequestResponse(executionId, DEFAULT_ACQUIRED_FOR_MILLIS, DEFAULT_LOCK_ACQUISITION_ID));
        return this;
    }


    public MockRunnerServerOld addExecutionWithAllTasksRequestResponse(String executionId, long acquiredForMillis, String acquisitionId) {
        executionRequestResponses.add(new ExecutePlanRequestResponse(executionId, acquiredForMillis, acquisitionId));
        return this;
    }

    public MockRunnerServerOld addSimpleStageExecutionPlan(String executionId, String stageName, List<AuditEntryMatcher> auditEntries) {
        return addSimpleStageExecutionPlan(executionId, stageName, auditEntries, Collections.emptyList());
    }

    public MockRunnerServerOld addSimpleStageExecutionPlan(String executionId, String stageName, List<AuditEntryMatcher> auditEntries, List<TaskWithOngoingStatus> ongoingStatuses) {

        Map<String, OngoingStatus> ongoingOperationByTask = ongoingStatuses.stream()
                .collect(Collectors.toMap(TaskWithOngoingStatus::getTaskId, TaskWithOngoingStatus::getOperation));

        Set<String> alreadyAddedTasks = new HashSet<>();
        List<TaskRequest> tasks = auditEntries.stream()
                .filter(auditEntryExpectation -> !alreadyAddedTasks.contains(auditEntryExpectation.getTaskId()))
                .map(auditEntryExpectation -> {
                    alreadyAddedTasks.add(auditEntryExpectation.getTaskId());
                    OngoingStatus operation = ongoingOperationByTask.get(auditEntryExpectation.getTaskId());
                    if (operation == null) {
                        return TaskRequest.task(auditEntryExpectation.getTaskId(), auditEntryExpectation.isTransactional());
                    } else if (operation == OngoingStatus.ROLLBACK) {
                        return TaskRequest.ongoingRollback(auditEntryExpectation.getTaskId(), auditEntryExpectation.isTransactional());
                    } else {
                        return TaskRequest.ongoingExecution(auditEntryExpectation.getTaskId(), auditEntryExpectation.isTransactional());
                    }
                })
                .collect(Collectors.toList());

        List<StageRequest> stageRequest = Collections.singletonList(new StageRequest(stageName, 0, tasks));

        executionExpectation = new ExecutionExpectation(executionId, stageRequest, auditEntries, 60000, 0);
        return this;
    }

    public MockRunnerServerOld addMultipleStageExecutionPlan(String executionId, List<String> stageNames, List<AuditEntryMatcher> auditEntries) {
        return addMultipleStageExecutionPlan(executionId, stageNames, auditEntries, Collections.emptyList());
    }

    public MockRunnerServerOld addMultipleStageExecutionPlan(String executionId, List<String> stageNames, List<AuditEntryMatcher> auditEntries, List<TaskWithOngoingStatus> ongoingStatuses) {

        Map<String, OngoingStatus> ongoingOperationByTask = ongoingStatuses.stream()
                .collect(Collectors.toMap(TaskWithOngoingStatus::getTaskId, TaskWithOngoingStatus::getOperation));

        Set<String> alreadyAddedTasks = new HashSet<>();
        List<TaskRequest> tasks = auditEntries.stream()
                .filter(auditEntryExpectation -> !alreadyAddedTasks.contains(auditEntryExpectation.getTaskId()))
                .map(auditEntryExpectation -> {
                    alreadyAddedTasks.add(auditEntryExpectation.getTaskId());
                    OngoingStatus operation = ongoingOperationByTask.get(auditEntryExpectation.getTaskId());
                    if (operation == null) {
                        return TaskRequest.task(auditEntryExpectation.getTaskId(), auditEntryExpectation.isTransactional());
                    } else if (operation == OngoingStatus.ROLLBACK) {
                        return TaskRequest.ongoingRollback(auditEntryExpectation.getTaskId(), auditEntryExpectation.isTransactional());
                    } else {
                        return TaskRequest.ongoingExecution(auditEntryExpectation.getTaskId(), auditEntryExpectation.isTransactional());
                    }
                })
                .collect(Collectors.toList());

        List<StageRequest> stageRequest = new ArrayList<>();
        int i = 0;
        for (String stageName : stageNames) {
            stageRequest.add(new StageRequest(stageName, i, Collections.singletonList(tasks.get(i))));
            i++;
        }

        executionExpectation = new ExecutionExpectation(executionId, stageRequest, auditEntries, 60000, 0);
        return this;
    }

    public MockRunnerServerOld addSuccessfulImporterCall(List<AuditEntry> legacyAuditEntries) {
        importerCall = true;
        importerExecutionRequest = legacyAuditEntries;

        return this;
    }

    public MockRunnerServerOld addFailureImporterCall(List<AuditEntry> legacyAuditEntries) {
        importerCall = true;
        importerExecutionRequest = legacyAuditEntries;

        return this;
    }

    private void importerCall() {
        String executionUrl = "/api/v1/environment/{environmentId}/service/{serviceId}/execution/import"//?elapsedMillis={elapsedMillis}"
                .replace("{environmentId}", environmentId).replace("{serviceId}", serviceId);

        wireMockServer.stubFor(post(urlPathEqualTo(executionUrl))
                .withRequestBody(equalToJson(toJson(importerExecutionRequest)))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json").withBody(toJson(""))));

    }


    public MockRunnerServerOld setJwt(String jwt) {
        this.jwt = jwt;
        return this;
    }


    public void start() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(serverPort));
        wireMockServer.start();

        mockAuthEndpoint();
        mockExecutionEndpoint();
        mockAuditWriteEndpoint();
        mockReleaseLockEndpoint();

        if (importerCall) {
            this.importerCall();
        }
    }

    public void stop() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
    }

    private void mockAuthEndpoint() {

        AuthRequest authRequest = new AuthRequest(apiToken, serviceName, environmentName);

        AuthResponse tokenResponse = new AuthResponse();
        tokenResponse.setJwt(jwt);
        tokenResponse.setCredentialId(credentialId);
        tokenResponse.setOrganisationId(organisationId);
        tokenResponse.setOrganisationName(organisationName);
        tokenResponse.setProjectId(projectId);
        tokenResponse.setProjectName(projectName);
        tokenResponse.setServiceId(serviceId);
        tokenResponse.setServiceName(serviceName);
        tokenResponse.setEnvironmentId(environmentId);
        tokenResponse.setEnvironmentName(environmentName);

        wireMockServer.stubFor(post(urlPathEqualTo("/api/v1/auth/exchange-token")).withRequestBody(equalToJson(toJson(authRequest))).willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(toJson(tokenResponse))));


    }


    private void mockExecutionEndpoint() {
        String executionUrl = "/api/v1/environment/{environmentId}/service/{serviceId}/execution"//?elapsedMillis={elapsedMillis}"
                .replace("{environmentId}", environmentId).replace("{serviceId}", serviceId)
//                .replace("{elapsedMillis}", String.valueOf(executionExpectation.getElapsedMillis()))
                ;

        if (executionRequestResponses.size() == 1) {

            ExecutionPlanResponse response = getExecutionPlanResponse(0);
            ExecutionPlanRequest request = getExecutionPlanRequest(0);
            wireMockServer.stubFor(post(urlPathEqualTo(executionUrl)).withRequestBody(equalToJson(toJson(request))).willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json").withBody(toJson(response))));

        } else {
            String scenarioName = "Execution-plan-request";
            for (int i = 0; i < executionRequestResponses.size(); i++) {

                String scenarioState = i == 0 ? Scenario.STARTED : "execution-state-" + i;

                ExecutionPlanRequest request = getExecutionPlanRequest(i);
                ExecutionPlanResponse response = getExecutionPlanResponse(i);

                ScenarioMappingBuilder scenarioMappingBuilder = post(urlPathEqualTo(executionUrl)).inScenario(scenarioName).whenScenarioStateIs(scenarioState);
                if (i < executionRequestResponses.size() - 1) {
                    scenarioMappingBuilder.willSetStateTo("execution-state-" + (i + 1));
                }
                String json = toJson(request);
                wireMockServer.stubFor(scenarioMappingBuilder.withRequestBody(equalToJson(json)).willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json").withBody(toJson(response))));
            }
        }

    }


    private void mockAuditWriteEndpoint() {

        if(executionExpectation != null) {
            String executionUrl = "/api/v1/environment/{environmentId}/service/{serviceId}/execution/{executionId}/task/{taskId}/audit"
                    .replace("{environmentId}", environmentId)
                    .replace("{serviceId}", serviceId)
                    .replace("{executionId}", executionExpectation.getExecutionId());

            List<AuditEntryMatcher> auditEntryExpectations = executionExpectation.getAuditEntryExpectations();

            if (auditEntryExpectations.size() == 1) {

                AuditEntryMatcher request = auditEntryExpectations.get(0);
                wireMockServer.stubFor(
                        post(urlPathEqualTo(executionUrl.replace("{taskId}", request.getTaskId())))
                                .withRequestBody(equalToJson(toJson(request), true, true))
                                .willReturn(aResponse()
                                        .withStatus(201)
                                        .withHeader("Content-Type", "application/json")
                                )
                );

            } else {
                String scenarioName = "audit-logs";
                for (int i = 0; i < auditEntryExpectations.size(); i++) {
                    String scenarioState = i == 0 ? Scenario.STARTED : "audit-log-state-" + i;
                    AuditEntryMatcher request = auditEntryExpectations.get(i);
                    String json = toJson(request);
                    wireMockServer.stubFor(
                            post(urlPathEqualTo(executionUrl.replace("{taskId}", request.getTaskId())))
                                    .withName("audit-stub" + i)
                                    .inScenario(scenarioName)
                                    .whenScenarioStateIs(scenarioState)
                                    .willSetStateTo("audit-log-state-" + (i + 1))
                                    .withRequestBody(equalToJson(json, true, true))
                                    .willReturn(aResponse()
                                            .withStatus(201)
                                            .withHeader("Content-Type", "application/json")
                                    )
                    );
                }
            }
        }


    }

    private void mockReleaseLockEndpoint() {
        LockResponse lockResponse = new LockResponse();
        lockResponse.setKey(serviceId);
        lockResponse.setOwner(runnerId);
        if(executionExpectation != null) {
            lockResponse.setAcquiredForMillis(executionExpectation.getAcquiredForMillis());
        }
        lockResponse.setAcquisitionId(DEFAULT_LOCK_ACQUISITION_ID);

        String url = "/api/v1/{key}/lock".replace("{key}", serviceId);
        wireMockServer.stubFor(delete(urlPathEqualTo(url)).willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(toJson(lockResponse))));
    }

    private ExecutionPlanRequest getExecutionPlanRequest(int index) {
        ExecutionPlanRequestResponse executionPlanRequestResponse = executionRequestResponses.get(index);
        List<StageRequest> stages = executionExpectation != null ? executionExpectation.getStageRequest() : Collections.emptyList();
        return new ExecutionPlanRequest(executionPlanRequestResponse.getAcquiredForMillis(), stages);
    }

    private ExecutionPlanResponse getExecutionPlanResponse(int index) {
        if (executionRequestResponses.get(index) instanceof ExecutePlanRequestResponse) {
            ExecutePlanRequestResponse requestResponse = (ExecutePlanRequestResponse) executionRequestResponses.get(index);
            ExecutionPlanResponse executionPlanResponse = new ExecutionPlanResponse();
            executionPlanResponse.setExecutionId(requestResponse.executionId);
            executionPlanResponse.setAction(ActionResponse.EXECUTE);

            LockResponse lockMock = new LockResponse();
            lockMock.setKey(serviceId);
            lockMock.setOwner(runnerId);
            lockMock.setAcquiredForMillis(requestResponse.getAcquiredForMillis());
            lockMock.setAcquisitionId(requestResponse.getAcquisitionId());

            executionPlanResponse.setLock(lockMock);

            executionPlanResponse.setStages(executionExpectation.getStageRequest().stream().map(MockRunnerServerOld::toStageResponse).collect(Collectors.toList()));
            return executionPlanResponse;
        } else if (executionRequestResponses.get(index) instanceof AwaitPlanRequestResponse) {

            AwaitPlanRequestResponse requestResponse = (AwaitPlanRequestResponse) executionRequestResponses.get(index);

            ExecutionPlanResponse executionPlanResponse = new ExecutionPlanResponse();
            executionPlanResponse.setExecutionId(requestResponse.executionId);
            executionPlanResponse.setAction(ActionResponse.AWAIT);

            LockResponse lock = new LockResponse();
            lock.setAcquisitionId(requestResponse.getAcquisitionId());
            lock.setKey(serviceName);
            lock.setOwner(runnerId);
            lock.setAcquiredForMillis(requestResponse.getAcquiredForMillis());
            executionPlanResponse.setLock(lock);
            return executionPlanResponse;
        } else {
            //IT'S CONTINUE
            ExecutionPlanResponse executionPlanResponse = new ExecutionPlanResponse();
            executionPlanResponse.setAction(ActionResponse.CONTINUE);
            return executionPlanResponse;
        }

    }

    private static StageResponse toStageResponse(StageRequest stageRequest) {
        StageResponse stage = new StageResponse();
        stage.setName(stageRequest.getName());
        stage.setTasks(stageRequest.getTasks().stream()
                .map(onGoingTask -> new TaskResponse(onGoingTask.getId(), PENDING_EXECUTION))
                .collect(Collectors.toList()));
        return stage;
    }


    public static abstract class ExecutionPlanRequestResponse {

        private final long acquiredForMillis;

        ExecutionPlanRequestResponse(long acquiredForMillis) {
            this.acquiredForMillis = acquiredForMillis;
        }

        long getAcquiredForMillis() {
            return acquiredForMillis;
        }

    }

    private static class AwaitPlanRequestResponse extends ExecutionPlanRequestResponse {

        private final String executionId;
        private final String acquisitionId;

        AwaitPlanRequestResponse(String executionId, long acquiredForMillis, String acquisitionId) {
            super(acquiredForMillis);
            this.acquisitionId = acquisitionId;
            this.executionId = executionId;
        }

        public String getAcquisitionId() {
            return acquisitionId;
        }

        public String getExecutionId() {
            return executionId;
        }
    }


    private static class ExecutePlanRequestResponse extends ExecutionPlanRequestResponse {

        private final String executionId;
        private final String acquisitionId;

        ExecutePlanRequestResponse(String executionId, long acquiredForMillis, String acquisitionId) {
            super(acquiredForMillis);
            this.acquisitionId = acquisitionId;
            this.executionId = executionId;
        }

        public String getAcquisitionId() {
            return acquisitionId;
        }

        public String getExecutionId() {
            return executionId;
        }
    }

    private static class ContinuePlanRequestResponse extends ExecutionPlanRequestResponse {

        ContinuePlanRequestResponse(long acquiredForMillis) {
            super(acquiredForMillis);
        }
    }

    private static class ExecutionExpectation {
        private final String executionId;
        private final List<StageRequest> stageRequest;
        private final List<AuditEntryMatcher> auditEntryExpectations;
        private final long elapsedMillis;
        private final long acquiredForMillis;

        public ExecutionExpectation(String executionId, List<StageRequest> stageRequest, List<AuditEntryMatcher> auditEntryExpectations, long acquiredForMillis, long elapsedMillis) {
            this.executionId = executionId;
            this.stageRequest = stageRequest;
            this.auditEntryExpectations = auditEntryExpectations;
            this.acquiredForMillis = acquiredForMillis;
            this.elapsedMillis = elapsedMillis;
        }

        public String getExecutionId() {
            return executionId;
        }

        public List<AuditEntryMatcher> getAuditEntryExpectations() {
            return auditEntryExpectations;
        }

        public List<StageRequest> getStageRequest() {
            return stageRequest;
        }

        public long getElapsedMillis() {
            return elapsedMillis;
        }

        public long getAcquiredForMillis() {
            return acquiredForMillis;
        }
    }

}
