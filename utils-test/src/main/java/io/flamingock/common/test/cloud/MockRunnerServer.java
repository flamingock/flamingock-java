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

package io.flamingock.common.test.cloud;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ScenarioMappingBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.flamingock.core.cloud.api.auth.AuthRequest;
import io.flamingock.core.cloud.api.auth.AuthResponse;
import io.flamingock.core.cloud.api.planner.ExecutionPlanRequest;
import io.flamingock.core.cloud.api.planner.ExecutionPlanResponse;
import io.flamingock.core.cloud.api.planner.StageRequest;
import io.flamingock.core.cloud.api.transaction.OngoingStatus;

import java.util.*;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.flamingock.common.test.cloud.JsonMapper.toJson;
import static io.flamingock.core.cloud.api.planner.ExecutionPlanResponse.TaskState.PENDING_EXECUTION;

public final class MockRunnerServer {

    public static final String DEFAULT_LOCK_ACQUISITION_ID = UUID.randomUUID().toString();

    private final List<ExecutionPlanRequestResponse> executionRequestResponses = new LinkedList<>();

    private boolean importerCall = false;

    private List<MongockLegacyAuditEntry> importerExecutionRequest = new LinkedList<>();

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

    public MockRunnerServer setServerPort(int serverPort) {
        this.serverPort = serverPort;
        return this;
    }

    public MockRunnerServer setApiToken(String apiToken) {
        this.apiToken = apiToken;
        return this;
    }

    public MockRunnerServer setOrganisationId(String organisationId) {
        this.organisationId = organisationId;
        return this;
    }

    public MockRunnerServer setOrganisationName(String organisationName) {
        this.organisationName = organisationName;
        return this;
    }

    public MockRunnerServer setProjectId(String projectId) {
        this.projectId = projectId;
        return this;
    }

    public MockRunnerServer setProjectName(String projectName) {
        this.projectName = projectName;
        return this;
    }

    public MockRunnerServer setCredentialId(String credentialId) {
        this.credentialId = credentialId;
        return this;
    }

    public MockRunnerServer setServiceId(String serviceId) {
        this.serviceId = serviceId;
        return this;
    }

    public MockRunnerServer setServiceName(String serviceName) {
        this.serviceName = serviceName;
        return this;
    }

    public MockRunnerServer setEnvironmentId(String environmentId) {
        this.environmentId = environmentId;
        return this;
    }

    public MockRunnerServer setEnvironmentName(String environmentName) {
        this.environmentName = environmentName;
        return this;
    }

    public MockRunnerServer setRunnerId(String runnerId) {
        this.runnerId = runnerId;
        return this;
    }

    public MockRunnerServer addExecutionContinueRequestResponse() {
        return addExecutionContinueRequestResponse(DEFAULT_ACQUIRED_FOR_MILLIS);
    }

    public MockRunnerServer addExecutionContinueRequestResponse(long acquiredForMillis) {
        executionRequestResponses.add(new ContinuePlanRequestResponse(acquiredForMillis));
        return this;
    }


    public MockRunnerServer addExecutionAwaitRequestResponse(String executionId) {
        return addExecutionAwaitRequestResponse(executionId, DEFAULT_ACQUIRED_FOR_MILLIS, DEFAULT_LOCK_ACQUISITION_ID);
    }

    public MockRunnerServer addExecutionAwaitRequestResponse(String executionId, long acquiredForMillis, String acquisitionId) {
        executionRequestResponses.add(new AwaitPlanRequestResponse(executionId, acquiredForMillis, acquisitionId));
        return this;
    }


    public MockRunnerServer addExecutionWithAllTasksRequestResponse(String executionId) {
        executionRequestResponses.add(new ExecutePlanRequestResponse(executionId, DEFAULT_ACQUIRED_FOR_MILLIS, DEFAULT_LOCK_ACQUISITION_ID));
        return this;
    }


    public MockRunnerServer addExecutionWithAllTasksRequestResponse(String executionId, long acquiredForMillis, String acquisitionId) {
        executionRequestResponses.add(new ExecutePlanRequestResponse(executionId, acquiredForMillis, acquisitionId));
        return this;
    }

    public MockRunnerServer addSimpleStageExecutionPlan(String executionId, String stageName, List<AuditEntryExpectation> auditEntries) {
        return addSimpleStageExecutionPlan(executionId, stageName, auditEntries, Collections.emptyList());
    }

    public MockRunnerServer addSimpleStageExecutionPlan(String executionId, String stageName, List<AuditEntryExpectation> auditEntries, List<OngoingStatus> ongoingStatuses) {

        Map<String, OngoingStatus.Operation> ongoingOperationByTask = ongoingStatuses.stream()
                .collect(Collectors.toMap(OngoingStatus::getTaskId, OngoingStatus::getOperation));

        List<StageRequest.Task> tasks = auditEntries.stream()
                .map(AuditEntryExpectation::getTaskId)
                .collect(Collectors.toSet())
                .stream()
                .map(taskId -> {
                    OngoingStatus.Operation operation = ongoingOperationByTask.get(taskId);
                    if (operation == null) {
                        return StageRequest.Task.task(taskId, true);
                    } else if (operation == OngoingStatus.Operation.ROLLBACK) {
                        return StageRequest.Task.ongoingRollback(taskId, true);
                    } else {
                        return StageRequest.Task.ongoingExecution(taskId, true);
                    }
                })
                .collect(Collectors.toList());

        StageRequest stageRequest = new StageRequest(stageName, 0, tasks);

        executionExpectation = new ExecutionExpectation(executionId, stageRequest, auditEntries, 60000, 0);
        return this;
    }

