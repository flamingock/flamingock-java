package io.flamingock.cloud.api.response;

public abstract class LogAuditEntryResponse {

    private LockInfo lock;

    public LogAuditEntryResponse() {
    }

    public LogAuditEntryResponse(LockInfo lock) {
        this.lock = lock;
    }

    public LockInfo getLock() {
        return lock;
    }

    public void setLock(LockInfo lock) {
        this.lock = lock;
    }
}
