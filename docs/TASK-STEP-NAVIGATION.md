# Flamingock Task Step Navigation Flow

## Task Execution Flow Diagram

The following Mermaid graph shows how tasks flow through different TaskStep types during execution in the StepNavigator:

```mermaid
graph TB
    %% Entry Point
    START([Task Execution Request]) --> CHECK_EXECUTED{task.isAlreadyExecuted?}
    
    %% Already Executed Path
    CHECK_EXECUTED -->|Yes| ALREADY_APPLIED[CompletedAlreadyAppliedStep]
    ALREADY_APPLIED --> SUCCESS_SUMMARY[Success Summary]
    
    %% New Execution Path
    CHECK_EXECUTED -->|No| START_STEP[StartStep]
    START_STEP --> AUDIT_START[auditStartExecution]
    AUDIT_START --> EXECUTABLE_STEP[ExecutableStep]
    
    %% Transaction Decision
    EXECUTABLE_STEP --> CHECK_TRANSACTIONAL{isTransactional?}
    
    %% Non-Transactional Path
    CHECK_TRANSACTIONAL -->|No| EXECUTE_TASK[executeTask]
    EXECUTE_TASK --> EXECUTION_OUTCOME{Execution Result}
    
    %% Transactional Path
    CHECK_TRANSACTIONAL -->|Yes| CLOUD_CHECK{Cloud Edition?}
    CLOUD_CHECK -->|Yes| SET_ONGOING[Set OngoingStatus.EXECUTION]
    CLOUD_CHECK -->|No| TRANSACTION_WRAPPER[TransactionWrapper]
    SET_ONGOING --> TRANSACTION_WRAPPER
    
    TRANSACTION_WRAPPER --> EXECUTE_IN_TRANSACTION[executeTask in Transaction]
    EXECUTE_IN_TRANSACTION --> TRANSACTION_OUTCOME{Transaction Result}
    
    %% Execution Outcomes
    EXECUTION_OUTCOME -->|Success| SUCCESS_EXECUTION[SuccessExecutionStep]
    EXECUTION_OUTCOME -->|Failure| FAILED_EXECUTION[FailedExecutionStep]
    
    TRANSACTION_OUTCOME -->|Success| SUCCESS_EXECUTION_TX[SuccessExecutionStep]
    TRANSACTION_OUTCOME -->|Failure| AUTO_ROLLBACK[CompleteAutoRolledBackStep]
    
    %% Success Path
    SUCCESS_EXECUTION --> AUDIT_SUCCESS[auditExecution]
    SUCCESS_EXECUTION_TX --> AUDIT_SUCCESS_TX[auditExecution]
    SUCCESS_EXECUTION_TX --> CLEAN_ONGOING[Clean OngoingStatus]
    
    AUDIT_SUCCESS --> SUCCESS_AUDIT_RESULT{Audit Result}
    AUDIT_SUCCESS_TX --> SUCCESS_AUDIT_RESULT
    
    SUCCESS_AUDIT_RESULT -->|Success| COMPLETED_SUCCESS[CompletedSuccessStep]
    SUCCESS_AUDIT_RESULT -->|Failed| FAILED_AUDIT[FailedAuditExecutionStep]
    
    %% Failed Execution Path
    FAILED_EXECUTION --> AUDIT_FAILED[auditExecution]
    AUDIT_FAILED --> FAILED_AUDIT_RESULT{Audit Result}
    
    FAILED_AUDIT_RESULT -->|Success| FAILED_SUCCESS_AUDIT[FailedExecutionSuccessAuditStep]
    FAILED_AUDIT_RESULT -->|Failed| FAILED_EXECUTION_OR_AUDIT[FailedExecutionOrAuditStep]
    
    %% Rollback Decision
    FAILED_SUCCESS_AUDIT --> ROLLBACK_CHECK{Is RollableFailedStep?}
    FAILED_EXECUTION_OR_AUDIT --> ROLLBACK_CHECK
    FAILED_AUDIT --> ROLLBACK_CHECK
    AUTO_ROLLBACK --> ROLLBACK_CHECK
    
    %% Rollback Process
    ROLLBACK_CHECK -->|Yes| ROLLBACK_TYPE{Rollback Type}
    ROLLBACK_TYPE -->|Auto| AUDIT_AUTO_ROLLBACK[auditAutoRollback]
    ROLLBACK_TYPE -->|Manual| MANUAL_ROLLBACK_LOOP[Manual Rollback Loop]
    
    MANUAL_ROLLBACK_LOOP --> ROLLABLE_STEP[RollableStep]
    ROLLABLE_STEP --> MANUAL_ROLLBACK[manualRollback]
    MANUAL_ROLLBACK --> ROLLBACK_OUTCOME{Rollback Result}
    
    ROLLBACK_OUTCOME -->|Success| MANUAL_ROLLED_BACK[ManualRolledBackStep]
    ROLLBACK_OUTCOME -->|Failed| FAILED_MANUAL_ROLLBACK[FailedManualRolledBackStep]
    
    MANUAL_ROLLED_BACK --> AUDIT_MANUAL_ROLLBACK[auditManualRollback]
    FAILED_MANUAL_ROLLBACK --> AUDIT_MANUAL_ROLLBACK
    
    AUDIT_MANUAL_ROLLBACK --> COMPLETED_FAILED_MANUAL[CompletedFailedManualRollback]
    AUDIT_AUTO_ROLLBACK --> COMPLETED_AUTO_ROLLBACK[CompleteAutoRolledBackStep]
    
    %% Final Outcomes
    COMPLETED_SUCCESS --> SUCCESS_SUMMARY
    COMPLETED_FAILED_MANUAL --> FAILED_SUMMARY[Failed Summary]
    COMPLETED_AUTO_ROLLBACK --> FAILED_SUMMARY
    ROLLBACK_CHECK -->|No| SUCCESS_SUMMARY
    
    %% Style Classes
    classDef stepClass fill:#e1f5fe,stroke:#01579b,stroke-width:2px
    classDef decisionClass fill:#fff3e0,stroke:#ef6c00,stroke-width:2px
    classDef processClass fill:#f3e5f5,stroke:#4a148c,stroke-width:2px
    classDef terminalClass fill:#e8f5e8,stroke:#2e7d32,stroke-width:2px
    classDef failureClass fill:#ffebee,stroke:#c62828,stroke-width:2px
    
    %% Apply Styles
    class START_STEP,EXECUTABLE_STEP,SUCCESS_EXECUTION,FAILED_EXECUTION,SUCCESS_EXECUTION_TX stepClass
    class COMPLETED_SUCCESS,ALREADY_APPLIED,MANUAL_ROLLED_BACK,FAILED_MANUAL_ROLLBACK stepClass
    class AUTO_ROLLBACK,COMPLETED_FAILED_MANUAL,COMPLETED_AUTO_ROLLBACK stepClass
    class FAILED_SUCCESS_AUDIT,FAILED_EXECUTION_OR_AUDIT,FAILED_AUDIT stepClass
    
    class CHECK_EXECUTED,CHECK_TRANSACTIONAL,CLOUD_CHECK,EXECUTION_OUTCOME decisionClass
    class TRANSACTION_OUTCOME,SUCCESS_AUDIT_RESULT,FAILED_AUDIT_RESULT decisionClass
    class ROLLBACK_CHECK,ROLLBACK_TYPE,ROLLBACK_OUTCOME decisionClass
    
    class AUDIT_START,EXECUTE_TASK,AUDIT_SUCCESS,AUDIT_FAILED processClass
    class SET_ONGOING,CLEAN_ONGOING,TRANSACTION_WRAPPER,EXECUTE_IN_TRANSACTION processClass
    class AUDIT_SUCCESS_TX,MANUAL_ROLLBACK_LOOP,MANUAL_ROLLBACK processClass
    class AUDIT_MANUAL_ROLLBACK,AUDIT_AUTO_ROLLBACK processClass
    
    class SUCCESS_SUMMARY,FAILED_SUMMARY terminalClass
```

