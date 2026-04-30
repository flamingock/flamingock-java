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
