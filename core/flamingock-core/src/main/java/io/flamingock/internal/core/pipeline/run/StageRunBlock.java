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

import io.flamingock.api.StageType;

import java.util.Collections;
import java.util.List;

/**
 * Typed window over a group of {@link StageRun}s sharing the same {@link StageType}. Blocks are
 * the structural unit of execution dependency: blocks are returned by
 * {@link PipelineRun#getStageBlocks()} in dependency order, and a later block depends on the
 * successful completion of every prior block.
 *
 * <p>This class is intentionally a thin abstraction. It exposes only:
 * <ul>
 *   <li>The block's {@link StageType}.</li>
 *   <li>The {@link StageRun}s belonging to it (shared references with {@link PipelineRun}).</li>
 *   <li>Predicates over the block's collective state: {@link #isTerminal()},
 *       {@link #isSuccessful()}, {@link #hasFailures()}.</li>
 * </ul>
 *
 * <p>It does NOT decide which stage is "next to execute" — that is the planner's responsibility.
 * Consumers wanting the next eligible stage iterate {@link #getStageRuns()} and apply their own
 * filter.
 */
public final class StageRunBlock {

    private final StageType type;
    private final List<StageRun> stageRuns;

    StageRunBlock(StageType type, List<StageRun> stageRuns) {
        this.type = type;
        this.stageRuns = Collections.unmodifiableList(stageRuns);
    }

    public StageType getType() {
        return type;
    }

    public List<StageRun> getStageRuns() {
        return stageRuns;
    }

    /**
     * Every stage in this block has reached a terminal state (Completed / Failed / BlockedForMI).
     * Vacuous true for an empty block.
     */
    public boolean isTerminal() {
        for (StageRun stageRun : stageRuns) {
            if (!isTerminalState(stageRun)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Every stage in this block ended in {@code Completed}. Vacuous true for an empty block.
     * Precondition for the next block to safely begin under the future dependency-gating planner.
     */
    public boolean isSuccessful() {
        for (StageRun stageRun : stageRuns) {
            if (!stageRun.getState().isCompleted()) {
                return false;
            }
        }
        return true;
    }

    /** At least one stage in this block is {@code Failed} or {@code BlockedForMI}. */
    public boolean hasFailures() {
        for (StageRun stageRun : stageRuns) {
            if (stageRun.getState().isFailed()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTerminalState(StageRun stageRun) {
        return stageRun.getState().isCompleted() || stageRun.getState().isFailed();
    }
}
