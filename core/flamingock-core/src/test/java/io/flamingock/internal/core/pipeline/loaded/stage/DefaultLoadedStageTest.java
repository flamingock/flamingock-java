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
package io.flamingock.internal.core.pipeline.loaded.stage;

import io.flamingock.api.StageType;
import io.flamingock.api.StageCategory;
import io.flamingock.internal.common.core.error.validation.ValidationError;
import io.flamingock.internal.core.change.loaded.AbstractLoadedChange;
import io.flamingock.internal.core.pipeline.loaded.PipelineValidationContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultLoadedStageTest {

    @Test
    @DisplayName("STANDARD native stage rejects runAlways changes")
    void standardStageRejectsRunAlwaysChanges() {
        DefaultLoadedStage stage = new DefaultLoadedStage(
                "standard",
                StageCategory.DEFAULT,
                StageType.STANDARD,
                Collections.singletonList(change("change-1", true)));

        List<ValidationError> errors = stage.getValidationErrors(mock(PipelineValidationContext.class));

        assertTrue(hasMessage(errors, "runAlways changes are only allowed in RUN_ALWAYS stages"));
    }

    @Test
    @DisplayName("RUN_ALWAYS native stage rejects non-runAlways changes")
    void runAlwaysStageRejectsNonRunAlwaysChanges() {
        DefaultLoadedStage stage = new DefaultLoadedStage(
                "repeatable",
                StageCategory.DEFAULT,
                StageType.RUN_ALWAYS,
                Collections.singletonList(change("change-1", false)));

        List<ValidationError> errors = stage.getValidationErrors(mock(PipelineValidationContext.class));

        assertTrue(hasMessage(errors, "RUN_ALWAYS stages can only contain runAlways changes"));
    }

    @Test
    @DisplayName("LEGACY stage allows mixed runAlways values")
    void legacyStageAllowsMixedRunAlwaysValues() {
        LegacyLoadedStage stage = new LegacyLoadedStage(
                "legacy",
                StageCategory.LEGACY,
                Arrays.asList(legacyChange("change-1", true), legacyChange("change-2", false)));

        List<ValidationError> errors = stage.getValidationErrors(mock(PipelineValidationContext.class));

        assertFalse(hasMessage(errors, "runAlways"));
    }

    private static boolean hasMessage(List<ValidationError> errors, String text) {
        return errors.stream().anyMatch(error -> error.getMessage().contains(text));
    }

    private static AbstractLoadedChange change(String id, boolean runAlways) {
        AbstractLoadedChange change = mock(AbstractLoadedChange.class);
        when(change.getId()).thenReturn(id);
        when(change.isStandard()).thenReturn(true);
        when(change.isRunAlways()).thenReturn(runAlways);
        when(change.getValidationErrors(any(StageValidationContext.class))).thenReturn(Collections.emptyList());
        return change;
    }

    private static AbstractLoadedChange legacyChange(String id, boolean runAlways) {
        AbstractLoadedChange change = change(id, runAlways);
        when(change.isStandard()).thenReturn(false);
        when(change.isLegacy()).thenReturn(true);
        return change;
    }
}
