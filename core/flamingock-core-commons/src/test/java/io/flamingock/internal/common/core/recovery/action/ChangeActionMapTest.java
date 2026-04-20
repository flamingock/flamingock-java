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
package io.flamingock.internal.common.core.recovery.action;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChangeActionMapTest {

    @Test
    @DisplayName("Should return true when map contains MANUAL_INTERVENTION")
    void shouldReturnTrueWhenMapContainsManualIntervention() {
        Map<String, ChangeAction> map = new HashMap<>();
        map.put("change-1", ChangeAction.MANUAL_INTERVENTION);
        assertTrue(new ChangeActionMap(map).hasManualInterventionActions());
    }

    @Test
    @DisplayName("Should return false when map has only APPLY and SKIP")
    void shouldReturnFalseWhenMapHasOnlyApplyAndSkip() {
        Map<String, ChangeAction> map = new HashMap<>();
        map.put("change-1", ChangeAction.APPLY);
        map.put("change-2", ChangeAction.SKIP);
        assertFalse(new ChangeActionMap(map).hasManualInterventionActions());
    }

    @Test
    @DisplayName("Should return false when map is empty")
    void shouldReturnFalseWhenMapIsEmpty() {
        assertFalse(new ChangeActionMap(Collections.emptyMap()).hasManualInterventionActions());
    }

    @Test
    @DisplayName("Should return true when multiple changes and one is MANUAL_INTERVENTION")
    void shouldReturnTrueWhenMultipleChangesAndOneIsManualIntervention() {
        Map<String, ChangeAction> map = new HashMap<>();
        map.put("change-1", ChangeAction.APPLY);
        map.put("change-2", ChangeAction.SKIP);
        map.put("change-3", ChangeAction.MANUAL_INTERVENTION);
        assertTrue(new ChangeActionMap(map).hasManualInterventionActions());
    }
}
