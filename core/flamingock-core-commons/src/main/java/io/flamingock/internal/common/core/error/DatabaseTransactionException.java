/*
 * Copyright 2023 Flamingock (https://www.flamingock.io)
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
package io.flamingock.internal.common.core.error;

import java.time.Duration;

/**
 * Exception thrown when database transaction operations fail.
 * 
 * <p>This exception provides comprehensive context about transaction failures including:
 * <ul>
 *   <li>Transaction state and isolation level</li>
 *   <li>Timeout information and actual duration</li>
 *   <li>Rollback status and recovery actions taken</li>
 *   <li>Database connection and session details</li>
 *   <li>SQL statement or operation that caused the failure</li>
 * </ul>
 * 
 * <p>This exception should be used for all transaction-related failures in target systems
 * including commit failures, rollback failures, timeout errors, and connection issues.
 * 
 * @since 6.0.0
 */
public class DatabaseTransactionException extends FlamingockException {

    public enum TransactionState {
        STARTED, ACTIVE, COMMITTED, FAILED, ROLLED_BACK, TIMEOUT
    }

    public enum RollbackStatus {
        SUCCESS, FAILED, NOT_ATTEMPTED, NOT_SUPPORTED
    }

    private final TransactionState transactionState;
    private final String isolationLevel;
    private final Duration timeout;
    private final Duration actualDuration;
    private final RollbackStatus rollbackStatus;
    private final String databaseOperation;
    private final String connectionInfo;

    /**
     * Creates a new DatabaseTransactionException with complete transaction context.
     *
     * @param message descriptive error message
     * @param transactionState the state of the transaction when it failed
     * @param isolationLevel the isolation level (e.g., "READ_COMMITTED", "SERIALIZABLE")
     * @param timeout the configured transaction timeout
     * @param actualDuration how long the transaction ran before failing
     * @param rollbackStatus whether rollback was attempted and succeeded
     * @param databaseOperation the SQL or operation that caused the failure
     * @param connectionInfo connection pool or session information
     * @param cause the underlying database exception
     */
    public DatabaseTransactionException(String message,
                                      TransactionState transactionState,
                                      String isolationLevel,
                                      Duration timeout,
                                      Duration actualDuration,
                                      RollbackStatus rollbackStatus,
                                      String databaseOperation,
                                      String connectionInfo,
                                      Throwable cause) {
        super(buildTransactionMessage(message, transactionState, isolationLevel, timeout, 
                                     actualDuration, rollbackStatus, databaseOperation, connectionInfo), cause);
        this.transactionState = transactionState;
        this.isolationLevel = isolationLevel;
        this.timeout = timeout;
        this.actualDuration = actualDuration;
        this.rollbackStatus = rollbackStatus;
        this.databaseOperation = databaseOperation;
        this.connectionInfo = connectionInfo;
    }

    /**
     * Creates a DatabaseTransactionException with minimal context (for backward compatibility).
     *
     * @param message descriptive error message
     * @param transactionState the state when the transaction failed
     * @param cause the underlying exception
     */
    public DatabaseTransactionException(String message, TransactionState transactionState, Throwable cause) {
        this(message, transactionState, null, null, null, RollbackStatus.NOT_ATTEMPTED, null, null, cause);
    }

    /**
     * Creates a DatabaseTransactionException for rollback failures.
     *
     * @param message descriptive error message
     * @param cause the underlying exception
     */
    public static DatabaseTransactionException rollbackFailed(String message, Throwable cause) {
        return new DatabaseTransactionException(message, TransactionState.FAILED, null, null, null, 
                                               RollbackStatus.FAILED, null, null, cause);
    }

    /**
     * Creates a DatabaseTransactionException for transaction timeouts.
     *
     * @param message descriptive error message
     * @param timeout the configured timeout
     * @param actualDuration how long it actually ran
     * @param cause the underlying exception
     */
    public static DatabaseTransactionException timeout(String message, Duration timeout, Duration actualDuration, Throwable cause) {
        return new DatabaseTransactionException(message, TransactionState.TIMEOUT, null, timeout, 
                                               actualDuration, RollbackStatus.NOT_ATTEMPTED, null, null, cause);
    }

    private static String buildTransactionMessage(String message,
                                                 TransactionState transactionState,
                                                 String isolationLevel,
                                                 Duration timeout,
                                                 Duration actualDuration,
                                                 RollbackStatus rollbackStatus,
                                                 String databaseOperation,
                                                 String connectionInfo) {
        StringBuilder contextMessage = new StringBuilder(message);
        
        if (transactionState != null) {
            contextMessage.append("\n  Transaction State: ").append(transactionState);
        }
        if (isolationLevel != null) {
            contextMessage.append("\n  Isolation Level: ").append(isolationLevel);
        }
        if (timeout != null) {
            contextMessage.append("\n  Timeout: ").append(formatDuration(timeout));
        }
        if (actualDuration != null) {
            contextMessage.append("\n  Duration: ").append(formatDuration(actualDuration));
        }
        if (rollbackStatus != null && rollbackStatus != RollbackStatus.NOT_ATTEMPTED) {
            contextMessage.append("\n  Rollback: ").append(rollbackStatus);
        }
        if (databaseOperation != null) {
            contextMessage.append("\n  Operation: ").append(truncateOperation(databaseOperation));
        }
        if (connectionInfo != null) {
            contextMessage.append("\n  Connection: ").append(connectionInfo);
        }
        
        return contextMessage.toString();
    }

    private static String formatDuration(Duration duration) {
        long millis = duration.toMillis();
        if (millis < 1000) {
            return millis + "ms";
        } else if (millis < 60000) {
            return String.format("%.1fs", millis / 1000.0);
        } else {
            return String.format("%.1fm", millis / 60000.0);
        }
    }

    private static String truncateOperation(String operation) {
        if (operation == null || operation.length() <= 200) {
            return operation;
        }
        return operation.substring(0, 200) + "... (truncated)";
    }

    // Getters for programmatic access
    public TransactionState getTransactionState() { return transactionState; }
    public String getIsolationLevel() { return isolationLevel; }
    public Duration getTimeout() { return timeout; }
    public Duration getActualDuration() { return actualDuration; }
    public RollbackStatus getRollbackStatus() { return rollbackStatus; }
    public String getDatabaseOperation() { return databaseOperation; }
    public String getConnectionInfo() { return connectionInfo; }
}