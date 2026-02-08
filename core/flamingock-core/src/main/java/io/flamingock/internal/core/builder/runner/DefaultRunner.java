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
package io.flamingock.internal.core.builder.runner;

import io.flamingock.internal.core.operation.AbstractOperationResult;
import io.flamingock.internal.core.operation.RunnableOperation;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;

public class DefaultRunner implements Runner {
    private static final Logger logger = FlamingockLoggerFactory.getLogger("flamingock.runner");

    private final RunnerId runnerId;
    private final RunnableOperation<?, ?> operation;
    private final Runnable finalizer;


    public DefaultRunner(RunnerId runnerId,
                         RunnableOperation<?, ?> operation,
                         Runnable finalizer) {
        this.runnerId = runnerId;
        this.finalizer = finalizer;
        this.operation = operation;
    }

    @Override
    public void run() {
        try {
            AbstractOperationResult result = operation.run();
            //todo process result. Maybe just printing result
        } finally {
            finalizer.run();
        }
    }

}
