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
package io.flamingock.cloud.lock;

import io.flamingock.cloud.api.response.LockInfoResponse;
import io.flamingock.cloud.lock.client.LockServiceClient;
import io.flamingock.internal.core.external.store.lock.LockAcquisition;
import io.flamingock.internal.core.external.store.lock.LockKey;
import io.flamingock.internal.core.external.store.lock.LockService;
import io.flamingock.internal.core.external.store.lock.LockServiceException;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.internal.util.ServerException;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;

public class CloudLockService implements LockService {
    private static final Logger logger = FlamingockLoggerFactory.getLogger("CloudLock");

    private final LockServiceClient client;

    public CloudLockService(LockServiceClient client) {
        this.client = client;
    }

    /**
     * On cloud, the lease duration is fixed at acquire time and the server echoes it back; the
     * {@code leaseMillis} parameter from the {@link LockService} contract is unused here.
     */
    @Override
    public LockAcquisition extendLock(LockKey key, RunnerId owner, long leaseMillis) throws LockServiceException {
        try {
            LockInfoResponse lockExtension = client.extendLock(key, owner);
            return new LockAcquisition(RunnerId.fromString(lockExtension.getOwner()), lockExtension.getAcquiredForMillis());

        } catch (ServerException ex) {
            throw new LockServiceException(
                    ex.getRequestString(),
                    ex.getBodyString(),
                    String.format("Error extending lock[%s] for runner[%s]: %s", key, owner, ex.getMessage())
            );
        } catch (Throwable ex) {
            throw new LockServiceException(
                    "n/a",
                    "n/a",
                    String.format("Unexpected error extending lock[%s] for runner[%s]: %s", key, owner, ex.getMessage())
            );
        }
    }

    /**
     * Not supported on cloud: the lock REST API exposes no read-only "get current lock state"
     * endpoint. The Java client never needs to read lock state out-of-band — acquisition is
     * materialised from the execution-plan response, and extend / release are owner-asserted
     * server-side. Throws to surface any unexpected caller loudly.
     */
    @Override
    public LockAcquisition getLockInfo(LockKey lockKey) {
        throw new UnsupportedOperationException(
                "getLockInfo is not supported on cloud: the lock REST API has no read-only endpoint");
    }

    @Override
    public void releaseLock(LockKey lockKey, RunnerId owner) {
        try {
            client.releaseLock(lockKey, owner);
        } catch (ServerException ex) {
            logger.warn("Server rejected lock[{}] release for runner[{}]: {}", lockKey, owner, ex.getMessage());
        } catch (Throwable ex) {
            logger.warn("Unexpected error releasing lock[{}] for runner[{}]: {}", lockKey, owner, ex.getMessage(), ex);
        }
    }
}
