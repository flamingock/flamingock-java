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
package io.flamingock.targetsystem.dynamodb;

import io.flamingock.internal.common.core.context.Dependency;
import io.flamingock.internal.common.core.error.DatabaseTransactionException;
import io.flamingock.internal.core.transaction.TransactionManager;
import io.flamingock.internal.core.runtime.ExecutionRuntime;
import io.flamingock.internal.core.task.navigation.step.FailedStep;
import io.flamingock.internal.core.transaction.TransactionWrapper;
import io.flamingock.internal.util.dynamodb.DynamoDBUtil;
import io.flamingock.internal.util.FlamingockLoggerFactory;
import org.slf4j.Logger;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DynamoDBTxWrapper implements TransactionWrapper {
    private static final Logger logger = FlamingockLoggerFactory.getLogger("DynamoTx");

    private final TransactionManager<TransactWriteItemsEnhancedRequest.Builder> txManager;
    private final DynamoDBUtil dynamoDBUtil;

    public DynamoDBTxWrapper(DynamoDbClient client,
                             TransactionManager<TransactWriteItemsEnhancedRequest.Builder> txManager) {
        this.dynamoDBUtil = new DynamoDBUtil(client);
        this.txManager = txManager;
    }

    public TransactionManager<TransactWriteItemsEnhancedRequest.Builder> getTxManager() {
        return txManager;
    }


    @Override
    public <T> T wrapInTransaction(ExecutionRuntime executionRuntime, Function<ExecutionRuntime, T> operation) {
        LocalDateTime transactionStart = LocalDateTime.now();
        String sessionId = executionRuntime.getSessionId();
        TransactWriteItemsEnhancedRequest.Builder writeRequestBuilder = txManager.startSession(sessionId);
        Dependency writeRequestBuilderDependency = new Dependency(writeRequestBuilder);
        
        try {
            String connectionInfo = getConnectionInfo();
            logger.debug("Starting DynamoDB transaction [connection={}]", connectionInfo);
            
            executionRuntime.addDependency(writeRequestBuilderDependency);
            
            T result = operation.apply(executionRuntime);
            Duration transactionDuration = Duration.between(transactionStart, LocalDateTime.now());
            
            if (result instanceof FailedStep) {
                logger.info("Skipping DynamoDB transaction commit due to failed step [duration={}]", formatDuration(transactionDuration));
            } else {
                try {
                    logger.debug("Committing DynamoDB transaction [duration={}]", formatDuration(transactionDuration));
                    TransactWriteItemsEnhancedRequest request = writeRequestBuilder.build();
                    if(request.transactWriteItems() != null && !request.transactWriteItems().isEmpty()) {
                        dynamoDBUtil.getEnhancedClient().transactWriteItems(request);
                    }
                    logger.debug("DynamoDB transaction commit completed successfully [duration={}]", formatDuration(transactionDuration));
                } catch (TransactionCanceledException ex) {
                    String cancellationReasons = ex.cancellationReasons().stream()
                        .map(reason -> String.format("%s: %s", reason.code(), reason.message()))
                        .collect(Collectors.joining(", "));
                    
                    logger.error("DynamoDB transaction cancelled [duration={} reasons={}]", 
                               formatDuration(transactionDuration), cancellationReasons);
                    
                    throw new DatabaseTransactionException(
                        "DynamoDB transaction was cancelled during commit",
                        DatabaseTransactionException.TransactionState.FAILED,
                        null, // isolation level not applicable to DynamoDB
                        null, // timeout not available
                        transactionDuration,
                        DatabaseTransactionException.RollbackStatus.NOT_SUPPORTED, // DynamoDB handles atomicity
                        "TransactWriteItems",
                        connectionInfo,
                        ex
                    );
                } catch (Exception ex) {
                    logger.error("DynamoDB transaction failed during commit [duration={} error={}]", 
                               formatDuration(transactionDuration), ex.getMessage());
                    
                    throw new DatabaseTransactionException(
                        ex.getMessage(),
                        DatabaseTransactionException.TransactionState.FAILED,
                        null,
                        null,
                        transactionDuration,
                        DatabaseTransactionException.RollbackStatus.NOT_SUPPORTED, // DynamoDB handles atomicity
                        "TransactWriteItems",
                        connectionInfo,
                        ex
                    );
                }
            }

            return result;
        } finally {
            txManager.closeSession(sessionId);
        }
    }

    private String getConnectionInfo() {
        try {
            return "DynamoDB Enhanced Client";
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
