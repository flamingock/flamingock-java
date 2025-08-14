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
package io.flamingock.internal.common.cloud.planner.response;


/**
 * Represents the cloud orchestrator's decision about what action should be taken
 * for a specific task. This enum maintains separation from the internal ChangeAction
 * to preserve cloud domain boundaries while keeping aligned enum values.
 */
public enum CloudChangeAction {
    
    /**
     * The task needs to be applied/executed - cloud orchestrator determined it should run.
     * Maps to ChangeAction.APPLY.
     */
    APPLY,
    
    /**
     * The task should be skipped as it has already been successfully executed.
     * Maps to ChangeAction.SKIP.
     */
    SKIP,
    
    /**
     * Manual intervention is required for this task.
     * Maps to ChangeAction.MANUAL_INTERVENTION.
     */
    MANUAL_INTERVENTION
}