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
import com.mongodb.client.MongoClient;
import io.flamingock.internal.common.core.context.Dependency;
import io.flamingock.internal.core.runtime.ExecutionRuntime;
import io.flamingock.internal.core.task.navigation.step.FailedStep;
import io.flamingock.internal.core.transaction.TransactionWrapper;
import io.flamingock.internal.core.community.TransactionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

public class MongoSyncTxWrapper implements TransactionWrapper {
    private static final Logger logger = LoggerFactory.getLogger(MongoSyncTxWrapper.class);

    private final TransactionManager<ClientSession> sessionManager;

    public MongoSyncTxWrapper(MongoClient mongoClient) {
        sessionManager = new TransactionManager<>(mongoClient::startSession);
    }

    @Deprecated
    public MongoSyncTxWrapper(TransactionManager<ClientSession> sessionManager) {
        this.sessionManager = sessionManager;
    }

    public TransactionManager<ClientSession> getTxManager() {
        return sessionManager;
    }

    @Override
    public <T> T wrapInTransaction(ExecutionRuntime executionRuntime, Function<ExecutionRuntime, T> operation) {
        String sessionId = executionRuntime.getSessionId();
        Dependency clienteSessionDependency;
        try (ClientSession clientSession = sessionManager.startSession(sessionId)) {
            clienteSessionDependency = new Dependency(clientSession);
            clientSession.startTransaction(TransactionOptions.builder().build());
            executionRuntime.addDependency(clienteSessionDependency);
            T result = operation.apply(executionRuntime);
            if (result instanceof FailedStep) {
                clientSession.abortTransaction();
            } else {
                clientSession.commitTransaction();
            }
            return result;
        } finally {
            sessionManager.closeSession(sessionId);
        }
    }


}
