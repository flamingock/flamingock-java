package io.flamingock.cloud.api.response;

import io.flamingock.cloud.api.vo.LockStatus;

public class LockResponse {

    private LockInfo lock;
    private LockStatus status;

    public LockResponse() {
    }

    public LockResponse(LockStatus status, LockInfo lock) {
        this.lock = lock;
        this.status = status;
    }

    public LockInfo getLock() {
        return lock;
    }

    public void setLock(LockInfo lock) {
        this.lock = lock;
    }

    public LockStatus getStatus() {
        return status;
    }

    public void setStatus(LockStatus status) {
        this.status = status;
    }
}
