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
package io.flamingock.common.test.cloud;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.ScenarioMappingBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.flamingock.common.test.cloud.execution.ExecutionBaseRequestResponseMock;
import io.flamingock.common.test.cloud.mock.MockExecutionPlanBuilder;
import io.flamingock.common.test.cloud.prototype.PrototypeClientSubmission;
import io.flamingock.common.test.cloud.prototype.PrototypeStage;
import io.flamingock.common.test.cloud.prototype.PrototypeChange;
import io.flamingock.cloud.api.request.TokenExchangeRequest;
import io.flamingock.cloud.api.response.TokenExchangeResponse;
import io.flamingock.cloud.api.request.StageRequest;
import io.flamingock.cloud.api.response.ExecutionPlanResponse;
import io.flamingock.cloud.api.response.StageResponse;
import io.flamingock.cloud.api.response.ChangeResponse;
import io.flamingock.internal.common.core.audit.AuditEntry;

import java.util.*;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.flamingock.common.test.cloud.utils.JsonMapper.toJson;
import static io.flamingock.cloud.api.vo.CloudChangeAction.APPLY;

public final class MockRunnerServer {

    public static final String DEFAULT_LOCK_ACQUISITION_ID = UUID.randomUUID().toString();
    public static final long DEFAULT_ACQUIRED_FOR_MILLIS = 60000L;

    private final List<ExecutionBaseRequestResponseMock> requestResponseList = new ArrayList<>();
    private final List<AuditRequestExpectation> auditEntryWriteExpectations = new ArrayList<>();
//    private final List<RequestPatternBuilder> verificableRequests = new ArrayList<>();

    private PrototypeClientSubmission clientSubmission;
    private List<AuditEntry> importerExecutionRequest = new LinkedList<>();

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
    private boolean importerCall = false;

    private static StageResponse toStageResponse(StageRequest stageRequest) {
        StageResponse stage = new StageResponse();
        stage.setName(stageRequest.getName());
        stage.setChanges(stageRequest.getChanges().stream()
                .map(onGoingChange -> new ChangeResponse(onGoingChange.getId(), APPLY))
                .collect(Collectors.toList()));
        return stage;
    }

    public MockRunnerServer setJwt(String jwt) {
        this.jwt = jwt;
        return this;
    }

    public MockRunnerServer setServerPort(int serverPort) {
        this.serverPort = serverPort;
        return this;
    }

    public MockRunnerServer setApiToken(String apiToken) {
        this.apiToken = apiToken;
        return this;
    }

    public MockRunnerServer setOrganisationId(long organisationId) {
        this.organisationId = organisationId;
        return this;
    }

    public MockRunnerServer setOrganisationName(String organisationName) {
        this.organisationName = organisationName;
        return this;
    }

    public MockRunnerServer setProjectId(long projectId) {
        this.projectId = projectId;
        return this;
    }

    public MockRunnerServer setProjectName(String projectName) {
        this.projectName = projectName;
        return this;
    }

    public MockRunnerServer setCredentialId(long credentialId) {
        this.credentialId = credentialId;
        return this;
    }

    public MockRunnerServer setServiceId(long serviceId) {
        this.serviceId = serviceId;
        return this;
    }

    public MockRunnerServer setServiceName(String serviceName) {
        this.serviceName = serviceName;
        return this;
    }