## Key Components Explanation

### Entry Point Logic
- **All task execution starts** with checking `task.isAlreadyExecuted()`
- **No alternative entry points** - failed tasks that continue still go through StartStep
- **Pre-execution filtering** determines which tasks reach the StepNavigator

### Step Transitions
1. **StartStep** → **ExecutableStep** (via `start()` method)
2. **ExecutableStep** → **ExecutionStep** variants (via `execute()`)
3. **ExecutionStep** → **AfterExecutionAuditStep** variants (via `applyAuditResult()`)

### Transaction Handling
- **Transactional tasks** go through `TransactionWrapper`
- **Cloud edition** tracks ongoing status during execution
- **Auto-rollback** occurs for transactional failures
- **Manual rollback** handles non-transactional failures

### Rollback Mechanisms
- **RollableFailedStep** triggers rollback process
- **Manual rollback** loops through rollback steps
- **Auto rollback** for transaction-managed failures
- **Audit operations** track all rollback activities

### Completion States
- **CompletedSuccessStep**: Successful execution and audit
- **CompletedAlreadyAppliedStep**: Task was already executed
- **CompletedFailedManualRollback**: Failed with manual rollback
- **CompleteAutoRolledBackStep**: Failed with automatic rollback

## Critical Insights

1. **Single Entry Path**: Despite the complexity, all executions flow through StartStep
2. **State-Based Navigation**: Step transitions are based on execution outcomes and audit results  
3. **Transaction Awareness**: Cloud and Community editions handle transactions differently
4. **Comprehensive Rollback**: Multiple rollback strategies ensure system consistency
5. **Audit Trail**: Every step transition is audited for complete traceability

This navigation system ensures robust execution with proper error handling, rollback capabilities, and complete audit trails for all change operations.