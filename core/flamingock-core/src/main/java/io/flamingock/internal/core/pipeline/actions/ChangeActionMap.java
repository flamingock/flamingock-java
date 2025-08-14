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
package io.flamingock.internal.core.pipeline.actions;

import java.util.Collections;
import java.util.Map;

/**
 * Contains the action plan for a stage, specifying which action should be taken
 * for each change during execution. This provides a clean abstraction for both
 * community (audit-based) and cloud (orchestrator-based) execution planning.
 */
public class ChangeActionMap {

    private final Map<String, ChangeAction> actionMap;

    public ChangeActionMap(Map<String, ChangeAction> actionMap) {
        this.actionMap = actionMap != null ? Collections.unmodifiableMap(new java.util.HashMap<>(actionMap)) : Collections.emptyMap();
    }

    /**
     * Returns the action to be taken for the specified change ID.
     * If no audit entry exists for the given ID, it is assumed its first execution.
     *
     * @param changeId the unique identifier for the change
     * @return the action to be taken, or APPLY if no action is specified for this change
     */
    public ChangeAction getActionFor(String changeId) {
        return actionMap.getOrDefault(changeId, ChangeAction.APPLY);
    }

    /**
     * Returns an immutable copy of the action map.
     *
     * @return the complete action map for the stage
     */
    public Map<String, ChangeAction> getActionMap() {
        return actionMap;
    }

    /**
     * Returns true if the action plan is empty (no actions specified).
     * 
     * @return true if the action plan is empty
     */
    public boolean isEmpty() {
        return actionMap.isEmpty();
    }

    /**
     * Returns the number of changes in the action plan.
     * 
     * @return the number of changes in the action plan
     */
    public int size() {
        return actionMap.size();
    }

    @Override
    public String toString() {
        return String.format("StageActionPlan{size=%d, actions=%s}", actionMap.size(), actionMap);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChangeActionMap)) return false;

        ChangeActionMap that = (ChangeActionMap) o;
        return actionMap.equals(that.actionMap);
    }

    @Override
    public int hashCode() {
        return actionMap.hashCode();
    }
}