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
package io.flamingock.internal.common.core.pipeline;

import io.flamingock.internal.common.core.audit.AuditEntry;

import java.util.Optional;

public class PipelineHelper {

    public static final String SYSTEM_STAGE_ID = "flamingock-system-stage";
    public static final String LEGACY_STAGE_ID = "flamingock-legacy-stage";

    private final PipelineDescriptor pipelineDescriptor;

    public PipelineHelper(PipelineDescriptor pipelineDescriptor) {
        this.pipelineDescriptor = pipelineDescriptor;
    }

    public Optional<String> findStageId(AuditEntry auditEntryFromOrigin) {
        if (Boolean.TRUE.equals(auditEntryFromOrigin.getSystemChange())) {
            return Optional.of(LEGACY_STAGE_ID);
        }
        String changeIdInPipeline = getBaseChangeId(auditEntryFromOrigin);
        return pipelineDescriptor.getStageByChange(changeIdInPipeline);
    }

    public String getBaseChangeId(AuditEntry auditEntry) {
        String originalChangeId = auditEntry.getChangeId();
        int index = originalChangeId.indexOf("_before");
        return index >= 0 ? originalChangeId.substring(0, index) : originalChangeId;
    }

    public String getStorableChangeId(AuditEntry auditEntry) {
        return auditEntry.getChangeId();
    }
}
