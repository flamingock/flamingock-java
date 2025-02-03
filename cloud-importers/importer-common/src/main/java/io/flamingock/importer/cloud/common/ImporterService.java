/*
 * Copyright 2023 Flamingock (https://oss.flamingock.io)
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

package io.flamingock.importer.cloud.common;

import io.flamingock.commons.utils.JsonObjectMapper;
import io.flamingock.commons.utils.RunnerId;
import io.flamingock.commons.utils.http.Http;
import org.apache.http.impl.client.HttpClients;

import java.util.List;

public class ImporterService {

    private final Http.RequestBuilderFactory requestBuilderFactory = Http.DEFAULT_INSTANCE;

    private final String environmentId;
    private final String serviceId;
    private final String jwt;
    private final Http.RequestBuilder requestBuilder;

    public ImporterService(String serverHost, String environmentId, String serviceId, String jwt) {
        this.environmentId = environmentId;
        this.serviceId = serviceId;
        this.jwt = jwt;
        //Instance HttpClient
        Http.RequestBuilderFactory requestBuilderFactory = Http.builderFactory(HttpClients.createDefault(), JsonObjectMapper.DEFAULT_INSTANCE);
        this.requestBuilder = requestBuilderFactory.getRequestBuilder(serverHost);
    }

    public void send(List<MongockLegacyAuditEntry> data) {

        String pathTemplate = String.format(
                "/api/v1/environment/%s/service/%s/execution/import",
                environmentId,
                serviceId
        );

        RunnerId runnerId = RunnerId.generate();

        requestBuilder
                .POST(pathTemplate)
                .withRunnerId(runnerId)
                .withBearerToken(jwt)
                .setBody(data)
                .execute();
    }
}
