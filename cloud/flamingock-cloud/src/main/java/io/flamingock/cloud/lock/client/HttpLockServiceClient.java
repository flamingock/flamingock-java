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
package io.flamingock.cloud.lock.client;

import io.flamingock.cloud.auth.AuthManager;
import io.flamingock.cloud.api.response.LockInfoResponse;
import io.flamingock.cloud.api.response.LockResponse;
import io.flamingock.cloud.api.vo.CloudLockStatus;
import io.flamingock.internal.core.external.store.lock.LockKey;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.internal.util.http.Http;

import java.util.Collections;

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
    public LockInfoResponse extendLock(LockKey lockKey, RunnerId runnerId) {
        LockResponse response = httpFactory
                .POST(pathTemplate + "/extension")
                .withBearerToken(authManager.getJwtToken())
                .addPathParameter(SERVICE_PARAM, lockKey.toString())
                .withRunnerId(runnerId)
                .setBody(Collections.emptyMap())
                .execute(LockResponse.class);

        if (response == null || response.getStatus() != CloudLockStatus.EXTENDED || response.getLock() == null) {
            throw new IllegalStateException(String.format(
                    "Lock extension contract violation: expected status[%s] with non-null lock, got[%s]",
                    CloudLockStatus.EXTENDED, response));
        }
        return response.getLock();
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
