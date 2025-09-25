# Flamingock Execution Logging Examples

This document shows real-world examples of what users will see in their logs during different Flamingock execution scenarios with the improved logging system.

## 1. Happy Path - Successful Execution

**Scenario**: 3 changes execute successfully in a transactional pipeline

### Application Logs
```
INFO  - Starting Flamingock process successfully

DEBUG - Starting lock refresh daemon [lock_key=pipeline-process]
INFO  - Starting stage execution [stage=system tasks=3 execution_id=exec-001]

DEBUG - Starting change execution [change=create-user-table]
DEBUG - Starting SQL transaction [isolation=READ_COMMITTED connection=user@jdbc:mysql://localhost/db]
INFO  - Change execution completed successfully [change=create-user-table duration=450ms]
DEBUG - Committing successful SQL transaction [duration=450ms]
DEBUG - SQL transaction commit completed successfully [duration=450ms]
DEBUG - Audit operation completed successfully [change=create-user-table operation=execution]

DEBUG - Starting change execution [change=add-user-indexes]
DEBUG - Starting SQL transaction [isolation=READ_COMMITTED connection=user@jdbc:mysql://localhost/db]
INFO  - Change execution completed successfully [change=add-user-indexes duration=120ms]
DEBUG - Committing successful SQL transaction [duration=120ms]
DEBUG - SQL transaction commit completed successfully [duration=120ms]
DEBUG - Audit operation completed successfully [change=add-user-indexes operation=execution]

DEBUG - Starting change execution [change=seed-admin-user]
DEBUG - Starting SQL transaction [isolation=READ_COMMITTED connection=user@jdbc:mysql://localhost/db]
INFO  - Change execution completed successfully [change=seed-admin-user duration=80ms]
DEBUG - Committing successful SQL transaction [duration=80ms]
DEBUG - SQL transaction commit completed successfully [duration=80ms]
DEBUG - Audit operation completed successfully [change=seed-admin-user operation=execution]

INFO  - Stage execution completed successfully [stage=system duration=1.2s tasks=3]
DEBUG - Lock refresh daemon stopped [lock_key=pipeline-process]

INFO  - Finished Flamingock process successfully
Stage: system
Task: create-user-table
Task: add-user-indexes  
Task: seed-admin-user
```

---

## 2. Database Transaction Failure

**Scenario**: Second change fails due to database constraint violation, triggers rollback

### Application Logs
```
INFO  - Starting Flamingock process successfully

DEBUG - Starting lock refresh daemon [lock_key=pipeline-process]
INFO  - Starting stage execution [stage=system tasks=3 execution_id=exec-002]

DEBUG - Starting change execution [change=create-user-table]
DEBUG - Starting SQL transaction [isolation=READ_COMMITTED connection=user@jdbc:mysql://localhost/db]
INFO  - Change execution completed successfully [change=create-user-table duration=420ms]
DEBUG - Committing successful SQL transaction [duration=420ms]
DEBUG - SQL transaction commit completed successfully [duration=420ms]
DEBUG - Audit operation completed successfully [change=create-user-table operation=execution]

DEBUG - Starting change execution [change=add-duplicate-index]
DEBUG - Starting SQL transaction [isolation=READ_COMMITTED connection=user@jdbc:mysql://localhost/db]
ERROR - Change execution failed [change=add-duplicate-index stage=system]
ERROR - SQL transaction failed, attempting rollback [duration=850ms error=Duplicate entry 'users_email_idx' for key 'index_name']
INFO  - SQL transaction rollback completed successfully after failure [duration=850ms]

ERROR - Stage execution failed [stage=system duration=1.8s]
DEBUG - Lock refresh daemon stopped [lock_key=pipeline-process]

ERROR - Error executing the process. ABORTED OPERATION
io.flamingock.internal.common.core.error.DatabaseTransactionException: SQL transaction failed during operation execution
  Transaction State: FAILED
  Isolation Level: READ_COMMITTED
  Duration: 850ms
  Rollback: SUCCESS
  Connection: user@jdbc:mysql://localhost/db
Caused by: java.sql.SQLException: Duplicate entry 'users_email_idx' for key 'index_name'
	at ...
```

