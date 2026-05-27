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
package io.flamingock.internal.core.event.listener;

import io.flamingock.internal.common.core.response.data.ExecuteResponseData;
import io.flamingock.internal.common.core.response.data.ExecutionReportFormatter;
import io.flamingock.internal.core.event.model.IPipelineFailedEvent;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;

import java.util.function.Consumer;

/**
 * Writes the canonical execution report at {@code ERROR} on the {@code FK-Report} logger when a
 * pipeline fails. Registered by the builder unless {@code enableDefaultExecutionReport(false)}
 * is set.
 *
 * <p>Defensive: must never throw. If the event carries no response data we fall back to logging
 * the carried exception only; if the formatter itself fails we emit a single fallback line and
 * swallow the throwable.
 */
public final class DefaultPipelineFailedReportListener implements Consumer<IPipelineFailedEvent> {

    // Bare component name; FlamingockLoggerFactory.getLogger prepends "FK-" → "FK-Report".
    public static final String LOGGER_NAME = "Report";

    private static final Logger logger = FlamingockLoggerFactory.getLogger(LOGGER_NAME);

    @Override
    public void accept(IPipelineFailedEvent event) {
        try {
            ExecuteResponseData result = event != null ? event.getResult() : null;
            Exception cause = event != null ? event.getException() : null;
            if (result == null) {
                logger.error("Flamingock execution failed (no execution data available)", cause);
                return;
            }
            logger.error("{}{}", System.lineSeparator(), ExecutionReportFormatter.report(result));
        } catch (Throwable t) {
            logger.error("FK-Report rendering failed: {}", t.toString(), t);
        }
    }
}
