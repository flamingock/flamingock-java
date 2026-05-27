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

import io.flamingock.internal.common.core.response.data.ExecutionReportFormatter;
import io.flamingock.internal.core.event.model.IPipelineCompletedEvent;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;

import java.util.function.Consumer;

/**
 * Writes the canonical execution report at {@code INFO} on the {@code FK-Report} logger when a
 * pipeline completes successfully. Registered by the builder unless
 * {@code enableDefaultExecutionReport(false)} is set.
 *
 * <p>Defensive: must never throw. A failure inside the formatter is reported as a single
 * fallback {@code ERROR} line and swallowed so it cannot mask the run outcome.
 */
public final class DefaultPipelineCompletedReportListener implements Consumer<IPipelineCompletedEvent> {

    // Bare component name; FlamingockLoggerFactory.getLogger prepends "FK-" → "FK-Report".
    public static final String LOGGER_NAME = "Report";

    private static final Logger logger = FlamingockLoggerFactory.getLogger(LOGGER_NAME);

    @Override
    public void accept(IPipelineCompletedEvent event) {
        try {
            String report = ExecutionReportFormatter.report(event != null ? event.getResult() : null);
            logger.info("{}{}", System.lineSeparator(), report);
        } catch (Throwable t) {
            logger.error("FK-Report rendering failed: {}", t.toString(), t);
        }
    }
}
