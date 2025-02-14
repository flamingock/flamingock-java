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

import io.flamingock.commons.utils.ThreadSleeper;
import io.flamingock.core.api.exception.FlamingockException;
import io.flamingock.core.cloud.api.audit.AuditEntryRequest;
import io.flamingock.core.cloud.changes.CloudChange1;
import io.flamingock.core.cloud.changes.CloudChange2;
import io.flamingock.common.test.cloud.deprecated.AuditEntryMatcher;
import io.flamingock.common.test.cloud.deprecated.MockRunnerServerOld;
import io.flamingock.core.configurator.standalone.FlamingockStandalone;
import io.flamingock.core.configurator.standalone.StandaloneCloudBuilder;
import io.flamingock.core.engine.lock.LockException;
import io.flamingock.core.pipeline.Stage;
import io.flamingock.core.runner.Runner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.mockito.internal.verification.Times;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;


//TODO add listener to check final Summary
//TODO verify calls to server
public class CloudEngineTest {

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
    private StandaloneCloudBuilder flamingockBuilder;

    private static final List<AuditEntryMatcher> auditEntryExpectations = new LinkedList<>();

    @BeforeAll
    static void beforeAll() {
        auditEntryExpectations.add(new

                AuditEntryMatcher(
                "create-persons-table-from-template",
                AuditEntryRequest.Status.EXECUTED,
                CloudChange1.class.getName(),
                "execution"
        ));
        auditEntryExpectations.add(new

                AuditEntryMatcher(
                "create-persons-table-from-template-2",
                AuditEntryRequest.Status.EXECUTED,
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


    //TODO add listener to check final Summary
    @Test
    @DisplayName("Should run successfully happy path")
    void happyPath() {
        //GIVEN
        String executionId = "execution-1";
        mockRunnerServer
                .addSimpleStageExecutionPlan(executionId, "stage-1", auditEntryExpectations)
                .addExecutionWithAllTasksRequestResponse(executionId)
                .addExecutionContinueRequestResponse();

        mockRunnerServer.start();

        //WHEN
        //THEN
        Runner runner = flamingockBuilder
                .build();
        runner.execute();

    }

    @Test
    @DisplayName("Should perform the right calls to server when sequence: AWAIT, EXECUTE, CONTINUE")
    void shouldPerformRightCallsWhenAwaitExecuteContinue() {
        //GIVEN
        try (MockedConstruction<ThreadSleeper> lockThreadSleeperConstructors = Mockito.mockConstruction(ThreadSleeper.class)) {


            String executionId = "execution-1";
            mockRunnerServer
                    .addSimpleStageExecutionPlan(executionId, "stage-1", auditEntryExpectations)
                    .addExecutionAwaitRequestResponse(executionId)
                    .addExecutionWithAllTasksRequestResponse(executionId)
                    .addExecutionContinueRequestResponse()
                    .start();

            //WHEN
            Runner runner = flamingockBuilder
                    .build();
            runner.execute();

            //THEN
            //check second times it doesn't wait
            ThreadSleeper secondThreadSleeper = lockThreadSleeperConstructors.constructed().get(1);
            verify(secondThreadSleeper, new Times(0)).checkThresholdAndWait(anyLong());

            //TODO add listener to check final Summary
        }

    }

    @Test
    @DisplayName("Should wait and abort when the lock is blocked and the ThreadSleeper eventually throws an exception")
    void shouldWaitAndEventuallyAbort() {
        ///GIVEN
        String acquisitionId = UUID.randomUUID().toString();
        String executionId = "execution-1";
        mockRunnerServer
                .addSimpleStageExecutionPlan(executionId, "stage-1", auditEntryExpectations)
                .addExecutionAwaitRequestResponse(executionId, 5000L, acquisitionId)
                .addExecutionAwaitRequestResponse(executionId, 5000L, acquisitionId)
                .addExecutionAwaitRequestResponse(executionId, 5000L, acquisitionId)
                .start();

        //WHEN
        Runner runner = flamingockBuilder
                .setLockAcquiredForMillis(5000L)
                .setLockTryFrequencyMillis(175L)
                .setLockQuitTryingAfterMillis(375L)
                .build();
        FlamingockException exception = Assertions.assertThrows(FlamingockException.class, runner::execute);


        //THEN
        Assertions.assertTrue(exception instanceof LockException);
        Assertions.assertTrue(exception.getMessage().startsWith("Quit trying to acquire the lock after"));
        Assertions.assertTrue(exception.getMessage().endsWith("[ Maximum waiting millis reached: 375 ]"));
    }


    //TODO add elaspsedMillis and lastAcquisitionId expectation to server
    @Test
    @DisplayName("Should wait and retry when lock is initially blocked eventually released")
    void shouldWaitAndRetry() {
        //GIVEN
        String executionId = "execution-1";
        String acquisitionId = UUID.randomUUID().toString();
        mockRunnerServer
                .addSimpleStageExecutionPlan(executionId, "stage-1", auditEntryExpectations)
                .addExecutionAwaitRequestResponse(executionId, 5000L, acquisitionId)
                .addExecutionAwaitRequestResponse(executionId, 5000L, acquisitionId)
                .addExecutionContinueRequestResponse(5000L)
                .start();

        //WHEN
        Runner runner = flamingockBuilder
                .setLockAcquiredForMillis(5000L)
                .setLockTryFrequencyMillis(3000L)
                .setLockQuitTryingAfterMillis(9000L)
                .build();

        runner.execute();

    }


    @Test
    @DisplayName("Should continue and not run anything if server returns CONTINUE at first")
    void shouldContinue() {
        mockRunnerServer
                .addSimpleStageExecutionPlan("execution-1", "stage-1", auditEntryExpectations)
                .addExecutionContinueRequestResponse()
                .start();

        //WHEN
        Runner runner = flamingockBuilder
                .build();

        runner.execute();
    }


}