    public MockRunnerServer setEnvironmentId(long environmentId) {
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

    public MockRunnerServer withClientSubmissionBase(PrototypeClientSubmission clientSubmission) {
        this.clientSubmission = clientSubmission;
        return this;
    }

    public MockRunnerServer withExecutionPlanRequestsExpectation(ExecutionBaseRequestResponseMock firstResponse,
                                                                 ExecutionBaseRequestResponseMock... otherResponses) {
        this.requestResponseList.add(firstResponse);
        this.requestResponseList.addAll(Arrays.asList(otherResponses));
        return this;
    }

    public MockRunnerServer withAuditRequestsExpectation(AuditRequestExpectation first, AuditRequestExpectation... others) {
        auditEntryWriteExpectations.add(first);
        auditEntryWriteExpectations.addAll(Arrays.asList(others));
        return this;
    }

    public MockRunnerServer addSuccessfulImporterCall(List<AuditEntry> legacyAuditEntries) {
        importerCall = true;
        importerExecutionRequest = legacyAuditEntries;
        return this;
    }

    public MockRunnerServer addFailureImporterCall(List<AuditEntry> legacyAuditEntries) {
        importerCall = true;
        importerExecutionRequest = legacyAuditEntries;
        return this;
    }


    public void verifyAllCalls() {
        //TODO implement verification
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

        MockExecutionPlanBuilder executionPlanBuilder = new MockExecutionPlanBuilder(
                runnerId,
                String.valueOf(serviceId),
                clientSubmission);
        if (requestResponseList.size() == 1) {

            ExecutionBaseRequestResponseMock requestResponse = requestResponseList.get(0);
            ExecutionPlanResponse response = executionPlanBuilder.getResponse(requestResponse);
            String requestJson = toJson(executionPlanBuilder.getRequest(requestResponse));


            // ignoreArrayOrder=true, ignoreExtraElements=true — tolerate forward-compatible
            // additions to the wire (e.g. StageRequest.status) without breaking existing stubs.
            wireMockServer.stubFor(post(urlPathEqualTo(executionUrl)).withRequestBody(equalToJson(requestJson, true, true))
                    .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json").withBody(toJson(response))));

//            verificableRequests.add(postRequestedFor(urlEqualTo(executionUrl))
//                    .withHeader("Content-Type", equalTo("application/json"))
//                    .withRequestBody(equalToJson(toJson(requestJson))));


        } else {
            String scenarioName = "Execution-plan-request";
            for (int i = 0; i < requestResponseList.size(); i++) {

                String scenarioState = i == 0 ? Scenario.STARTED : "execution-state-" + i;


                ScenarioMappingBuilder scenarioMappingBuilder = post(urlPathEqualTo(executionUrl)).inScenario(scenarioName).whenScenarioStateIs(scenarioState);
                if (i < requestResponseList.size() - 1) {
                    scenarioMappingBuilder.willSetStateTo("execution-state-" + (i + 1));
                }
                ExecutionBaseRequestResponseMock requestResponse = requestResponseList.get(i);
                String requestJson = toJson(executionPlanBuilder.getRequest(requestResponse));
                ResponseDefinitionBuilder responseDefBuilder = aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody(toJson(executionPlanBuilder.getResponse(requestResponse)));

                wireMockServer.stubFor(scenarioMappingBuilder.withRequestBody(equalToJson(requestJson, true, true)).willReturn(responseDefBuilder));

//                verificableRequests.add(postRequestedFor(urlEqualTo(executionUrl))
//                        .withHeader("Content-Type", equalTo("application/json"))
//                        .withRequestBody(equalToJson(requestJson)));
            }
        }

    }

    private void mockAuditWriteEndpoint() {

        for (int i = 0; i < auditEntryWriteExpectations.size(); i++) {
            AuditRequestExpectation auditWrite = auditEntryWriteExpectations.get(i);
            //TODO we need to change the audit's endpoint in the server to have the auditId in the path
            //TODO should we add the stage to the url too

            String auditUrlTemplate = "/api/v1/environment/{environmentId}/service/{serviceId}/execution/{executionId}/change/{changeId}/audit"///{auditId}"
                    .replace("{environmentId}", String.valueOf(environmentId))
                    .replace("{serviceId}", String.valueOf(serviceId))
                    .replace("{executionId}", String.valueOf(auditWrite.getExecutionId()));

            PrototypeChange changePrototype = getChangePrototype(auditWrite.getChangeId());
            String auditUrl = auditUrlTemplate.replace("{auditId}", changePrototype.getChangeId());
            String requestJson = toJson(changePrototype.toAuditExpectation(auditWrite.getState()));
            if (auditEntryWriteExpectations.size() == 1) {
                wireMockServer.stubFor(
                        post(urlPathEqualTo(auditUrl.replace("{changeId}", auditWrite.getChangeId())))
                                .withRequestBody(equalToJson(requestJson, true, true))
                                .willReturn(aResponse()
                                        .withStatus(201)
                                        .withHeader("Content-Type", "application/json")
                                )
                );

//                verificableRequests.add(postRequestedFor(urlEqualTo(auditUrl))
//                        .withHeader("Content-Type", equalTo("application/json"))
//                        .withRequestBody(equalToJson(requestJson, true, true)));

            } else {
                String scenarioName = "audit-logs";
                String scenarioState = i == 0 ? Scenario.STARTED : "audit-log-state-" + i;
                wireMockServer.stubFor(
                        post(urlPathEqualTo(auditUrl.replace("{changeId}", auditWrite.getChangeId())))
                                .withName("audit-stub" + i)
                                .inScenario(scenarioName)
                                .whenScenarioStateIs(scenarioState)
                                .willSetStateTo("audit-log-state-" + (i + 1))
                                .withRequestBody(equalToJson(requestJson, true, true))
                                .willReturn(aResponse()
                                        .withStatus(201)
                                        .withHeader("Content-Type", "application/json")
                                )
                );

//                verificableRequests.add(postRequestedFor(urlEqualTo(auditUrl))
//                        .withHeader("Content-Type", equalTo("application/json"))
//                        .withRequestBody(equalToJson(requestJson, true, true)));
            }

        }

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

    private void mockReleaseLockEndpoint() {
        ResponseDefinitionBuilder responseDefBuilder = aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json");
        wireMockServer
                .stubFor(delete(urlPathEqualTo("/api/v1/{key}/lock".replace("{key}", String.valueOf(serviceId))))
                        .willReturn(responseDefBuilder));
    }

    private PrototypeChange getChangePrototype(String changeId) {
        return clientSubmission
                .getStages()
                .stream()
                .map(PrototypeStage::getChanges)
                .flatMap(List::stream)
                .filter(change -> changeId.equals(change.getChangeId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Change not found with id " + changeId));
    }


}
