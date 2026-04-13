package io.flamingock.cloud.api.response;

public class LogAuditEntryCreatedResponse extends LogAuditEntryResponse {

    public LogAuditEntryCreatedResponse() {
    }

    public LogAuditEntryCreatedResponse(LockInfo lock) {
        super(lock);
    }
}
