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
package io.flamingock.targetsystem.mysql;

import io.flamingock.internal.common.core.context.Dependency;
import io.flamingock.internal.common.core.error.DatabaseTransactionException;
import io.flamingock.internal.core.community.TransactionManager;
import io.flamingock.internal.core.runtime.ExecutionRuntime;
import io.flamingock.internal.core.task.navigation.step.FailedStep;
import io.flamingock.internal.core.transaction.TransactionWrapper;
import io.flamingock.internal.util.FlamingockLoggerFactory;
import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.Function;

public class SqlTxWrapper implements TransactionWrapper {
    private static final Logger logger = FlamingockLoggerFactory.getLogger("SqlTx");

    private final TransactionManager<Connection> txManager;

    public SqlTxWrapper(TransactionManager<Connection> txManager) {
        this.txManager = txManager;
    }

    @Override
    public <T> T wrapInTransaction(ExecutionRuntime executionRuntime, Function<ExecutionRuntime, T> operation) {
        LocalDateTime transactionStart = LocalDateTime.now();
        
        try (Connection connection = txManager.startSession(executionRuntime.getSessionId())) {
            boolean originalAutoCommit = connection.getAutoCommit();
            String isolationLevel = getIsolationLevelName(connection.getTransactionIsolation());
            String connectionInfo = getConnectionInfo(connection);
            
            logger.debug("Starting SQL transaction [isolation={} connection={}]", isolationLevel, connectionInfo);
            
            try {
                connection.setAutoCommit(false);
                executionRuntime.addDependency(new Dependency(connection));
                
                T result = operation.apply(executionRuntime);
                Duration transactionDuration = Duration.between(transactionStart, LocalDateTime.now());
                
                if (result instanceof FailedStep) {
                    logger.info("Rolling back transaction due to failed step [duration={}]", formatDuration(transactionDuration));
                    connection.rollback();
                    logger.debug("Transaction rollback completed successfully [duration={}]", formatDuration(transactionDuration));
                } else {
                    logger.debug("Committing successful transaction [duration={}]", formatDuration(transactionDuration));
                    connection.commit();
                    logger.debug("Transaction commit completed successfully [duration={}]", formatDuration(transactionDuration));
                }
                return result;
                
            } catch (Exception e) {
                Duration failureDuration = Duration.between(transactionStart, LocalDateTime.now());
                logger.error("Transaction failed, attempting rollback [duration={} error={}]", 
                           formatDuration(failureDuration), e.getMessage());
                
                DatabaseTransactionException.RollbackStatus rollbackStatus;
                try {
                    connection.rollback();
                    rollbackStatus = DatabaseTransactionException.RollbackStatus.SUCCESS;
                    logger.info("Transaction rollback completed successfully after failure [duration={}]", 
                              formatDuration(failureDuration));
                } catch (SQLException rollbackEx) {
                    rollbackStatus = DatabaseTransactionException.RollbackStatus.FAILED;
                    logger.error("Transaction rollback failed [duration={} rollback_error={}]", 
                               formatDuration(failureDuration), rollbackEx.getMessage(), rollbackEx);
                }
                
                throw new DatabaseTransactionException(
                    "SQL transaction failed during operation execution",
                    DatabaseTransactionException.TransactionState.FAILED,
                    isolationLevel,
                    null, // timeout not available
                    failureDuration,
                    rollbackStatus,
                    null, // specific SQL operation not available at this level
                    connectionInfo,
                    e
                );
                
            } finally {
                try {
                    connection.setAutoCommit(originalAutoCommit);
                } catch (SQLException setAutoCommitEx) {
                    logger.error("Failed to restore autoCommit to {} [connection={}]", 
                               originalAutoCommit, connectionInfo, setAutoCommitEx);
                }
            }

        } catch (SQLException e) {
            Duration failureDuration = Duration.between(transactionStart, LocalDateTime.now());
            throw new DatabaseTransactionException(
                "Failed to establish SQL database connection",
                DatabaseTransactionException.TransactionState.FAILED,
                null, // isolation level unknown
                null, // timeout not available
                failureDuration,
                DatabaseTransactionException.RollbackStatus.NOT_ATTEMPTED,
                null, // no specific operation
                "Connection establishment failed",
                e
            );
        }
    }
    
    private String getIsolationLevelName(int isolationLevel) {
        switch (isolationLevel) {
            case Connection.TRANSACTION_READ_UNCOMMITTED: return "READ_UNCOMMITTED";
            case Connection.TRANSACTION_READ_COMMITTED: return "READ_COMMITTED";
            case Connection.TRANSACTION_REPEATABLE_READ: return "REPEATABLE_READ";
            case Connection.TRANSACTION_SERIALIZABLE: return "SERIALIZABLE";
            default: return "UNKNOWN(" + isolationLevel + ")";
        }
    }
    
    private String getConnectionInfo(Connection connection) {
        try {
            return String.format("%s@%s", connection.getMetaData().getUserName(), 
                               connection.getMetaData().getURL());
        } catch (SQLException e) {
            return "connection_info_unavailable";
        }
    }
    
    private String formatDuration(Duration duration) {
        long millis = duration.toMillis();
        if (millis < 1000) {
            return millis + "";
        } else if (millis < 60000) {
            return String.format("%.1fs", millis / 1000.0);
        } else {
            return String.format("%.1fm", millis / 60000.0);
        }
    }

}