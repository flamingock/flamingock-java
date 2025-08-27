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
package io.flamingock.targetystem.mongodb.sync;

import com.mongodb.TransactionOptions;
import com.mongodb.client.ClientSession;
import io.flamingock.internal.common.core.context.Dependency;
import io.flamingock.internal.common.core.error.DatabaseTransactionException;
import io.flamingock.internal.core.runtime.ExecutionRuntime;
import io.flamingock.internal.core.task.navigation.step.FailedStep;
import io.flamingock.internal.core.transaction.TransactionWrapper;
import io.flamingock.internal.core.transaction.TransactionManager;
import io.flamingock.internal.util.FlamingockLoggerFactory;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.Function;

public class MongoSyncTxWrapper implements TransactionWrapper {
    private static final Logger logger = FlamingockLoggerFactory.getLogger("MongoTx");

    private final TransactionManager<ClientSession> sessionManager;

   public MongoSyncTxWrapper(TransactionManager<ClientSession> sessionManager) {
        this.sessionManager = sessionManager;
    }

    TransactionManager<ClientSession> getTxManager() {
        return sessionManager;
    }

    @Override
    public <T> T wrapInTransaction(ExecutionRuntime executionRuntime, Function<ExecutionRuntime, T> operation) {
        LocalDateTime transactionStart = LocalDateTime.now();
        String sessionId = executionRuntime.getSessionId();
        Dependency clienteSessionDependency;
        
        try (ClientSession clientSession = sessionManager.startSession(sessionId)) {
            clienteSessionDependency = new Dependency(clientSession);
            String connectionInfo = getConnectionInfo(clientSession);
            
            logger.debug("Starting MongoDB transaction [connection={}]", connectionInfo);
            clientSession.startTransaction(TransactionOptions.builder().build());
            executionRuntime.addDependency(clienteSessionDependency);
            
            try {
                T result = operation.apply(executionRuntime);
                Duration transactionDuration = Duration.between(transactionStart, LocalDateTime.now());
                
                if (result instanceof FailedStep) {
                    logger.info("Rolling back MongoDB transaction due to failed step [duration={}]", formatDuration(transactionDuration));
                    clientSession.abortTransaction();
                    logger.debug("MongoDB transaction rollback completed successfully [duration={}]", formatDuration(transactionDuration));
                } else {
                    logger.debug("Committing successful MongoDB transaction [duration={}]", formatDuration(transactionDuration));
                    clientSession.commitTransaction();
                    logger.debug("MongoDB transaction commit completed successfully [duration={}]", formatDuration(transactionDuration));
                }
                return result;
                
            } catch (Exception e) {
                Duration failureDuration = Duration.between(transactionStart, LocalDateTime.now());
                logger.error("MongoDB transaction failed, attempting rollback [duration={} error={}]", 
                           formatDuration(failureDuration), e.getMessage());
                
                DatabaseTransactionException.RollbackStatus rollbackStatus;
                try {
                    clientSession.abortTransaction();
                    rollbackStatus = DatabaseTransactionException.RollbackStatus.SUCCESS;
                    logger.info("MongoDB transaction rollback completed successfully after failure [duration={}]", 
                              formatDuration(failureDuration));
                } catch (Exception rollbackEx) {
                    rollbackStatus = DatabaseTransactionException.RollbackStatus.FAILED;
                    logger.error("Transaction rollback failed [duration={} rollback_error={}]", 
                               formatDuration(failureDuration), rollbackEx.getMessage(), rollbackEx);
                }
                
                throw new DatabaseTransactionException(
                    "MongoDB transaction failed during operation execution",
                    DatabaseTransactionException.TransactionState.FAILED,
                    null, // isolation level not applicable to MongoDB
                    null, // timeout not available
                    failureDuration,
                    rollbackStatus,
                    null, // specific operation not available at this level
                    connectionInfo,
                    e
                );
            }
            
        } finally {
            sessionManager.closeSession(sessionId);
        }
    }

    private String getConnectionInfo(ClientSession session) {
        try {
            return String.format("session_%s", session.getServerSession().getIdentifier());
        } catch (Exception e) {
            return "connection_info_unavailable";
        }
    }
    
    private String formatDuration(Duration duration) {
        long millis = duration.toMillis();
        if (millis < 1000) {
            return millis + "ms";
        } else if (millis < 60000) {
            return String.format("%.1fs", millis / 1000.0);
        } else {
            return String.format("%.1fm", millis / 60000.0);
        }
    }

}
