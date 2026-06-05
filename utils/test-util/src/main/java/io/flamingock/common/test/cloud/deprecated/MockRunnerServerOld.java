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
package io.flamingock.common.test.cloud.deprecated;

import io.flamingock.cloud.api.response.LockInfoResponse;
import io.flamingock.cloud.api.response.StageResponse;
import io.flamingock.cloud.api.response.ChangeResponse;
import io.flamingock.cloud.api.vo.CloudExecutionAction;
import io.flamingock.cloud.api.vo.CloudTargetSystemAuditMarkType;
import io.flamingock.internal.common.core.targets.TargetSystemAuditMarkType;
import io.flamingock.internal.common.core.audit.AuditEntry;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ScenarioMappingBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.flamingock.cloud.api.request.TokenExchangeRequest;
import io.flamingock.cloud.api.response.TokenExchangeResponse;
import io.flamingock.cloud.api.request.ExecutionPlanRequest;
import io.flamingock.cloud.api.response.ExecutionPlanResponse;
import io.flamingock.cloud.api.response.PipelineResultResponse;
import io.flamingock.cloud.api.response.StageResultResponse;
import io.flamingock.cloud.api.vo.CloudPlannerVerdict;
import io.flamingock.cloud.api.request.StageRequest; import io.flamingock.cloud.api.request.ChangeRequest;
import io.flamingock.internal.core.external.targets.mark.TargetSystemAuditMark;

