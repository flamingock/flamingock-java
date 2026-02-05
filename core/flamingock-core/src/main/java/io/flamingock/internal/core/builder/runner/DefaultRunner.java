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
import io.flamingock.internal.core.operation.Operation;
import io.flamingock.internal.core.operation.OperationArgs;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;

public class DefaultRunner implements Runner {
    private static final Logger logger = FlamingockLoggerFactory.getLogger("PipelineRunner");

    private final RunnerId runnerId;
    private final Runnable runnerOps;
    private final Runnable finalizer;


    public <T extends OperationArgs> DefaultRunner(RunnerId runnerId,
                                                   Operation<T, ?> operation,
                                                   T args,
                                                   Runnable finalizer) {
        this.runnerId = runnerId;
        this.finalizer = finalizer;
        runnerOps = () -> {
            AbstractOperationResult result = operation.execute(args);
        };
    }

    @Override
    public void run() {
        try {
            runnerOps.run();
        } finally {
            finalizer.run();
        }
    }

}
