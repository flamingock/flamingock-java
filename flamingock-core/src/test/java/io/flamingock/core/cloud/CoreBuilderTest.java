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

import io.flamingock.common.test.cloud.deprecated.MockRunnerServerOld;
import io.flamingock.core.api.exception.FlamingockException;
import io.flamingock.core.configurator.standalone.FlamingockStandalone;
import io.flamingock.core.pipeline.Stage;
import io.flamingock.core.runner.Runner;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.UUID;


public class CoreBuilderTest {
    private static MockRunnerServerOld mockRunnerServer;

    @BeforeAll
    public static void beforeAll() {
        mockRunnerServer = new MockRunnerServerOld()
                .setServerPort(8888)
                .setOrganisationId(UUID.randomUUID().toString())
                .setOrganisationName("MyOrganisation")
                .setProjectId(UUID.randomUUID().toString())
                .setProjectName("MyOrganisation")
                .setServiceId("clients-service-id")
                .setServiceName("clients-service")
                .setEnvironmentId("development-env-id")
                .setEnvironmentName("development")
                .setCredentialId(UUID.randomUUID().toString())
                .setApiToken("FAKE_API_TOKEN")
                .setJwt("fake_jwt")
                .addExecutionContinueRequestResponse();

        mockRunnerServer.start();
    }

    @AfterAll
    public static void afterAll() {
        mockRunnerServer.stop();
    }

    @Test
    @DisplayName("Should throw an exception when the only stage is empty")
    void shouldThrowExceptionWhenTheOnlyStageEmpty() {

        Runner runner = FlamingockStandalone.cloud()
                .setApiToken("FAKE_API_TOKEN")
                .setHost("http://localhost:8888")
                .setService("clients-service")
                .setEnvironment("development")
                .addStage(new Stage("failing-stage-1")
                        .setCodePackages(Collections.singletonList("io.flamingock.wrong.package")))
                .build();
        FlamingockException exception = Assertions.assertThrows(FlamingockException.class, runner::execute);

        Assertions.assertEquals("There are empty stages: failing-stage-1", exception.getMessage());

    }

    @Test
    @DisplayName("Should throw an exception when all stages are empty")
    void shouldThrowExceptionWhenAllPackagesEmpty() {

        Runner runner = FlamingockStandalone.cloud()
                .setApiToken("FAKE_API_TOKEN")
                .setHost("http://localhost:8888")
                .setService("clients-service")
                .setEnvironment("development")
                .addStage(new Stage("failing-stage-1")
                        .setCodePackages(Collections.singletonList("io.flamingock.wrong.package-1")))
                .addStage(new Stage("failing-stage-2")
                        .setCodePackages(Collections.singletonList("io.flamingock.wrong.package-2")))
                .build();
        FlamingockException exception = Assertions.assertThrows(FlamingockException.class, runner::execute);

        Assertions.assertEquals("There are empty stages: failing-stage-1,failing-stage-2", exception.getMessage());

    }

    @Test
    @DisplayName("Should throw an exception when all stages are empty")
    void shouldThrowExceptionWhenAtLeastOnePackagesEmpty() {

        Runner runner = FlamingockStandalone.cloud()
                .setApiToken("FAKE_API_TOKEN")
                .setHost("http://localhost:8888")
                .setService("clients-service")
                .setEnvironment("development")
                .addStage(new Stage("failing-stage-1")
                        .setCodePackages(Collections.singletonList("io.flamingock.wrong.package-1")))
                .addStage(new Stage("success-stage")
                        .setCodePackages(Collections.singletonList("io.flamingock.core.cloud.changes")))
                .addStage(new Stage("failing-stage-2")
                        .setCodePackages(Collections.singletonList("io.flamingock.wrong.package-2")))
                .build();
        FlamingockException exception = Assertions.assertThrows(FlamingockException.class, runner::execute);

        Assertions.assertEquals("There are empty stages: failing-stage-1,failing-stage-2", exception.getMessage());

    }


}
