/*
 * Copyright 2025 Flamingock (https://www.flamingock.io)
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
package io.flamingock.targetsystem.couchbase;

import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.transactions.TransactionAttemptContext;
import com.couchbase.client.java.transactions.error.TransactionFailedException;
import io.flamingock.internal.common.core.context.Dependency;
import io.flamingock.internal.core.community.TransactionManager;
import io.flamingock.internal.core.runtime.ExecutionRuntime;
import io.flamingock.internal.core.task.navigation.step.FailedStep;
import io.flamingock.internal.core.transaction.TransactionWrapper;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public class CouchbaseTxWrapper implements TransactionWrapper {

    private final Cluster cluster;
    private final TransactionManager<TransactionAttemptContext> txManager;

    static class IntentionalRollback extends RuntimeException {}

    public CouchbaseTxWrapper(Cluster cluster, TransactionManager<TransactionAttemptContext> txManager) {
        this.cluster = cluster;
        this.txManager = txManager;
    }

    TransactionManager<TransactionAttemptContext> getTxManager() {
        return txManager;
    }

    @Override
    public <T> T wrapInTransaction(ExecutionRuntime executionRuntime, Function<ExecutionRuntime, T> operation) {
        String sessionId = executionRuntime.getSessionId();

        AtomicReference<T> resultRef = new AtomicReference<>();

        try {
            cluster.transactions().run(ctx -> {
                txManager.startSession(sessionId, ctx);
                Dependency clienteSessionDependency = new Dependency(ctx);
                executionRuntime.addDependency(clienteSessionDependency);
                T result = operation.apply(executionRuntime);
                resultRef.set(result);
                if (result instanceof FailedStep) {
                    throw new IntentionalRollback();
                }
            });
            return resultRef.get();
        }
        catch (TransactionFailedException e) {
            if (e.getCause() instanceof IntentionalRollback) {
                return resultRef.get();
            }
            throw e;
        }
        finally {
            txManager.closeSession(sessionId);
        }
    }
}
