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
package io.flamingock.core.cloud;

import io.flamingock.core.cloud.changes.CloudChange1;
import io.flamingock.core.cloud.changes.CloudChange2;
import io.flamingock.core.cloud.utils.TestCloudTargetSystem;
import io.flamingock.common.test.cloud.deprecated.AuditEntryMatcher;
import io.flamingock.common.test.cloud.deprecated.MockRunnerServerOld;
import io.flamingock.internal.core.builder.change.CloudChangeRunnerBuilder;
import io.flamingock.internal.core.builder.FlamingockFactory;
import io.flamingock.internal.common.cloud.audit.AuditEntryRequest;
import io.flamingock.internal.common.cloud.vo.TargetSystemAuditMarkType;
import io.flamingock.internal.core.targets.mark.TargetSystemAuditMark;
import io.flamingock.internal.core.runner.Runner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.internal.verification.Times;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@Disabled("It requires implement get All statuses from the OngoingRepo from all TargetSystems")
public class CloudTransactionTest {

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

    private MockRunnerServerOld mockRunnerServer;
    private CloudChangeRunnerBuilder flamingockBuilder;

    private static final List<AuditEntryMatcher> auditEntries = new LinkedList<>();

    @BeforeAll
    static void beforeAll() {
        auditEntries.add(new

                AuditEntryMatcher(
                "create-persons-table-from-template",
                AuditEntryRequest.Status.APPLIED,
                CloudChange1.class.getName(),
                "execution"
        ));
        auditEntries.add(new

                AuditEntryMatcher(
                "create-persons-table-from-template-2",
                AuditEntryRequest.Status.APPLIED,
                CloudChange2.class.getName(),
                "execution"
        ));
    }

    @BeforeEach
    void beforeEach() {
        mockRunnerServer = new MockRunnerServerOld()
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

        flamingockBuilder = FlamingockFactory.getCloudBuilder()
                .setApiToken(apiToken)
                .setHost("http://localhost:" + runnerServerPort)
                .setService(serviceName)
                .setEnvironment(environmentName)
        //.addStage(new Stage("changes")
//                        .setCodePackages(Collections.singletonList("io.flamingock.core.cloud.changes")))
        ;
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
                .addSimpleStageExecutionPlan(executionId, "changes", auditEntries)
                .addExecutionWithAllTasksRequestResponse(executionId)
                .addExecutionContinueRequestResponse();

        mockRunnerServer.start();

        //WHEN
        TestCloudTargetSystem cloudTargetSystem = Mockito.spy(new TestCloudTargetSystem("transactional-target-system"));

        Runner runner = flamingockBuilder
                .addTargetSystem(cloudTargetSystem)
                .build();
        runner.execute();

        //THEN
        verify(cloudTargetSystem.getOnGoingTaskStatusRepository(), new Times(2)).listAll();
        verify(cloudTargetSystem.getOnGoingTaskStatusRepository(), new Times(1)).mark(new TargetSystemAuditMark("create-persons-table-from-template", TargetSystemAuditMarkType.APPLIED));

        ArgumentCaptor<String> changeUnitIdValuesCaptor = ArgumentCaptor.forClass(String.class);
        verify(cloudTargetSystem.getOnGoingTaskStatusRepository(), new Times(1)).mark(new TargetSystemAuditMark("create-persons-table-from-template-2", TargetSystemAuditMarkType.APPLIED));
        verify(cloudTargetSystem.getOnGoingTaskStatusRepository(), new Times(2)).clearMark(changeUnitIdValuesCaptor.capture());
        List<String> allValues = changeUnitIdValuesCaptor.getAllValues();

        Assertions.assertEquals("create-persons-table-from-template", allValues.get(0));
        Assertions.assertEquals("create-persons-table-from-template-2", allValues.get(1));

//        //2 execution plans: First to execute and second to continue
//        verify(cloudMockBuilder.getRequestWithBody(), new Times(2)).execute(ExecutionPlanResponse.class);
//        //2 audit writes
//        verify(cloudMockBuilder.getRequestWithBody(), new Times(2)).execute();
//        //DELETE LOCK
//        verify(cloudMockBuilder.getBasicRequest(), new Times(1)).execute();


    }


}
