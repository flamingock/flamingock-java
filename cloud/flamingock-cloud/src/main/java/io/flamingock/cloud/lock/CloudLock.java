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
import io.flamingock.internal.core.configuration.core.CoreConfigurable;
import io.flamingock.internal.core.external.store.lock.Lock;
import io.flamingock.internal.core.external.store.lock.LockKey;
import io.flamingock.internal.core.external.store.lock.LockService;
import io.flamingock.internal.util.TimeService;
import io.flamingock.internal.util.id.RunnerId;

public class CloudLock extends Lock {


    private CloudLock(RunnerId owner,
                      LockKey lockKey,
                      long leaseMillis,
                      long stopTryingAfterMillis,
                      long retryFrequencyMillis,
                      LockService lockService,
                      TimeService timeService,
                      boolean refreshDaemonEnabled) {
        super(owner, lockKey, leaseMillis, stopTryingAfterMillis, retryFrequencyMillis, lockService, timeService, refreshDaemonEnabled);
        updateLease(leaseMillis);
    }

    public static CloudLock initialiseLocal(LockInfoResponse lockInfo,
                                            CoreConfigurable coreConfiguration,
                                            RunnerId owner,
                                            CloudLockService lockService,
                                            TimeService timeService) {

        CloudLock cloudLock = new CloudLock(
                owner,
                LockKey.fromString(lockInfo.getKey()),
                lockInfo.getAcquiredForMillis(),
                coreConfiguration.getLockQuitTryingAfterMillis(),
                coreConfiguration.getLockTryFrequencyMillis(),
                lockService,
                timeService,
                coreConfiguration.isEnableRefreshDaemon()
        );
        cloudLock.startDaemonIfEnabled();
        return cloudLock;
    }


}
