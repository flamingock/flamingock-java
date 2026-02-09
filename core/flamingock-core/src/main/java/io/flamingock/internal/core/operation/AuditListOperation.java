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
package io.flamingock.internal.core.operation;

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.core.external.store.audit.AuditPersistence;

import java.util.List;
import java.util.stream.Collectors;

public class AuditListOperation implements Operation<AuditListArgs, AuditListResult>{

    private final AuditPersistence persistence;

    public AuditListOperation(AuditPersistence persistence) {
        this.persistence = persistence;
    }

    @Override
    public AuditListResult execute(AuditListArgs args) {
        // Step 1: Get base data based on --history flag
        List<AuditEntry> entries = args.isHistory()
                ? persistence.getAuditHistory()
                : persistence.getAuditSnapshot();

        // Step 2: Apply --since filter if present (works on both modes)
        if (args.getSince() != null) {
            entries = entries.stream()
                    .filter(e -> e.getCreatedAt() != null && !e.getCreatedAt().isBefore(args.getSince()))
                    .collect(Collectors.toList());
        }

        // Step 3: Return with extended flag
        return new AuditListResult(entries, args.isExtended());
    }
}
