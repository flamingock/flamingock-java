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
package io.flamingock.internal.common.core.response.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Result data for a stage. Two complementary dimensions, each owned by a distinct writer:
 *
 * <ul>
 *   <li>{@link #state} ({@link StageState}) — execution dimension. Owned by the operation;
 *       moves NOT_STARTED → STARTED → COMPLETED / FAILED / BLOCKED_MI as the executor runs.
 *       The predicate "executor was invoked on this stage" is exactly {@code !state.isNotStarted()}.</li>
 *   <li>{@link #plannerVerdict} ({@link PlannerVerdict}) — audit/snapshot dimension. Owned by
 *       the planner; set only while {@code state} is still {@code NOT_STARTED}.</li>
 * </ul>
 *
 * <p>The two writers never overlap on a field. Per-change records ({@link #changes}) accept
 * writes from both via a defensive merge: operation wins, planner fills gaps.
 */
public class StageResult {

    private String stageId;
    private String stageName;
    private StageState state;
    private long durationMs;
    private List<ChangeResult> changes;

    /**
     * Structural change count from the loaded pipeline — total changes declared on this stage,
     * regardless of how many were actually evaluated this run. Populated by
     * {@code PipelineRun.toResponse()} for every stage so the reporter can render
     * "Not reached" rows with accurate "(N changes)" counts even when {@link #changes} is empty.
     */
    private int totalChanges;

    /**
     * Planner's view of the stage, derived from audit (community) or the cloud server's response.
     * Default {@link PlannerVerdict#NOT_EVALUATED}; transitions monotone-forward to
     * {@link PlannerVerdict#NEEDS_WORK} or {@link PlannerVerdict#UP_TO_DATE}. Written only while
     * {@link #state} is still {@code NOT_STARTED} — once execution has begun, state is the truth.
     */
    private PlannerVerdict plannerVerdict = PlannerVerdict.NOT_EVALUATED;

    public StageResult() {
        this.changes = new ArrayList<>();
        this.state = StageState.NOT_STARTED;
    }

    private StageResult(Builder builder) {
        this.stageId = builder.stageId;
        this.stageName = builder.stageName;
        this.state = builder.state != null ? builder.state : StageState.NOT_STARTED;
        this.durationMs = builder.durationMs;
        this.changes = builder.changes != null ? builder.changes : new ArrayList<>();
        this.totalChanges = builder.totalChanges;
        this.plannerVerdict = builder.plannerVerdict != null ? builder.plannerVerdict : PlannerVerdict.NOT_EVALUATED;
    }

    public String getStageId() {
        return stageId;
    }

    public void setStageId(String stageId) {
        this.stageId = stageId;
    }

    public String getStageName() {
        return stageName;
    }

    public void setStageName(String stageName) {
        this.stageName = stageName;
    }

    public StageState getState() {
        return state;
    }

    public void setState(StageState state) {
        this.state = state;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public List<ChangeResult> getChanges() {
        return changes;
    }

    public void setChanges(List<ChangeResult> changes) {
        this.changes = changes;
    }

    public int getTotalChanges() {
        return totalChanges;
    }

    public void setTotalChanges(int totalChanges) {
        this.totalChanges = totalChanges;
    }

    public PlannerVerdict getPlannerVerdict() {
        return plannerVerdict;
    }

    public void setPlannerVerdict(PlannerVerdict plannerVerdict) {
        this.plannerVerdict = plannerVerdict != null ? plannerVerdict : PlannerVerdict.NOT_EVALUATED;
    }

    public boolean isFailed() {
        return state.isFailed();
    }

    public boolean isCompleted() {
        return state.isCompleted();
    }

    public int getAppliedCount() {
        return (int) changes.stream()
                .filter(ChangeResult::isApplied)
                .count();
    }

    public int getAlreadyAppliedCount() {
        return (int) changes.stream()
                .filter(ChangeResult::isAlreadyApplied)
                .count();
    }

    public int getFailedCount() {
        return (int) changes.stream()
                .filter(ChangeResult::isFailed)
                .count();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a builder pre-populated from an existing result (useful for transitions that
     * preserve identity fields and tweak the state).
     */
    public static Builder builder(StageResult source) {
        return new Builder()
                .stageId(source.stageId)
                .stageName(source.stageName)
                .state(source.state)
                .durationMs(source.durationMs)
                .changes(new ArrayList<>(source.changes))
                .totalChanges(source.totalChanges)
                .plannerVerdict(source.plannerVerdict);
    }

    public static class Builder {
        private String stageId;
        private String stageName;
        private StageState state;
        private long durationMs;
        private List<ChangeResult> changes = new ArrayList<>();
        private int totalChanges;
        private PlannerVerdict plannerVerdict = PlannerVerdict.NOT_EVALUATED;

        public Builder stageId(String stageId) {
            this.stageId = stageId;
            return this;
        }

        public Builder stageName(String stageName) {
            this.stageName = stageName;
            return this;
        }

        public Builder state(StageState state) {
            this.state = state;
            return this;
        }

        public Builder durationMs(long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public Builder changes(List<ChangeResult> changes) {
            this.changes = changes;
            return this;
        }

        public Builder addChange(ChangeResult change) {
            if (this.changes == null) {
                this.changes = new ArrayList<>();
            }
            this.changes.add(change);
            return this;
        }

        public Builder totalChanges(int totalChanges) {
            this.totalChanges = totalChanges;
            return this;
        }

        public Builder plannerVerdict(PlannerVerdict plannerVerdict) {
            this.plannerVerdict = plannerVerdict != null ? plannerVerdict : PlannerVerdict.NOT_EVALUATED;
            return this;
        }

        public StageResult build() {
            return new StageResult(this);
        }
    }
}
