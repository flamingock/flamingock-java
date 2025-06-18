package io.flamingock.internal.core.community;

public final class Constants {

    public static final String DEFAULT_AUDIT_STORE_NAME = "flamingockAuditLogs";
    public static final String AUDIT_LOG_PK = "partitionKey";
    public static final String AUDIT_LOG_STAGE_ID = "stageId";

    public static final String DEFAULT_LOCK_STORE_NAME = "flamingockLocks";
    public static final String LOCK_PK = "partitionKey";
    public static final String LOCK_OWNER = "lockOwner";

    public static final String KEY_EXECUTION_ID = "executionId";
    public static final String KEY_CHANGE_ID = "changeId";
    public static final String KEY_AUTHOR = "author";
    public static final String KEY_TIMESTAMP = "timestamp";
    public static final String KEY_STATE = "state";
    public static final String KEY_TYPE = "type";
    public static final String KEY_CHANGEUNIT_CLASS = "changeUnitClass";
    public static final String KEY_INVOKED_METHOD = "invokedMethod";
    public static final String KEY_METADATA = "metadata";
    public static final String KEY_EXECUTION_MILLIS = "executionMillis";
    public static final String KEY_EXECUTION_HOSTNAME = "executionHostname";
    public static final String KEY_ERROR_TRACE = "errorTrace";
    public static final String KEY_SYSTEM_CHANGE = "systemChange";

    private Constants() {
    }
}
