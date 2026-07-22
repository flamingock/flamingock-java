/*
 * Copyright 2026 Flamingock (https://www.flamingock.io)
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
package io.flamingock.targetsystem.mongodb.reactive;

import com.mongodb.TransactionOptions;
import com.mongodb.reactivestreams.client.ClientSession;
import io.flamingock.internal.common.core.context.Dependency;
import io.flamingock.internal.common.core.context.RuntimeContext;
import io.flamingock.internal.common.core.error.DatabaseTransactionException;
import io.flamingock.internal.common.core.transaction.TransactionWrapper;
import io.flamingock.internal.core.change.navigation.step.FailedStep;
import io.flamingock.internal.core.transaction.TransactionManager;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import io.flamingock.reactive.util.PublisherSync;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.Function;

public class MongoDBReactiveTxWrapper implements TransactionWrapper {

    private static final Logger logger = FlamingockLoggerFactory.getLogger("MongoReactiveTx");
    private final TransactionManager<ClientSession> sessionManager;

    public MongoDBReactiveTxWrapper(TransactionManager<ClientSession> sessionManager) {
        this.sessionManager = sessionManager;
    }

    TransactionManager<ClientSession> getTxManager() {
        return sessionManager;
    }

    @Override
    public <CONTEXT extends RuntimeContext, RESULT> RESULT wrapInTransaction(CONTEXT executionContext, Function<CONTEXT, RESULT> operation) {
        LocalDateTime transactionStart = LocalDateTime.now();
        String sessionId = executionContext.getSessionId();
        ClientSession clientSession = sessionManager.startSession(sessionId);
        String connectionInfo = getConnectionInfo(clientSession);

        try {
            logger.debug("Starting MongoDB reactive transaction [connection={}]", connectionInfo);
            clientSession.startTransaction(TransactionOptions.builder().build());
            executionContext.addDependency(new Dependency(ClientSession.class, clientSession, false));

            try {
                RESULT result = operation.apply(executionContext);
                Duration transactionDuration = Duration.between(transactionStart, LocalDateTime.now());
                if (result instanceof FailedStep) {
                    logger.info("Rolling back MongoDB reactive transaction due to failed step [duration={}]", formatDuration(transactionDuration));
                    PublisherSync.complete(clientSession.abortTransaction());
                    logger.debug("MongoDB reactive transaction rollback completed successfully [duration={}]", formatDuration(transactionDuration));
                } else {
                    logger.debug("Committing successful MongoDB reactive transaction [duration={}]", formatDuration(transactionDuration));
                    PublisherSync.complete(clientSession.commitTransaction());
                    logger.debug("MongoDB reactive transaction commit completed successfully [duration={}]", formatDuration(transactionDuration));
                }
                return result;
            } catch (Exception e) {
                Duration failureDuration = Duration.between(transactionStart, LocalDateTime.now());
                logger.debug("MongoDB reactive transaction failed, attempting rollback [duration={} error={}]",
                        formatDuration(failureDuration), e.getMessage());

                DatabaseTransactionException.RollbackStatus rollbackStatus;
                try {
                    PublisherSync.complete(clientSession.abortTransaction());
                    rollbackStatus = DatabaseTransactionException.RollbackStatus.SUCCESS;
                    logger.info("MongoDB reactive transaction rollback completed successfully after failure [duration={}]",
                            formatDuration(failureDuration));
                } catch (Exception rollbackEx) {
                    rollbackStatus = DatabaseTransactionException.RollbackStatus.FAILED;
                    logger.debug("MongoDB reactive transaction rollback failed [duration={} rollback_error={}]",
                            formatDuration(failureDuration), rollbackEx.getMessage(), rollbackEx);
                }
                throw new DatabaseTransactionException(
                        "MongoDB reactive transaction failed during operation execution",
                        DatabaseTransactionException.TransactionState.FAILED,
                        null, // isolation level not applicable to MongoDB
                        null, // timeout not available
                        failureDuration,
                        rollbackStatus,
                        null, // specific operation not available at this level
                        connectionInfo,
                        e);
            }
        } finally {
            try {
                clientSession.close();
            } finally {
                sessionManager.closeSession(sessionId);
            }
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
