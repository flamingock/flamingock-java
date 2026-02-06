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

import io.flamingock.internal.common.core.response.ResponseChannel;
import io.flamingock.internal.common.core.response.ResponseEnvelope;
import io.flamingock.internal.common.core.response.ResponseError;
import io.flamingock.internal.core.operation.AbstractOperationResult;
import io.flamingock.internal.core.operation.OperationType;
import io.flamingock.internal.core.operation.RunnableOperation;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;

/**
 * CLI-specific runner that writes operation results to a response channel,
 * flushes output streams, and exits with the appropriate code.
 */
public class CliRunner implements Runner {

    private static final Logger logger = FlamingockLoggerFactory.getLogger("flamingock.cli.runner");

    private final RunnableOperation<?, ?> operation;
    private final Runnable finalizer;
    private final ResponseChannel channel;
    private final OperationType operationType;

    public CliRunner(RunnableOperation<?, ?> operation,
                     Runnable finalizer,
                     ResponseChannel channel,
                     OperationType operationType) {
        this.operation = operation;
        this.finalizer = finalizer;
        this.channel = channel;
        this.operationType = operationType;
    }

    @Override
    public void run() {
        long startTime = System.currentTimeMillis();
        AbstractOperationResult result = null;
        Throwable error = null;
        int exitCode = 0;

        try {
            result = operation.run();
        } catch (Throwable t) {
            error = t;
            exitCode = 1;
            logger.error("Operation failed", t);
        } finally {
            long durationMs = System.currentTimeMillis() - startTime;

            try {
                writeResponse(result, error, durationMs);
            } catch (Exception e) {
                logger.error("Failed to write response", e);
                if (exitCode == 0) {
                    exitCode = 1;
                }
            }

            try {
                finalizer.run();
            } catch (Exception e) {
                logger.error("Finalizer failed", e);
            }

            try {
                channel.close();
            } catch (Exception e) {
                logger.error("Failed to close channel", e);
            }

            System.out.flush();
            System.err.flush();
            System.exit(exitCode);
        }
    }

    private void writeResponse(AbstractOperationResult result, Throwable error, long durationMs) {
        ResponseEnvelope envelope;
        String operationName = operationType.name();

        if (error != null) {
            envelope = ResponseEnvelope.failure(operationName, ResponseError.from(error), durationMs);
        } else {
            Object data = result != null ? result.toResponseData() : null;
            envelope = ResponseEnvelope.success(operationName, data, durationMs);
        }

        channel.write(envelope);
    }
}