import java.util.*;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.flamingock.common.test.cloud.utils.JsonMapper.toJson;
import static io.flamingock.cloud.api.vo.CloudChangeAction.APPLY;

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

    private long organisationId = 1L;
    private String organisationName = "default-organisation-name";

    private long projectId = 2L;
    private String projectName = "default-project-name";

    private long serviceId = 3L;
    private String serviceName = "default-service-name";

    private long environmentId = 4L;
    private String environmentName = "default-environment-name";

    private String runnerId = "default-runner-name";

    private String jwt = "default-jwt";

    private String apiToken = "default-api-token";

    private long credentialId = 5L;

    public MockRunnerServerOld setServerPort(int serverPort) {
        this.serverPort = serverPort;
        return this;
    }

    public MockRunnerServerOld setApiToken(String apiToken) {
        this.apiToken = apiToken;
        return this;
    }

    public MockRunnerServerOld setOrganisationId(long organisationId) {
        this.organisationId = organisationId;
        return this;
    }

    public MockRunnerServerOld setOrganisationName(String organisationName) {
        this.organisationName = organisationName;
        return this;
    }

    public MockRunnerServerOld setProjectId(long projectId) {
        this.projectId = projectId;
        return this;
    }

    public MockRunnerServerOld setProjectName(String projectName) {
        this.projectName = projectName;
        return this;
    }

    public MockRunnerServerOld setCredentialId(long credentialId) {
        this.credentialId = credentialId;
        return this;
    }

    public MockRunnerServerOld setServiceId(long serviceId) {
        this.serviceId = serviceId;
        return this;
    }

    public MockRunnerServerOld setServiceName(String serviceName) {
        this.serviceName = serviceName;
        return this;
    }

    public MockRunnerServerOld setEnvironmentId(long environmentId) {
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


    public MockRunnerServerOld addExecutionWithAllChangesRequestResponse(String executionId) {
        executionRequestResponses.add(new ExecutePlanRequestResponse(executionId, DEFAULT_ACQUIRED_FOR_MILLIS, DEFAULT_LOCK_ACQUISITION_ID));
        return this;
    }


    public MockRunnerServerOld addExecutionWithAllChangesRequestResponse(String executionId, long acquiredForMillis, String acquisitionId) {
        executionRequestResponses.add(new ExecutePlanRequestResponse(executionId, acquiredForMillis, acquisitionId));
        return this;
    }

    public MockRunnerServerOld addSimpleStageExecutionPlan(String executionId, String stageName, List<AuditEntryMatcher> auditEntries) {
        return addSimpleStageExecutionPlan(executionId, stageName, auditEntries, Collections.emptyList());
    }

    public MockRunnerServerOld addSimpleStageExecutionPlan(String executionId, String stageName, List<AuditEntryMatcher> auditEntries, List<TargetSystemAuditMark> ongoingStatuses) {

        Map<String, TargetSystemAuditMarkType> ongoingOperationByChange = ongoingStatuses.stream()
                .collect(Collectors.toMap(TargetSystemAuditMark::getChangeId, TargetSystemAuditMark::getOperation));

        Set<String> alreadyAddedChanges = new HashSet<>();
        List<ChangeRequest> changes = auditEntries.stream()
                .filter(auditEntryExpectation -> !alreadyAddedChanges.contains(auditEntryExpectation.getChangeId()))
                .map(auditEntryExpectation -> {
                    alreadyAddedChanges.add(auditEntryExpectation.getChangeId());
                    TargetSystemAuditMarkType operation = ongoingOperationByChange.get(auditEntryExpectation.getChangeId());
                    CloudTargetSystemAuditMarkType cloudStatus = operation != null
                            ? CloudTargetSystemAuditMarkType.valueOf(operation.name())
                            : CloudTargetSystemAuditMarkType.NONE;
                    return new ChangeRequest(auditEntryExpectation.getChangeId(), cloudStatus, auditEntryExpectation.isTransactional());
                })
                .collect(Collectors.toList());

        List<StageRequest> stageRequest = Collections.singletonList(new StageRequest(stageName, 0, changes));

        executionExpectation = new ExecutionExpectation(executionId, stageRequest, auditEntries, 60000, 0);
        return this;
    }

    public MockRunnerServerOld addMultipleStageExecutionPlan(String executionId, List<String> stageNames, List<AuditEntryMatcher> auditEntries) {
        return addMultipleStageExecutionPlan(executionId, stageNames, auditEntries, Collections.emptyList());
    }

    public MockRunnerServerOld addMultipleStageExecutionPlan(String executionId, List<String> stageNames, List<AuditEntryMatcher> auditEntries, List<TargetSystemAuditMark> ongoingStatuses) {

        Map<String, TargetSystemAuditMarkType> ongoingOperationByChange = ongoingStatuses.stream()
                .collect(Collectors.toMap(TargetSystemAuditMark::getChangeId, TargetSystemAuditMark::getOperation));

        Set<String> alreadyAddedChanges = new HashSet<>();
        List<ChangeRequest> changes = auditEntries.stream()
                .filter(auditEntryExpectation -> !alreadyAddedChanges.contains(auditEntryExpectation.getChangeId()))
                .map(auditEntryExpectation -> {
                    alreadyAddedChanges.add(auditEntryExpectation.getChangeId());
                    TargetSystemAuditMarkType operation = ongoingOperationByChange.get(auditEntryExpectation.getChangeId());
                    CloudTargetSystemAuditMarkType cloudStatus = operation != null
                            ? CloudTargetSystemAuditMarkType.valueOf(operation.name())
                            : CloudTargetSystemAuditMarkType.NONE;
                    return new ChangeRequest(auditEntryExpectation.getChangeId(), cloudStatus, auditEntryExpectation.isTransactional());
                })
                .collect(Collectors.toList());

        List<StageRequest> stageRequest = new ArrayList<>();
        int i = 0;
        for (String stageName : stageNames) {
            stageRequest.add(new StageRequest(stageName, i, Collections.singletonList(changes.get(i))));
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
                .replace("{environmentId}", String.valueOf(environmentId)).replace("{serviceId}", String.valueOf(serviceId));

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

        TokenExchangeRequest authRequest = new TokenExchangeRequest(apiToken, serviceName, environmentName);

        TokenExchangeResponse tokenResponse = new TokenExchangeResponse();
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
                .replace("{environmentId}", String.valueOf(environmentId)).replace("{serviceId}", String.valueOf(serviceId))
//                .replace("{elapsedMillis}", String.valueOf(executionExpectation.getElapsedMillis()))
                ;

        if (executionRequestResponses.size() == 1) {

            ExecutionPlanResponse response = getExecutionPlanResponse(0);
            ExecutionPlanRequest request = getExecutionPlanRequest(0);
            // ignoreArrayOrder=true, ignoreExtraElements=true — tolerate forward-compatible additions
            // to the wire (e.g. StageRequest.status) without breaking pre-existing stubs.
            wireMockServer.stubFor(post(urlPathEqualTo(executionUrl)).withRequestBody(equalToJson(toJson(request), true, true)).willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json").withBody(toJson(response))));

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
                wireMockServer.stubFor(scenarioMappingBuilder.withRequestBody(equalToJson(json, true, true)).willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json").withBody(toJson(response))));
            }
        }

    }


    private void mockAuditWriteEndpoint() {

        if(executionExpectation != null) {
            String executionUrl = "/api/v1/environment/{environmentId}/service/{serviceId}/execution/{executionId}/change/{changeId}/audit"
                    .replace("{environmentId}", String.valueOf(environmentId))
                    .replace("{serviceId}", String.valueOf(serviceId))
                    .replace("{executionId}", executionExpectation.getExecutionId());

            List<AuditEntryMatcher> auditEntryExpectations = executionExpectation.getAuditEntryExpectations();

            if (auditEntryExpectations.size() == 1) {

                AuditEntryMatcher request = auditEntryExpectations.get(0);
                wireMockServer.stubFor(
                        post(urlPathEqualTo(executionUrl.replace("{changeId}", request.getChangeId())))
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
                            post(urlPathEqualTo(executionUrl.replace("{changeId}", request.getChangeId())))
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
        LockInfoResponse lockResponse = new LockInfoResponse();
        lockResponse.setKey(String.valueOf(serviceId));
        lockResponse.setOwner(runnerId);
        if(executionExpectation != null) {
            lockResponse.setAcquiredForMillis(executionExpectation.getAcquiredForMillis());
        }
        lockResponse.setAcquisitionId(DEFAULT_LOCK_ACQUISITION_ID);

        String url = "/api/v1/{key}/lock".replace("{key}", String.valueOf(serviceId));
        wireMockServer.stubFor(delete(urlPathEqualTo(url)).willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(toJson(lockResponse))));
    }

    private ExecutionPlanRequest getExecutionPlanRequest(int index) {
        ExecutionPlanRequestResponse executionPlanRequestResponse = executionRequestResponses.get(index);
        List<StageRequest> stages = executionExpectation != null ? executionExpectation.getStageRequest() : Collections.emptyList();
        // Deprecated mock — wrap the flat stages in a single DEFAULT block to satisfy the new
        // wire shape. Existing deprecated-flow tests preserve behaviour; multi-block tests use
        // MockExecutionPlanBuilder / MockRunnerServer directly.
        List<io.flamingock.cloud.api.request.StageBlockRequest> blocks = Collections.singletonList(
                new io.flamingock.cloud.api.request.StageBlockRequest(io.flamingock.api.StageType.DEFAULT, stages));
        return new ExecutionPlanRequest(executionPlanRequestResponse.getAcquiredForMillis(), blocks);
    }

    private ExecutionPlanResponse getExecutionPlanResponse(int index) {
        if (executionRequestResponses.get(index) instanceof ExecutePlanRequestResponse) {
            ExecutePlanRequestResponse requestResponse = (ExecutePlanRequestResponse) executionRequestResponses.get(index);
            ExecutionPlanResponse executionPlanResponse = new ExecutionPlanResponse();
            executionPlanResponse.setExecutionId(requestResponse.executionId);
            executionPlanResponse.setAction(CloudExecutionAction.EXECUTE);

            LockInfoResponse lockMock = new LockInfoResponse();
            lockMock.setKey(String.valueOf(serviceId));
            lockMock.setOwner(runnerId);
            lockMock.setAcquiredForMillis(requestResponse.getAcquiredForMillis());
            lockMock.setAcquisitionId(requestResponse.getAcquisitionId());

            executionPlanResponse.setLock(lockMock);

            executionPlanResponse.setStages(executionExpectation.getStageRequest().stream().map(MockRunnerServerOld::toStageResponse).collect(Collectors.toList()));
            executionPlanResponse.setPipelineResult(pipelineResultFromStages(executionExpectation.getStageRequest()));
            return executionPlanResponse;
        } else if (executionRequestResponses.get(index) instanceof AwaitPlanRequestResponse) {

            AwaitPlanRequestResponse requestResponse = (AwaitPlanRequestResponse) executionRequestResponses.get(index);

            ExecutionPlanResponse executionPlanResponse = new ExecutionPlanResponse();
            executionPlanResponse.setExecutionId(requestResponse.executionId);
            executionPlanResponse.setAction(CloudExecutionAction.AWAIT);

            LockInfoResponse lock = new LockInfoResponse();
            lock.setAcquisitionId(requestResponse.getAcquisitionId());
            lock.setKey(serviceName);
            lock.setOwner(runnerId);
            lock.setAcquiredForMillis(requestResponse.getAcquiredForMillis());
            executionPlanResponse.setLock(lock);
            return executionPlanResponse;
        } else {
            //IT'S CONTINUE
            ExecutionPlanResponse executionPlanResponse = new ExecutionPlanResponse();
            executionPlanResponse.setAction(CloudExecutionAction.CONTINUE);
            // pipelineResult required by ExecutionPlanResponse.validate() on CONTINUE. Empty
            // stages list is fine for this mock — tests using it don't assert on verdict/records.
            executionPlanResponse.setPipelineResult(new PipelineResultResponse(Collections.emptyList()));
            return executionPlanResponse;
        }

    }

    /**
     * Build a minimal {@link PipelineResultResponse} that mirrors the EXECUTE stages list:
     * one {@link StageResultResponse} per stage, NEEDS_WORK verdict, no per-change records.
     * Sufficient to satisfy {@code ExecutionPlanResponse.validate()}; richer mocks should be
     * built per-test when behaviour assertions need them.
     */
    private static PipelineResultResponse pipelineResultFromStages(List<StageRequest> stages) {
        List<StageResultResponse> stageResults = stages.stream()
                .map(s -> new StageResultResponse(s.getName(), CloudPlannerVerdict.NEEDS_WORK,
                        Collections.emptyList()))
                .collect(Collectors.toList());
        return new PipelineResultResponse(stageResults);
    }

    private static StageResponse toStageResponse(StageRequest stageRequest) {
        StageResponse stage = new StageResponse();
        stage.setName(stageRequest.getName());
        stage.setChanges(stageRequest.getChanges().stream()
                .map(onGoingChange -> new ChangeResponse(onGoingChange.getId(), APPLY))
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