    public MockRunnerServer addSuccessfulImporterCall(List<MongockLegacyAuditEntry> legacyAuditEntries) {
        importerCall = true;
        importerExecutionRequest = legacyAuditEntries;

        return this;
    }

    public MockRunnerServer addFailureImporterCall(List<MongockLegacyAuditEntry> legacyAuditEntries) {
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


    public MockRunnerServer setJwt(String jwt) {
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
            String executionUrl = "/api/v1/environment/{environmentId}/service/{serviceId}/execution/{executionId}/audit".replace("{environmentId}", environmentId).replace("{serviceId}", serviceId).replace("{executionId}", executionExpectation.getExecutionId());

            List<AuditEntryExpectation> auditEntryExpectations = executionExpectation.getAuditEntryExpectations();

            if (auditEntryExpectations.size() == 1) {

                AuditEntryExpectation request = auditEntryExpectations.get(0);
                wireMockServer.stubFor(
                        post(urlPathEqualTo(executionUrl))
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
                    AuditEntryExpectation request = auditEntryExpectations.get(i);
                    String json = toJson(request);
                    wireMockServer.stubFor(
                            post(urlPathEqualTo(executionUrl))
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
        ExecutionPlanResponse.Lock lockResponse = new ExecutionPlanResponse.Lock();
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
        List<StageRequest> stages = executionExpectation != null ? Collections.singletonList(executionExpectation.getStageRequest()) : Collections.emptyList();
        return new ExecutionPlanRequest(executionPlanRequestResponse.getAcquiredForMillis(), stages);
    }

    private ExecutionPlanResponse getExecutionPlanResponse(int index) {
        if (executionRequestResponses.get(index) instanceof ExecutePlanRequestResponse) {
            ExecutePlanRequestResponse requestResponse = (ExecutePlanRequestResponse) executionRequestResponses.get(index);
            ExecutionPlanResponse executionPlanResponse = new ExecutionPlanResponse();
            executionPlanResponse.setExecutionId(requestResponse.executionId);
            executionPlanResponse.setAction(ExecutionPlanResponse.Action.EXECUTE);

            ExecutionPlanResponse.Lock lockMock = new ExecutionPlanResponse.Lock();
            lockMock.setKey(serviceId);
            lockMock.setOwner(runnerId);
            lockMock.setAcquiredForMillis(requestResponse.getAcquiredForMillis());
            lockMock.setAcquisitionId(requestResponse.getAcquisitionId());

            executionPlanResponse.setLock(lockMock);

            executionPlanResponse.setStages(Collections.singletonList(toStageResponse(executionExpectation.getStageRequest())));
            return executionPlanResponse;
        } else if (executionRequestResponses.get(index) instanceof AwaitPlanRequestResponse) {

            AwaitPlanRequestResponse requestResponse = (AwaitPlanRequestResponse) executionRequestResponses.get(index);

            ExecutionPlanResponse executionPlanResponse = new ExecutionPlanResponse();
            executionPlanResponse.setExecutionId(requestResponse.executionId);
            executionPlanResponse.setAction(ExecutionPlanResponse.Action.AWAIT);

            ExecutionPlanResponse.Lock lock = new ExecutionPlanResponse.Lock();
            lock.setAcquisitionId(requestResponse.getAcquisitionId());
            lock.setKey(serviceName);
            lock.setOwner(runnerId);
            lock.setAcquiredForMillis(requestResponse.getAcquiredForMillis());
            executionPlanResponse.setLock(lock);
            return executionPlanResponse;
        } else {
            //IT'S CONTINUE
            ExecutionPlanResponse executionPlanResponse = new ExecutionPlanResponse();
            executionPlanResponse.setAction(ExecutionPlanResponse.Action.CONTINUE);
            return executionPlanResponse;
        }

    }

    private static ExecutionPlanResponse.Stage toStageResponse(StageRequest stageRequest) {
        ExecutionPlanResponse.Stage stage = new ExecutionPlanResponse.Stage();
        stage.setName(stageRequest.getName());
        stage.setTasks(stageRequest.getTasks().stream()
                .map(onGoingTask -> new ExecutionPlanResponse.Task(onGoingTask.getId(), PENDING_EXECUTION))
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
        private final StageRequest stageRequest;
        private final List<AuditEntryExpectation> auditEntryExpectations;
        private final long elapsedMillis;
        private final long acquiredForMillis;

        public ExecutionExpectation(String executionId, StageRequest stageRequest, List<AuditEntryExpectation> auditEntryExpectations, long acquiredForMillis, long elapsedMillis) {
            this.executionId = executionId;
            this.stageRequest = stageRequest;
            this.auditEntryExpectations = auditEntryExpectations;
            this.acquiredForMillis = acquiredForMillis;
            this.elapsedMillis = elapsedMillis;
        }

        public String getExecutionId() {
            return executionId;
        }

        public List<AuditEntryExpectation> getAuditEntryExpectations() {
            return auditEntryExpectations;
        }

        public StageRequest getStageRequest() {
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
