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

package io.flamingock.core.cloud.lock.client;

import io.flamingock.core.cloud.auth.AuthManager;
import io.flamingock.core.cloud.api.lock.LockExtensionRequest;
import io.flamingock.core.cloud.api.lock.LockResponse;
import io.flamingock.core.engine.lock.LockKey;
import io.flamingock.commons.utils.RunnerId;
import io.flamingock.commons.utils.http.Http;

public class HttpLockServiceClient implements LockServiceClient {

    private final String SERVICE_PARAM = "service";

    private final Http.RequestBuilder httpFactory;

    private final String pathTemplate;
    private final AuthManager authManager;

    public HttpLockServiceClient(String host,
                                 String apiVersion,
                                 Http.RequestBuilderFactory httpFactoryBuilder,
                                 AuthManager authManager) {
        this.pathTemplate = String.format("/api/%s/{%s}/lock", apiVersion, SERVICE_PARAM);
        this.httpFactory = httpFactoryBuilder
                .getRequestBuilder(host);
        this.authManager = authManager;
    }

    @Override
    public LockResponse extendLock(LockKey lockKey,
                                   RunnerId runnerId,
                                   LockExtensionRequest extensionRequest) {
        return httpFactory
                .POST(pathTemplate + "/extension")
                .withBearerToken(authManager.getJwtToken())
                .addPathParameter(SERVICE_PARAM, lockKey.toString())
                .withRunnerId(runnerId)
                .setBody(extensionRequest)
                .execute(LockResponse.class);
    }

    @Override
    public LockResponse getLock(LockKey lockKey) {
        return httpFactory
                .GET(pathTemplate)
                .withBearerToken(authManager.getJwtToken())
                .addPathParameter(SERVICE_PARAM, lockKey.toString())
                .execute(LockResponse.class);
    }

    @Override
    public void releaseLock(LockKey lockKey, RunnerId runnerId) {
        httpFactory
                .DELETE(pathTemplate)
                .withBearerToken(authManager.getJwtToken())
                .addPathParameter(SERVICE_PARAM, lockKey.toString())
                .withRunnerId(runnerId)
                .execute();
    }
}
