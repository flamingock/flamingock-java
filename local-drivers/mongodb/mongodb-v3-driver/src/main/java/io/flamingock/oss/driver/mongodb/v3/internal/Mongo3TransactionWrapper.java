/*
 * Copyright 2023 Flamingock (https://oss.flamingock.io)
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

package io.flamingock.oss.driver.mongodb.v3.internal;

import com.mongodb.TransactionOptions;
import com.mongodb.client.ClientSession;
import io.flamingock.core.runtime.dependency.Dependency;
import io.flamingock.core.task.navigation.step.FailedStep;
import io.flamingock.core.runtime.dependency.DependencyInjectable;
import io.flamingock.core.task.descriptor.TaskDescriptor;
import io.flamingock.core.transaction.TransactionWrapper;
import io.flamingock.core.local.TransactionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

public class Mongo3TransactionWrapper implements TransactionWrapper {
    private static final Logger logger = LoggerFactory.getLogger(Mongo3TransactionWrapper.class);

    private final TransactionManager<ClientSession> sessionManager;

    Mongo3TransactionWrapper(TransactionManager<ClientSession> sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public <T> T wrapInTransaction(TaskDescriptor taskDescriptor, DependencyInjectable dependencyInjectable, Supplier<T> operation) {
        String sessionId = taskDescriptor.getId();
        Dependency clienteSessionDependency = null;
        try (ClientSession clientSession = sessionManager.startSession(sessionId)) {
            clienteSessionDependency = new Dependency(clientSession);
            clientSession.startTransaction(TransactionOptions.builder().build());
            dependencyInjectable.addDependency(clienteSessionDependency);
            T result = operation.get();
            if (result instanceof FailedStep) {
                clientSession.abortTransaction();
            } else {
                clientSession.commitTransaction();
            }
            return result;
        } finally {
            //Although the ClientSession itself has been closed, it needs to be removed from the map
            sessionManager.closeSession(sessionId);
            if(clienteSessionDependency != null) {
                dependencyInjectable.removeDependencyByRef(clienteSessionDependency);
            }
        }
    }


}
