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

import io.flamingock.internal.common.core.response.FileResponseChannel;
import io.flamingock.internal.common.core.response.NoOpResponseChannel;
import io.flamingock.internal.common.core.response.ResponseChannel;
import io.flamingock.internal.core.builder.args.FlamingockArguments;
import io.flamingock.internal.core.operation.RunnableOperation;
import io.flamingock.internal.util.JsonObjectMapper;
import io.flamingock.internal.util.id.RunnerId;

/**
 * Factory for creating the appropriate Runner based on the execution mode.
 */
public class RunnerFactory {

    private final RunnerId runnerId;
    private final FlamingockArguments flamingockArgs;
    private final RunnableOperation<?, ?> operation;
    private final Runnable finalizer;

    public RunnerFactory(RunnerId runnerId,
                         FlamingockArguments flamingockArgs,
                         RunnableOperation<?, ?> operation,
                         Runnable finalizer) {
        this.runnerId = runnerId;
        this.flamingockArgs = flamingockArgs;
        this.operation = operation;
        this.finalizer = finalizer;
    }

    /**
     * Creates the appropriate runner based on the execution mode.
     *
     * @return CliRunner when in CLI mode, DefaultRunner otherwise
     */
    public Runner create() {
        if (flamingockArgs.isCliMode()) {
            return createCliRunner();
        }
        return createDefaultRunner();
    }

    private Runner createCliRunner() {
        ResponseChannel channel = flamingockArgs.getOutputFile()
                .map(outputFile -> (ResponseChannel) new FileResponseChannel(outputFile, JsonObjectMapper.DEFAULT_INSTANCE))
                .orElseGet(NoOpResponseChannel::new);

        return new CliRunner(operation, finalizer, channel, flamingockArgs.getOperation());
    }

    private Runner createDefaultRunner() {
        return new DefaultRunner(runnerId, operation, finalizer);
    }
}
