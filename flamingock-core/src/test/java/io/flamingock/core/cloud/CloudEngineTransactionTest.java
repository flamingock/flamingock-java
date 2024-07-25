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

import io.flamingock.core.cloud.planner.ExecutionPlanResponse;
import io.flamingock.core.cloud.transaction.OngoingStatus;
import io.flamingock.core.cloud.utils.CloudMockBuilderOld;
import io.flamingock.core.cloud.utils.TestCloudTransactioner;
import io.flamingock.core.configurator.standalone.FlamingockStandalone;
import io.flamingock.core.engine.audit.domain.AuditItem;
import io.flamingock.core.pipeline.Stage;
import io.flamingock.core.runner.Runner;
import io.flamingock.core.util.http.Http;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.internal.verification.Times;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

public class CloudEngineTransactionTest {


    @Test
    @DisplayName("Should follow the transactioner lifecycle")
    void happyPath() {
        try (MockedStatic<Http> http = Mockito.mockStatic(Http.class)) {
            CloudMockBuilderOld cloudMockBuilder = new CloudMockBuilderOld();
            cloudMockBuilder
                    .addSingleExecutionPlanResponse("stage1", "create-persons-table-from-template", "create-persons-table-from-template-2")
                    .addContinueExecutionPlanResponse()
                    .setHttp(http)
                    .mockServer();

            TestCloudTransactioner cloudTransactioner = Mockito.spy(new TestCloudTransactioner());

            Runner runner = FlamingockStandalone.cloud()
                    .setApiToken("FAKE_API_TOKEN")
                    .setHost("https://fake-cloud-server.io")
                    .setService("test-service")
                    .setEnvironment("test-environment")
                    .setCloudTransactioner(cloudTransactioner)
                    .addStage(new Stage("stage-name")
                            .setName("stage1")
                            .setCodePackages(Collections.singletonList("io.flamingock.core.cloud.changes")))
                    .build();
            runner.execute();

            //2 execution plans: First to execute and second to continue
            verify(cloudMockBuilder.getRequestWithBody(), new Times(2)).execute(ExecutionPlanResponse.class);
            //2 audit writes
            verify(cloudMockBuilder.getRequestWithBody(), new Times(2)).execute();
            //DELETE LOCK
            verify(cloudMockBuilder.getBasicRequest(), new Times(1)).execute();

            //Transaction
            verify(cloudTransactioner, new Times(2)).getOngoingStatuses();
            verify(cloudTransactioner, new Times(1)).saveOngoingStatus(new OngoingStatus("create-persons-table-from-template", AuditItem.Operation.EXECUTION));
            verify(cloudTransactioner, new Times(1)).cleanOngoingStatus("create-persons-table-from-template");

            verify(cloudTransactioner, new Times(1)).saveOngoingStatus(new OngoingStatus("create-persons-table-from-template-2", AuditItem.Operation.EXECUTION));
            verify(cloudTransactioner, new Times(1)).cleanOngoingStatus("create-persons-table-from-template-2");


        }
    }




}
