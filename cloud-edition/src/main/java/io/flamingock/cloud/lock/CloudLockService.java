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

package io.flamingock.cloud.lock;

import io.flamingock.core.cloud.api.lock.LockExtensionRequest;
import io.flamingock.core.cloud.api.lock.LockResponse;
import io.flamingock.cloud.lock.client.LockServiceClient;
import io.flamingock.core.engine.lock.LockAcquisition;
import io.flamingock.core.engine.lock.LockKey;
import io.flamingock.core.engine.lock.LockService;
import io.flamingock.core.engine.lock.LockServiceException;
import io.flamingock.commons.utils.id.RunnerId;
import io.flamingock.commons.utils.ServerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CloudLockService implements LockService {
    private static final Logger logger = LoggerFactory.getLogger(CloudLockService.class);


    private final LockServiceClient client;

    public CloudLockService(LockServiceClient client) {
        this.client = client;
    }

    @Override
    public LockAcquisition extendLock(LockKey key, RunnerId owner, long leaseMillis) throws LockServiceException {
        try {
            LockResponse lockExtension = client.extendLock(key, owner, new LockExtensionRequest(leaseMillis));
            return new LockAcquisition(RunnerId.fromString(lockExtension.getOwner()), lockExtension.getLockAcquiredForMillis());

        } catch (ServerException ex) {
            throw new LockServiceException(
                    ex.getRequestString(),
                    ex.getBodyString(),
                    String.format("Error extending lock[%s] for runner[%s]: %s", key.toString(), owner.toString(), ex.getMessage())
            );
        } catch (Throwable ex) {
            throw new LockServiceException(
                    "n/a",
                    "n/a",
                    String.format("Unexpected error extending lock[%s] for runner[%s]: %s", key.toString(), owner.toString(), ex.getMessage())
            );
        }
    }

    @Override
    public LockAcquisition getLock(LockKey lockKey) {
        try {
            LockResponse response = client.getLock(lockKey);
            return new LockAcquisition(RunnerId.fromString(response.getOwner()), response.getLockAcquiredForMillis());

        } catch (ServerException ex) {
            throw new LockServiceException(
                    ex.getRequestString(),
                    ex.getBodyString(),
                    String.format("Error retrieving lock[%s]: %s", lockKey.toString(), ex.getMessage())
            );
        } catch (Throwable ex) {
            throw new LockServiceException(
                    "n/a",
                    "n/a",
                    String.format("Unexpected error retrieving lock[%s]: %s", lockKey.toString(), ex.getMessage())
            );
        }
    }

    @Override
    public void releaseLock(LockKey lockKey, RunnerId owner) {
        try {
            client.releaseLock(lockKey, owner);

        } catch (ServerException ex) {
//            logger.warn(String.format("Error while connecting to server to release the lock[%s]: %s", lockKey.toString(), ex.getMessage()), ex);
        } catch (Throwable ex) {
//            logger.warn(String.format("Error releasing lock[%s]: %s", lockKey.toString(), ex.getMessage()), ex);
        }

    }

}
