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
package io.flamingock.internal.core.builder.ops;

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.core.store.audit.AuditPersistence;
import io.flamingock.internal.util.FlamingockLoggerFactory;
import io.flamingock.internal.util.id.RunnerId;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.List;

public class OpsClient {
    private final Logger logger = FlamingockLoggerFactory.getLogger("OpsClient");

    private final AuditPersistence auditPersistence;

    OpsClient(RunnerId runnerId, AuditPersistence auditPersistence) {
        this.auditPersistence = auditPersistence;
    }

    public void markAsSuccess(String changeUnit) {
        logger.info("ChangeUnit[{}] marked as success", changeUnit);
    }

    public void markAsRolledBack(String changeUnit) {
        logger.info("ChangeUnit[{}] marked as rolled back", changeUnit);
    }

    public List<AuditEntry> getConflictedAuditEntries() {
        logger.info("Listing audit entires");
        return Collections.emptyList();
    }
}
