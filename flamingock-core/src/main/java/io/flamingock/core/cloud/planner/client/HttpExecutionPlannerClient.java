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

package io.flamingock.core.cloud.planner.client;

import io.flamingock.core.cloud.auth.AuthManager;
import io.flamingock.core.cloud.api.planner.ExecutionPlanRequest;
import io.flamingock.core.cloud.api.planner.ExecutionPlanResponse;
import io.flamingock.commons.utils.id.EnvironmentId;
import io.flamingock.commons.utils.id.ServiceId;
import io.flamingock.commons.utils.RunnerId;
import io.flamingock.commons.utils.http.Http;

public class HttpExecutionPlannerClient implements ExecutionPlannerClient {


    private final Http.RequestBuilder requestBuilder;

    private final String pathTemplate;

    private final AuthManager authManager;

    private final RunnerId runnerId;


    public HttpExecutionPlannerClient(String host,
                                      EnvironmentId environmentId,
                                      ServiceId serviceId,
                                      RunnerId runnerId,
                                      String apiVersion,
                                      Http.RequestBuilderFactory httpFactoryBuilder,
                                      AuthManager authManager) {
        this.runnerId = runnerId;
        this.pathTemplate = String.format("/api/%s/environment/%s/service/%s/execution",
                apiVersion,
                environmentId.toString(),
                serviceId.toString());

        this.requestBuilder = httpFactoryBuilder
                .getRequestBuilder(host);
        this.authManager = authManager;
    }

    //TODO add environment
    @Override
    public ExecutionPlanResponse createExecution(ExecutionPlanRequest request, String lastAcquisitionId, long elapsedMillis) {
        return requestBuilder
                .POST(pathTemplate)
                .withRunnerId(runnerId)
                .withBearerToken(authManager.getJwtToken())
                .addQueryParameter("lastAcquisitionId", lastAcquisitionId)
                .addQueryParameter("elapsedMillis", elapsedMillis)
                .setBody(request)
                .execute(ExecutionPlanResponse.class);
    }
}
