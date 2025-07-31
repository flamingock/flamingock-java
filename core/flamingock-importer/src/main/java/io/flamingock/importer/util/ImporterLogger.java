/*
 * Copyright 2025 Flamingock (https://oss.flamingock.io)
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
 
package io.flamingock.importer.util;

import io.flamingock.importer.ImporterAdapter;
import io.flamingock.internal.common.core.audit.AuditWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImporterLogger {

    private final Logger logger;

    public ImporterLogger(String loggerName) {
        this.logger = LoggerFactory.getLogger(loggerName);;
    }

    public void logStart(ImporterAdapter importerAdapter, AuditWriter auditWriter) {
        logger.info("Importing audit log from [ {}] to Flamingock[{}]",
                importerAdapter.getClass().getSimpleName(),
                auditWriter.isCloud() ? "Cloud" : "CE");
    }
}
