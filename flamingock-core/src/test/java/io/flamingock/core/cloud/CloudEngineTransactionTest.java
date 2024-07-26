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

package io.flamingock.core.cloud;

import io.flamingock.common.test.cloud.AuditEntryExpectation;
import io.flamingock.common.test.cloud.MockRunnerServer;
import io.flamingock.core.cloud.api.audit.AuditEntryRequest;
import io.flamingock.core.cloud.changes.CloudChange1;
import io.flamingock.core.cloud.changes.CloudChange2;
import io.flamingock.core.cloud.api.transaction.OngoingStatus;
import io.flamingock.core.cloud.utils.TestCloudTransactioner;
import io.flamingock.core.configurator.standalone.FlamingockStandalone;
import io.flamingock.core.configurator.standalone.StandaloneCloudBuilder;
import io.flamingock.core.pipeline.Stage;
import io.flamingock.core.runner.Runner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.internal.verification.Times;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;

public class CloudEngineTransactionTest {

    private final String apiToken = "FAKE_API_TOKEN";
    private final String organisationId = UUID.randomUUID().toString();
    private final String organisationName = "MyOrganisation";

    private final String projectId = UUID.randomUUID().toString();
    private final String projectName = "MyOrganisation";

    private final String serviceName = "clients-service";
    private final String environmentName = "development";
    private final String serviceId = "clients-service-id";
    private final String environmentId = "development-env-id";
    private final String credentialId = UUID.randomUUID().toString();
    private final int runnerServerPort = 8888;
    private final String jwt = "fake_jwt";

    private MockRunnerServer mockRunnerServer;
    private StandaloneCloudBuilder flamingockBuilder;

    private static final List<AuditEntryExpectation> auditEntries = new LinkedList<>();

    @BeforeAll
    static void beforeAll() {
        auditEntries.add(new

                AuditEntryExpectation(
                "create-persons-table-from-template",
                AuditEntryRequest.Status.EXECUTED,
                CloudChange1.class.getName(),
                "execution"
        ));
        auditEntries.add(new

                AuditEntryExpectation(
                "create-persons-table-from-template-2",
                AuditEntryRequest.Status.EXECUTED,
                CloudChange2.class.getName(),
                "execution"
        ));
    }

    @BeforeEach
    void beforeEach() {
        mockRunnerServer = new MockRunnerServer()
                .setServerPort(runnerServerPort)
                .setOrganisationId(organisationId)
                .setOrganisationName(organisationName)
                .setProjectId(projectId)
                .setProjectName(projectName)
                .setServiceId(serviceId)
                .setServiceName(serviceName)
                .setEnvironmentId(environmentId)
                .setEnvironmentName(environmentName)
                .setCredentialId(credentialId)
                .setApiToken(apiToken)
                .setJwt(jwt);

        flamingockBuilder = FlamingockStandalone.cloud()
                .setApiToken(apiToken)
                .setHost("http://localhost:" + runnerServerPort)
                .setService(serviceName)
                .setEnvironment(environmentName)
                .addStage(new Stage("stage-1")
                        .setCodePackages(Collections.singletonList("io.flamingock.core.cloud.changes")));
    }

    @AfterEach
    void afterEach() {

        //tear down
        mockRunnerServer.stop();
    }


    @Test
    @DisplayName("Should run successfully happy path")
    void happyPath() {
        //GIVEN
        String executionId = "execution-1";
        mockRunnerServer
                .addSimpleStageExecutionPlan(executionId, "stage-1", auditEntries)
                .addExecutionWithAllTasksRequestResponse(executionId)
                .addExecutionContinueRequestResponse();

        mockRunnerServer.start();

        //WHEN
        TestCloudTransactioner cloudTransactioner = Mockito.spy(new TestCloudTransactioner());

        Runner runner = flamingockBuilder
                .setCloudTransactioner(cloudTransactioner)
                .build();
        runner.execute();

        //THEN
        verify(cloudTransactioner, new Times(2)).getOngoingStatuses();
        verify(cloudTransactioner, new Times(1)).saveOngoingStatus(new OngoingStatus("create-persons-table-from-template", OngoingStatus.Operation.EXECUTION));
        verify(cloudTransactioner, new Times(1)).cleanOngoingStatus("create-persons-table-from-template");

        verify(cloudTransactioner, new Times(1)).saveOngoingStatus(new OngoingStatus("create-persons-table-from-template-2", OngoingStatus.Operation.EXECUTION));
        verify(cloudTransactioner, new Times(1)).cleanOngoingStatus("create-persons-table-from-template-2");

//        //2 execution plans: First to execute and second to continue
//        verify(cloudMockBuilder.getRequestWithBody(), new Times(2)).execute(ExecutionPlanResponse.class);
//        //2 audit writes
//        verify(cloudMockBuilder.getRequestWithBody(), new Times(2)).execute();
//        //DELETE LOCK
//        verify(cloudMockBuilder.getBasicRequest(), new Times(1)).execute();


    }


}
