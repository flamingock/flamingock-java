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
package io.flamingock.internal.common.core.pipeline;

import io.flamingock.api.RecoveryStrategy;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.change.ChangeDescriptor;
import io.flamingock.internal.common.core.context.ContextInjectable;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static io.flamingock.internal.common.core.pipeline.PipelineHelper.LEGACY_STAGE_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineHelperTest {

    private final PipelineHelper pipelineHelper = new PipelineHelper(new PipelineDescriptor() {
        @Override
        public Optional<? extends ChangeDescriptor> getLoadedChange(String changeId) {
            return Optional.empty();
        }

        @Override
        public Optional<String> getStageByChange(String changeId) {
            return "known-change".equals(changeId) ? Optional.of("user-stage") : Optional.empty();
        }

        @Override
        public void contributeToContext(ContextInjectable contextInjectable) {
            // no-op for unit test
        }
    });

    @Test
    void shouldReturnLegacyStageForSystemChange() {
        Optional<String> stageId = pipelineHelper.findStageId(buildAuditEntry("system-change-1", true));

        assertEquals(Optional.of(LEGACY_STAGE_ID), stageId);
    }

    @Test
    void shouldReturnMatchingStageForKnownChange() {
        Optional<String> stageId = pipelineHelper.findStageId(buildAuditEntry("known-change", false));

        assertEquals(Optional.of("user-stage"), stageId);
    }

    @Test
    void shouldReturnEmptyForUnknownChange() {
        Optional<String> stageId = pipelineHelper.findStageId(buildAuditEntry("unknown-change", false));

        assertFalse(stageId.isPresent());
    }

    private static AuditEntry buildAuditEntry(String changeId, boolean systemChange) {
        return new AuditEntry(
                "exec-1",
                null,
                changeId,
                "author",
                LocalDateTime.now(),
                AuditEntry.Status.APPLIED,
                AuditEntry.ChangeType.MONGOCK_EXECUTION,
                "io.example.Change",
                "apply",
                null,
                10L,
                "host",
                null,
                systemChange,
                null,
                null,
                null,
                null,
                RecoveryStrategy.MANUAL_INTERVENTION,
                null
        );
    }
}
