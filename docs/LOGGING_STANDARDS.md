# Flamingock Logging Standards

## Overview
This document establishes consistent logging patterns across the Flamingock codebase to improve operational visibility, debugging efficiency, and monitoring capabilities.

## Log Levels

### ERROR
- **Transaction failures** (commit/rollback failures)
- **Lock acquisition failures** when `throwExceptionIfCannotObtainLock=true`
- **Critical system failures** that prevent operation completion
- **Change execution failures**
- **Target system initialization failures**

### WARN  
- **Lock acquisition failures** when `throwExceptionIfCannotObtainLock=false` (intentional behavior)
- **Fallback behaviors** (using default target systems, retry attempts)
- **Non-critical failures** that don't prevent overall success
- **Deprecated feature usage**

### INFO
- **Pipeline lifecycle** (started, completed, stage transitions)
- **Rollbacks due to failed steps** (expected behavior)
- **Lock acquisition success** after retries
- **Target system resolution** and configuration
- **Performance summaries**

### DEBUG
- **Transaction lifecycle** (start, commit, rollback success)
- **Change execution details**
- **Lock refresh operations**
- **Context building and dependency resolution**
- **Template processing**

### TRACE
- **Verbose operational details** (previously cluttering DEBUG)
- **Lock daemon heartbeat** (moved from DEBUG)
- **Detailed reflection operations**
- **Internal state transitions**

## Message Templates

### Structured Context Pattern
All log messages should include structured key-value context:

```
action_description [key=value key2=value2 ...]
```

**Examples:**
```
Transaction rollback failed [duration=1.2s rollback_error=Connection timeout]
Lock acquisition timeout [lock=pipeline-process attempts=5 duration=30s]
Change execution failed [change=user-migration-001 stage=system duration=2.1s]
```

### Duration Formatting
Consistent human-readable duration formatting:
- `< 1000ms`: `"500ms"`  
- `< 60s`: `"1.2s"`
- `>= 60s`: `"2.1m"`

### Connection Info Pattern
Consistent connection identification:
- **SQL**: `"user@jdbc:mysql://localhost/db"`
- **MongoDB**: `"session_507f1f77bcf86cd799439011"`
- **DynamoDB**: `"DynamoDB Enhanced Client"`
- **Spring Data**: `"Spring Data MongoDB"`

### Error Context Pattern
Rich error context for failures:
```
Primary_failure_description [context] with nested cause details
  Failure Type: SPECIFIC_TYPE
  Duration: 1.2s  
  Resource: specific_resource_id
  Resolution: suggested_action
```

## Component-Specific Standards

### Transaction Wrappers ✅
- ERROR for transaction/rollback failures
- INFO for rollbacks due to failed steps  
- DEBUG for normal transaction lifecycle
- Structured context with duration, connection info, error details

### Lock Management
- ERROR for acquisition failures when required
- WARN for acquisition failures when optional
- INFO for successful acquisition after retries
- DEBUG for refresh operations
- TRACE for daemon heartbeat (reduce noise)

### Change Execution  
- ERROR for change failures with rich context
- INFO for change completion with performance data
- DEBUG for execution lifecycle details
- Structured context: change ID, stage, execution mode, duration, target system

### Pipeline Orchestration
- INFO for pipeline/stage lifecycle events
- ERROR for pipeline failures with comprehensive summary
- DEBUG for execution planning and navigation
- Structured context: pipeline state, stage counts, duration summaries

### Target System Management
- ERROR for initialization failures  
- WARN for fallback behaviors
- INFO for successful resolution
- DEBUG for configuration loading
- Structured context: system IDs, validation mode, available alternatives

## Exception Integration

All ERROR level logging should use specific exceptions with rich context:
- `DatabaseTransactionException` for transaction failures
- `LockAcquisitionException` for lock failures  
- `ChangeExecutionException` for change failures
- `TargetSystemException` for target system issues

These exceptions automatically provide structured message formatting and programmatic access to failure context.

## Implementation Guidelines

1. **Replace generic FlamingockException** with specific exceptions
2. **Use structured key-value context** in all log messages
3. **Apply consistent log levels** per the standards above
4. **Include duration tracking** for all operations > 100ms
5. **Provide actionable error messages** with resolution guidance
6. **Move verbose details to TRACE** to reduce DEBUG noise

## Monitoring Integration

Structured logging enables:
- **Log aggregation** by error type and context keys
- **Performance monitoring** via duration tracking  
- **Operational alerts** on ERROR/WARN patterns
- **Debugging efficiency** with rich context preservation

## Examples

### Before (Inconsistent/Poor)
```
WARN - Transaction rollback failed
INFO - Lock not acquired
DEBUG - Change failed: java.lang.RuntimeException: Something went wrong
```

### After (Consistent/Rich)
```
ERROR - Transaction rollback failed [duration=1.2s rollback_error=Connection timeout]
  Transaction State: FAILED
  Duration: 1.2s
  Connection: user@jdbc:mysql://localhost/db

WARN - Process lock not acquired but throwExceptionIfCannotObtainLock=false - CONTINUING WITHOUT LOCK

ERROR - Change execution failed [change=user-migration-001 stage=system duration=2.1s]
  Change ID: user-migration-001
  Stage: system  
  Execution Mode: transactional
  Execution Duration: 2.1s
  Target System: mongodb-primary
```

This provides immediate operational context and enables efficient debugging and monitoring.