package io.flamingock.cloud.api.response;

public class LogAuditEntryAlreadyCreatedResponse extends LogAuditEntryResponse {

    public LogAuditEntryAlreadyCreatedResponse() {
    }

    public LogAuditEntryAlreadyCreatedResponse(LockInfo lock) {
        super(lock);
    }
}
