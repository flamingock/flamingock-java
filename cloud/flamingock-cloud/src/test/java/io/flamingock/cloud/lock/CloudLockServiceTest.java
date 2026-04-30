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
package io.flamingock.cloud.lock;

import io.flamingock.cloud.api.response.LockInfoResponse;
import io.flamingock.cloud.lock.client.LockServiceClient;
import io.flamingock.internal.core.external.store.lock.LockAcquisition;
import io.flamingock.internal.core.external.store.lock.LockKey;
import io.flamingock.internal.core.external.store.lock.LockServiceException;
import io.flamingock.internal.util.FlamingockError;
import io.flamingock.internal.util.ServerException;
import io.flamingock.internal.util.id.RunnerId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CloudLockService}, the adapter that wraps {@link LockServiceClient}
 * to expose the core {@code LockService} contract. Verifies success paths, error mapping
 * to {@link LockServiceException}, the silent-swallow behaviour of {@code releaseLock},
 * and the explicit {@code UnsupportedOperationException} for {@code getLockInfo}.
 */
class CloudLockServiceTest {

    private static final long IGNORED_LEASE_MILLIS = 60_000L;

    private LockServiceClient client;
    private CloudLockService service;
    private final LockKey lockKey = LockKey.fromString("service-1");
    private final RunnerId owner = RunnerId.fromString("runner-A");

    @BeforeEach
    void setUp() {
        client = mock(LockServiceClient.class);
        service = new CloudLockService(client);
    }

    @Test
    @DisplayName("extendLock: returns LockAcquisition derived from the response's inner lock")
    void extendLockHappyPath() {
        LockInfoResponse response = lockInfo(owner.toString(), 45_000L);
        when(client.extendLock(eq(lockKey), eq(owner))).thenReturn(response);

        LockAcquisition acquisition = service.extendLock(lockKey, owner, IGNORED_LEASE_MILLIS);

        assertEquals(owner, acquisition.getOwner());
        assertEquals(45_000L, acquisition.getAcquiredForMillis());
        verify(client, times(1)).extendLock(lockKey, owner);
    }

    @Test
    @DisplayName("extendLock: maps ServerException to LockServiceException and preserves request/body")
    void extendLockMapsServerException() {
        ServerException serverException = new ServerException(
                "POST http://server/lock/extension",
                "{\"some\":\"body\"}",
                new FlamingockError("R_LOCK_01", true, "Lock is acquired by other process"));
        when(client.extendLock(eq(lockKey), eq(owner))).thenThrow(serverException);

        LockServiceException ex = assertThrows(
                LockServiceException.class,
                () -> service.extendLock(lockKey, owner, IGNORED_LEASE_MILLIS));

        assertEquals("POST http://server/lock/extension", ex.getAcquireLockQuery());
        assertEquals("{\"some\":\"body\"}", ex.getNewLockEntity());
        assertTrue(ex.getErrorDetail().contains("Error extending lock"));
        assertTrue(ex.getErrorDetail().contains("runner-A"));
    }

    @Test
    @DisplayName("extendLock: maps unexpected throwable to LockServiceException with n/a request/body")
    void extendLockMapsUnexpectedThrowable() {
        when(client.extendLock(eq(lockKey), eq(owner))).thenThrow(new RuntimeException("boom"));

        LockServiceException ex = assertThrows(
                LockServiceException.class,
                () -> service.extendLock(lockKey, owner, IGNORED_LEASE_MILLIS));

        assertEquals("n/a", ex.getAcquireLockQuery());
        assertEquals("n/a", ex.getNewLockEntity());
        assertTrue(ex.getErrorDetail().contains("Unexpected error extending lock"));
    }

    @Test
    @DisplayName("getLockInfo: throws UnsupportedOperationException because cloud has no read-only endpoint")
    void getLockInfoThrowsUnsupported() {
        UnsupportedOperationException ex = assertThrows(
                UnsupportedOperationException.class,
                () -> service.getLockInfo(lockKey));

        assertTrue(ex.getMessage().contains("getLockInfo"));
        assertTrue(ex.getMessage().contains("cloud"));
    }

    @Test
    @DisplayName("releaseLock: swallows ServerException so that release stays best-effort")
    void releaseLockSwallowsServerException() {
        ServerException serverException = new ServerException(
                "DELETE http://server/lock",
                null,
                new FlamingockError("R_LOCK_02", true, "Lock cannot be deleted"));
        doThrow(serverException).when(client).releaseLock(eq(lockKey), eq(owner));

        service.releaseLock(lockKey, owner); // must not throw

        verify(client, times(1)).releaseLock(lockKey, owner);
    }

    @Test
    @DisplayName("releaseLock: swallows unexpected throwable as well")
    void releaseLockSwallowsUnexpectedThrowable() {
        doThrow(new RuntimeException("boom")).when(client).releaseLock(eq(lockKey), eq(owner));

        service.releaseLock(lockKey, owner); // must not throw

        verify(client, times(1)).releaseLock(lockKey, owner);
    }

    @Test
    @DisplayName("releaseLock: happy path delegates to the client and returns")
    void releaseLockHappyPath() {
        service.releaseLock(lockKey, owner);

        verify(client, times(1)).releaseLock(lockKey, owner);
        verify(client, never()).extendLock(any(), any());
    }

    private static LockInfoResponse lockInfo(String ownerId, long acquiredForMillis) {
        LockInfoResponse r = new LockInfoResponse();
        r.setKey("service-1");
        r.setOwner(ownerId);
        r.setAcquisitionId(ownerId + "-uuid");
        r.setAcquiredForMillis(acquiredForMillis);
        return r;
    }
}