---

## 3. Lock Acquisition Failure (Required)

**Scenario**: Another Flamingock instance is running, lock cannot be acquired with `throwExceptionIfCannotObtainLock=true`

### Application Logs
```
INFO  - Starting Flamingock process successfully

ERROR - Required process lock not acquired - ABORTING OPERATION
io.flamingock.internal.core.engine.lock.LockException: Failed to acquire lock 'pipeline-process' within timeout
  Failure Type: ACQUISITION_TIMEOUT
  Lock Key: pipeline-process
  Current Owner: flamingock-instance-456
  Attempt Duration: 30.0s
  Configured Timeout: 30.0s
  Retry Attempts: 5

ERROR - Error executing the process. ABORTED OPERATION
```

---

## 4. Lock Acquisition Failure (Optional - Continues)

**Scenario**: Lock cannot be acquired but `throwExceptionIfCannotObtainLock=false`, application continues without Flamingock

### Application Logs
```
INFO  - Starting Flamingock process successfully

WARN  - Process lock not acquired but throwExceptionIfCannotObtainLock=false - CONTINUING WITHOUT LOCK
io.flamingock.internal.core.engine.lock.LockException: Failed to acquire lock 'pipeline-process' within timeout
  Failure Type: ACQUISITION_TIMEOUT
  Lock Key: pipeline-process
  Current Owner: flamingock-instance-456
  Attempt Duration: 30.0s
  Configured Timeout: 30.0s
  Retry Attempts: 5

INFO  - Finished Flamingock process successfully
```

---

## 5. Mixed Transaction Modes

**Scenario**: Pipeline with both transactional and non-transactional changes, one non-transactional fails

### Application Logs  
```
INFO  - Starting Flamingock process successfully

DEBUG - Starting lock refresh daemon [lock_key=pipeline-process]
INFO  - Starting stage execution [stage=system tasks=4 execution_id=exec-003]

DEBUG - Starting change execution [change=create-schema]
DEBUG - Starting SQL transaction [isolation=READ_COMMITTED connection=user@jdbc:mysql://localhost/db]
INFO  - Change execution completed successfully [change=create-schema duration=280ms]
DEBUG - Committing successful SQL transaction [duration=280ms]
DEBUG - SQL transaction commit completed successfully [duration=280ms]
DEBUG - Audit operation completed successfully [change=create-schema operation=execution]

DEBUG - Starting change execution [change=bulk-data-load]
INFO  - Change execution completed successfully [change=bulk-data-load duration=5.2s]
DEBUG - Audit operation completed successfully [change=bulk-data-load operation=execution]

DEBUG - Starting change execution [change=create-indexes]
DEBUG - Starting SQL transaction [isolation=READ_COMMITTED connection=user@jdbc:mysql://localhost/db]
INFO  - Change execution completed successfully [change=create-indexes duration=1.8s]
DEBUG - Committing successful SQL transaction [duration=1.8s]
DEBUG - SQL transaction commit completed successfully [duration=1.8s]
DEBUG - Audit operation completed successfully [change=create-indexes operation=execution]

DEBUG - Starting change execution [change=update-config-file]
ERROR - Change execution failed [change=update-config-file stage=system]

ERROR - Stage execution failed [stage=system duration=8.1s]
DEBUG - Lock refresh daemon stopped [lock_key=pipeline-process]

ERROR - Error executing the process. ABORTED OPERATION
io.flamingock.internal.common.core.error.ChangeExecutionException: Failed to update configuration file
  Change ID: update-config-file
  Stage: system
  Execution Mode: non-transactional
  Execution Duration: 120ms
  Target System: local-filesystem
Caused by: java.io.IOException: Permission denied: /etc/app/config.properties
	at ...
```

---

## 6. MongoDB Transaction Failure

**Scenario**: MongoDB change fails due to document validation error

