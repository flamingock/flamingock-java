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
package io.flamingock.internal.core.pipeline.run;

import io.flamingock.internal.common.core.response.data.ChangeResult;
import io.flamingock.internal.common.core.response.data.ChangeStatus;
import io.flamingock.internal.common.core.response.data.StageResult;
import io.flamingock.internal.common.core.response.data.StageState;
import io.flamingock.internal.core.change.loaded.AbstractLoadedChange;
import io.flamingock.internal.core.pipeline.loaded.stage.AbstractLoadedStage;

public class StageRun {

    private final AbstractLoadedStage loadedStage;
    private StageResult result;

    public StageRun(AbstractLoadedStage loadedStage) {
        this.loadedStage = loadedStage;
        // Every loaded change starts with a ChangeResult.NOT_REACHED record. Writers (operation
        // via executor, planner via audit) transition records forward as they learn facts; any
        // record still NOT_REACHED at end-of-run literally means "neither writer had positive
        // info about this change."
        StageResult.Builder builder = StageResult.builder()
                .stageId(loadedStage.getName())
                .stageName(loadedStage.getName())
                .state(StageState.NOT_STARTED);
        if (loadedStage.getChanges() != null) {
            for (AbstractLoadedChange change : loadedStage.getChanges()) {
                builder.addChange(ChangeResult.builder()
                        .changeId(change.getId())
                        .status(ChangeStatus.NOT_REACHED)
                        .build());
            }
        }
        this.result = builder.build();
    }

    public String getName() {
        return loadedStage.getName();
    }

    public AbstractLoadedStage getLoadedStage() {
        return loadedStage;
    }

    public StageResult getResult() {
        return result;
    }

    public StageState getState() {
        return result.getState();
    }

    void setResult(StageResult result) {
        this.result = result;
    }
}
