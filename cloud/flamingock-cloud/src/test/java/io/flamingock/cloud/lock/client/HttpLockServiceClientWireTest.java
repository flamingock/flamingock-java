/*
 * Copyright 2026 Flamingock (https://www.flamingock.io)
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

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.flamingock.cloud.api.response.LockInfoResponse;
import io.flamingock.cloud.auth.AuthManager;
import io.flamingock.internal.core.external.store.lock.LockKey;
import io.flamingock.internal.util.ServerException;
import io.flamingock.internal.util.http.Http;
import io.flamingock.internal.util.id.RunnerId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Wire-contract tests for {@link HttpLockServiceClient}.
 *
 * <p>Verifies that the client speaks the protocol defined in
 * {@code cloud/flamingock-cloud/docs/lock-api-endpoints.md} for the two
 * client-invoked endpoints — extend and release. (Acquire is implicit in
 * the execution-plan response and is not invoked through this client.)</p>
 */
class HttpLockServiceClientWireTest {

    private static final String JWT = "test-jwt-token";
    private static final String LOCK_KEY = "service-1";
    private static final String OWNER = "runner-A";
    private static final String API_VERSION = "v1";
    private static final String EXTENSION_PATH = "/api/v1/" + LOCK_KEY + "/lock/extension";
    private static final String LOCK_PATH = "/api/v1/" + LOCK_KEY + "/lock";

    private WireMockServer server;
    private HttpLockServiceClient client;
    private final LockKey lockKey = LockKey.fromString(LOCK_KEY);
    private final RunnerId runnerId = RunnerId.fromString(OWNER);

    @BeforeEach
    void setUp() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();

        AuthManager authManager = mock(AuthManager.class);
        when(authManager.getJwtToken()).thenReturn(JWT);

        client = new HttpLockServiceClient(
                "http://localhost:" + server.port(),
                API_VERSION,
                Http.DEFAULT_INSTANCE,
                authManager
        );
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    @DisplayName("extendLock: POSTs to /lock/extension with auth + runner-id headers and parses LockResponse wrapper")
    void extendLockHappyPath() {
        server.stubFor(post(urlPathEqualTo(EXTENSION_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"EXTENDED\","
                                + "\"lock\":{\"key\":\"" + LOCK_KEY + "\","
                                + "\"owner\":\"" + OWNER + "\","
                                + "\"acquisitionId\":\"" + OWNER + "-9b8e2c4a\","
                                + "\"acquiredForMillis\":30000}}")));

        LockInfoResponse response = client.extendLock(lockKey, runnerId);

        assertNotNull(response);
        assertEquals(LOCK_KEY, response.getKey());
        assertEquals(OWNER, response.getOwner());
        assertEquals(OWNER + "-9b8e2c4a", response.getAcquisitionId());
        assertEquals(30000L, response.getAcquiredForMillis());

        server.verify(postRequestedFor(urlPathEqualTo(EXTENSION_PATH))
                .withHeader("Authorization", equalTo("Bearer " + JWT))
                .withHeader("flamingock-runner-id", equalTo(OWNER)));
    }

    @Test
    @DisplayName("extendLock: throws when server returns a non-EXTENDED status (contract drift guard)")
    void extendLockRejectsNonExtendedStatus() {
        server.stubFor(post(urlPathEqualTo(EXTENSION_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"ACQUIRED\","
                                + "\"lock\":{\"key\":\"" + LOCK_KEY + "\","
                                + "\"owner\":\"" + OWNER + "\","
                                + "\"acquisitionId\":\"" + OWNER + "-x\","
                                + "\"acquiredForMillis\":30000}}")));

        assertThrows(IllegalStateException.class, () -> client.extendLock(lockKey, runnerId));
    }

    @Test
    @DisplayName("extendLock: throws when the response body has a null lock")
    void extendLockRejectsNullLock() {
        server.stubFor(post(urlPathEqualTo(EXTENSION_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"EXTENDED\",\"lock\":null}")));

        assertThrows(IllegalStateException.class, () -> client.extendLock(lockKey, runnerId));
    }

    @Test
    @DisplayName("extendLock: surfaces R_LOCK_01 conflict from server as ServerException")
    void extendLockConflictRLock01() {
        server.stubFor(post(urlPathEqualTo(EXTENSION_PATH))
                .willReturn(aResponse()
                        .withStatus(409)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":\"R_LOCK_01\","
                                + "\"message\":\"Lock is acquired by other process\","
                                + "\"recoverable\":true,\"details\":[]}")));

        ServerException ex = assertThrows(ServerException.class, () -> client.extendLock(lockKey, runnerId));
        assertNotNull(ex.getError());
        assertEquals("R_LOCK_01", ex.getError().getCode());
    }

    @Test
    @DisplayName("extendLock: surfaces R_GEN_01 missing-runner-id error from server as ServerException")
    void extendLockBadRequestRGen01() {
        server.stubFor(post(urlPathEqualTo(EXTENSION_PATH))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":\"R_GEN_01\","
                                + "\"message\":\"flamingock-runner-id header missing\","
                                + "\"recoverable\":true,\"details\":[]}")));

        ServerException ex = assertThrows(ServerException.class, () -> client.extendLock(lockKey, runnerId));
        assertEquals("R_GEN_01", ex.getError().getCode());
    }

    @Test
    @DisplayName("releaseLock: DELETEs /lock with auth + runner-id headers and ignores response body")
    void releaseLockHappyPath() {
        server.stubFor(delete(urlPathEqualTo(LOCK_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"key\":\"" + LOCK_KEY + "\","
                                + "\"owner\":\"" + OWNER + "\","
                                + "\"acquisitionId\":\"" + OWNER + "-x\","
                                + "\"acquiredForMillis\":30000}")));

        client.releaseLock(lockKey, runnerId);

        server.verify(deleteRequestedFor(urlPathEqualTo(LOCK_PATH))
                .withHeader("Authorization", equalTo("Bearer " + JWT))
                .withHeader("flamingock-runner-id", equalTo(OWNER))
                .withRequestBody(absent()));
    }

    @Test
    @DisplayName("releaseLock: surfaces R_LOCK_02 conflict from server as ServerException")
    void releaseLockConflictRLock02() {
        server.stubFor(delete(urlPathEqualTo(LOCK_PATH))
                .willReturn(aResponse()
                        .withStatus(409)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":\"R_LOCK_02\","
                                + "\"message\":\"Lock cannot be deleted because it's acquired by other process\","
                                + "\"recoverable\":true,\"details\":[]}")));

        ServerException ex = assertThrows(ServerException.class, () -> client.releaseLock(lockKey, runnerId));
        assertEquals("R_LOCK_02", ex.getError().getCode());
    }
}