### Application Logs
```
INFO  - Starting Flamingock process successfully

DEBUG - Starting lock refresh daemon [lock_key=pipeline-process]
INFO  - Starting stage execution [stage=system tasks=2 execution_id=exec-004]

DEBUG - Starting change execution [change=create-user-collection]
DEBUG - Starting MongoDB transaction [connection=session_507f1f77bcf86cd799439011]
INFO  - Change execution completed successfully [change=create-user-collection duration=150ms]
DEBUG - Committing successful MongoDB transaction [duration=150ms]
DEBUG - MongoDB transaction commit completed successfully [duration=150ms]
DEBUG - Audit operation completed successfully [change=create-user-collection operation=execution]

DEBUG - Starting change execution [change=migrate-user-data]
DEBUG - Starting MongoDB transaction [connection=session_507f1f77bcf86cd799439012]
ERROR - Change execution failed [change=migrate-user-data stage=system]
ERROR - MongoDB transaction failed, attempting rollback [duration=2.1s error=Document failed validation]
INFO  - MongoDB transaction rollback completed successfully after failure [duration=2.1s]

ERROR - Stage execution failed [stage=system duration=3.2s]
DEBUG - Lock refresh daemon stopped [lock_key=pipeline-process]

ERROR - Error executing the process. ABORTED OPERATION
io.flamingock.internal.common.core.error.DatabaseTransactionException: MongoDB transaction failed during operation execution
  Transaction State: FAILED
  Duration: 2.1s
  Rollback: SUCCESS  
  Connection: session_507f1f77bcf86cd799439012
Caused by: com.mongodb.MongoWriteException: Document failed validation
	at ...
```

---

## 7. DynamoDB Transaction Cancellation

**Scenario**: DynamoDB transaction cancelled due to condition check failure

### Application Logs
```
INFO  - Starting Flamingock process successfully

DEBUG - Starting lock refresh daemon [lock_key=pipeline-process] 
INFO  - Starting stage execution [stage=system tasks=2 execution_id=exec-005]

DEBUG - Starting change execution [change=create-user-table]
DEBUG - Starting DynamoDB transaction [connection=DynamoDB Enhanced Client]
INFO  - Change execution completed successfully [change=create-user-table duration=320ms]
DEBUG - Committing DynamoDB transaction [duration=320ms]
DEBUG - DynamoDB transaction commit completed successfully [duration=320ms]
DEBUG - Audit operation completed successfully [change=create-user-table operation=execution]

DEBUG - Starting change execution [change=migrate-legacy-users]
DEBUG - Starting DynamoDB transaction [connection=DynamoDB Enhanced Client]
ERROR - Change execution failed [change=migrate-legacy-users stage=system]
ERROR - DynamoDB transaction cancelled [duration=1.8s reasons=ConditionalCheckFailed: Item already exists, ValidationException: Invalid attribute value]

ERROR - Stage execution failed [stage=system duration=2.9s]
DEBUG - Lock refresh daemon stopped [lock_key=pipeline-process]

ERROR - Error executing the process. ABORTED OPERATION
io.flamingock.internal.common.core.error.DatabaseTransactionException: DynamoDB transaction was cancelled during commit
  Transaction State: FAILED
  Duration: 1.8s
  Rollback: NOT_SUPPORTED
  Operation: TransactWriteItems
  Connection: DynamoDB Enhanced Client
Caused by: software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException: Transaction cancelled
	at ...
```

---

## 8. Target System Validation Failure

**Scenario**: Requested target system not found in strict validation mode

### Application Logs
```
INFO  - Starting Flamingock process successfully

ERROR - Error executing the process. ABORTED OPERATION
io.flamingock.internal.common.core.error.TargetSystemException: Target system 'postgresql-prod' not found
  Failure Type: NOT_FOUND
  Requested: postgresql-prod
  Available: [mysql-primary, mongodb-cluster]
  Validation Mode: strict
  Resolution: Use one of: [mysql-primary, mongodb-cluster] or configure 'postgresql-prod'
```

---

