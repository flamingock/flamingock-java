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

package io.flamingock.importer.cloud.common;

import io.flamingock.core.api.annotations.ChangeUnit;
import io.flamingock.core.api.annotations.Execution;
import io.flamingock.core.api.annotations.NonLockGuarded;
import io.flamingock.core.api.annotations.SystemChange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@SystemChange
@ChangeUnit(id = "mongock-legacy-importer-v1", order = "1")
public class MongockLegacyImporterChangeUnit {

    private static final Logger logger = LoggerFactory.getLogger(MongockLegacyImporterChangeUnit.class);

    @Execution
    public void execution(@NonLockGuarded MongockLegacyImporterConfiguration configuration) {

        List<MongockLegacyAuditEntry> data = configuration.readMongockLegacyAuditEntries();

        ImporterService importerService = new ImporterService(
                configuration.getServerHost(),
                configuration.getEnvironmentId().toString(),
                configuration.getServiceId().toString(),
                configuration.getJwt()
        );

        try {
            importerService.send(data);

        } catch (Throwable throwable) {
            logger.error("Error writing legacy audit:\n{}", throwable.toString());
            throw throwable;
        }
    }
}
