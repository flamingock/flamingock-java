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
package io.flamingock.internal.common.core.preview;

import io.flamingock.api.StageType;
import io.flamingock.api.StageCategory;
import io.flamingock.internal.common.core.change.RecoveryDescriptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewStageTest {

    @Test
    @DisplayName("RUN_ALWAYS default stage marks all preview changes as runAlways")
    void runAlwaysDefaultStageMarksAllChanges() {
        AbstractPreviewChange change = previewChange("change-1", false);

        PreviewStage stage = PreviewStage.defaultBuilder(StageCategory.DEFAULT)
                .setName("repeatable")
                .setType(StageType.RUN_ALWAYS)
                .setChanges(Collections.singletonList(change))
                .build();

        assertEquals(StageType.RUN_ALWAYS, stage.getType());
        assertTrue(stage.getChanges().iterator().next().isRunAlways());
    }

    @Test
    @DisplayName("STANDARD default stage keeps preview changes non-runAlways")
    void standardDefaultStageKeepsChangesNonRunAlways() {
        AbstractPreviewChange change = previewChange("change-1", false);

        PreviewStage stage = PreviewStage.defaultBuilder(StageCategory.DEFAULT)
                .setName("standard")
                .setChanges(Collections.singletonList(change))
                .build();

        assertEquals(StageType.STANDARD, stage.getType());
        assertFalse(stage.getChanges().iterator().next().isRunAlways());
    }

    @Test
    @DisplayName("LEGACY stage ignores stage type runAlways")
    void legacyStageIgnoresStageTypeRunAlways() {
        AbstractPreviewChange change = previewChange("change-1", false);

        PreviewStage stage = PreviewStage.defaultBuilder(StageCategory.LEGACY)
                .setName("legacy")
                .setType(StageType.RUN_ALWAYS)
                .setChanges(Collections.singletonList(change))
                .build();

        assertEquals(StageType.STANDARD, stage.getType());
        assertFalse(stage.getChanges().iterator().next().isRunAlways());
    }

    private static AbstractPreviewChange previewChange(String id, boolean runAlways) {
        return new AbstractPreviewChange(
                id,
                "001",
                "author",
                "source",
                "source-file",
                runAlways,
                true,
                false,
                null,
                RecoveryDescriptor.getDefault(),
                false) {
        };
    }
}
