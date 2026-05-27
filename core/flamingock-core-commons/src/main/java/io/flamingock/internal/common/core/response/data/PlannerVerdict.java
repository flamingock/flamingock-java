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

/**
 * The planner's view of a stage, derived from the audit snapshot (community) or the cloud
 * server's response. One of two complementary dimensions on {@code StageResult}:
 *
 * <ul>
 *   <li>{@code state} ({@link StageState}) — what the operation observed during execution
 *       (NOT_STARTED → STARTED → COMPLETED / FAILED / BLOCKED_MI). Operation-only.</li>
 *   <li>{@code plannerVerdict} (this enum) — what the audit/snapshot reflects. Planner-only.</li>
 * </ul>
 *
 * <p>Operation writes are immutable to the planner. The planner sets a verdict only when the
 * stage's {@code state} is still {@code NOT_STARTED}; once the operation has reached a terminal
 * state, that state is the truth for the run.
 *
 * <p>Verdict is monotone-forward: NOT_EVALUATED → NEEDS_WORK → UP_TO_DATE. The
 * NEEDS_WORK → UP_TO_DATE transition is valid (later snapshot reads see new applies); the
 * reverse is not.
 *
 * <p>Notably absent: a "failed" verdict. Failure is an execution observation owned by
 * {@code state}. From the planner's perspective, a stage with a failed change in the audit
 * still has pending work — the recovery strategy decides whether to retry.
 */
public enum PlannerVerdict {

    /**
     * Default. The planner has not reached a determination on this stage. Transient under the
     * community planner's always-walk model; persists for stages the planner couldn't evaluate.
     */
    NOT_EVALUATED,

    /**
     * The planner saw at least one change in the stage that is not in an applied state per
     * audit. The stage either has work to do or had work that wasn't reached this run.
     */
    NEEDS_WORK,

    /**
     * The planner confirmed every change in the stage is applied per audit. Authoritative —
     * the executor was not invoked and does not need to be. Renders as {@code [UP TO DATE]}.
     */
    UP_TO_DATE
}