## 9. Rollback Transaction Failure (Critical)

**Scenario**: Transaction fails AND rollback also fails - critical situation

### Application Logs
```
INFO  - Starting Flamingock process successfully

DEBUG - Starting lock refresh daemon [lock_key=pipeline-process]
INFO  - Starting stage execution [stage=system tasks=1 execution_id=exec-006]

DEBUG - Starting change execution [change=critical-schema-change]
DEBUG - Starting SQL transaction [isolation=SERIALIZABLE connection=admin@jdbc:mysql://prod/db]
ERROR - Change execution failed [change=critical-schema-change stage=system]
ERROR - SQL transaction failed, attempting rollback [duration=5.2s error=Lock wait timeout exceeded]
ERROR - Transaction rollback failed [duration=5.2s rollback_error=Communications link failure]

ERROR - Stage execution failed [stage=system duration=5.2s]
DEBUG - Lock refresh daemon stopped [lock_key=pipeline-process]

ERROR - Error executing the process. ABORTED OPERATION
io.flamingock.internal.common.core.error.DatabaseTransactionException: SQL transaction failed during operation execution
  Transaction State: FAILED
  Isolation Level: SERIALIZABLE
  Duration: 5.2s
  Rollback: FAILED
  Connection: admin@jdbc:mysql://prod/db
Caused by: java.sql.SQLException: Lock wait timeout exceeded; try restarting transaction
	at ...
```

---

## 10. Verbose Lock Daemon (TRACE level enabled)

**Scenario**: When TRACE logging is enabled, shows detailed lock daemon operations

### Application Logs (with TRACE level)
```
INFO  - Starting Flamingock process successfully

DEBUG - Starting lock refresh daemon [lock_key=pipeline-process]
TRACE - Lock daemon sleeping [lock_key=pipeline-process expires_at=2024-01-15T10:30:00 sleep_until=2024-01-15T10:25:00 sleep_duration=5.0m]
TRACE - Lock daemon refreshing lock [lock_key=pipeline-process]
TRACE - Lock daemon sleeping [lock_key=pipeline-process expires_at=2024-01-15T10:35:00 sleep_until=2024-01-15T10:30:00 sleep_duration=5.0m]
TRACE - Lock daemon refreshing lock [lock_key=pipeline-process]

INFO  - Starting stage execution [stage=system tasks=1 execution_id=exec-007]
DEBUG - Starting change execution [change=simple-change]
INFO  - Change execution completed successfully [change=simple-change duration=200ms]
INFO  - Stage execution completed successfully [stage=system duration=250ms tasks=1]

TRACE - Lock daemon detected expired lock [expires_at=2024-01-15T10:30:00 lock_key=pipeline-process]
DEBUG - Lock refresh daemon stopped [lock_key=pipeline-process]

INFO  - Finished Flamingock process successfully
```

## Key Improvements Demonstrated

### 🎯 **Operational Benefits**
1. **Clear Error Context**: Every error includes relevant context (duration, connection, state)
2. **Structured Format**: Key-value pairs enable log aggregation and monitoring
3. **Appropriate Levels**: ERROR for failures, INFO for lifecycle, DEBUG for details, TRACE for verbose
4. **Performance Visibility**: Duration tracking for all operations
5. **Actionable Messages**: Errors include resolution suggestions where possible

### 🔍 **Debugging Benefits**  
1. **Transaction Traceability**: Full transaction lifecycle with timing
2. **Change Context**: Execution mode, target system, stage information
3. **Rollback Status**: Clear indication of rollback success/failure
4. **Lock Management**: Lock key tracking and daemon lifecycle

### 📊 **Monitoring Benefits**
1. **Structured Logging**: Easy to parse for alerting and dashboards
2. **Error Classification**: Specific exception types with programmatic access
3. **Performance Metrics**: Built-in duration tracking for SLA monitoring
4. **Operational States**: Clear success/failure indicators for automation

The logging now provides excellent visibility into Flamingock execution with minimal noise at normal levels, while offering detailed tracing when needed for troubleshooting!