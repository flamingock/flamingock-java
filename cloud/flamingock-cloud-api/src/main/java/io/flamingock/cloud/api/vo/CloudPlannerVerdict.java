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
package io.flamingock.cloud.api.vo;

/**
 * Per-stage verdict synthesised by the cloud server from the audit-store snapshot and the
 * client's reported state. Mirrors the core-side {@code PlannerVerdict} enum and feeds the
 * client's {@code PipelineRun.markStageVerdict} writer verbatim.
 *
 * <p>Monotone-forward semantics on the client side: NOT_EVALUATED → NEEDS_WORK → UP_TO_DATE.
 * The server is expected to emit one of these per stage in every {@code pipelineResult}
 * response it returns; the client trusts the server's value without further interpretation.
 */
public enum CloudPlannerVerdict {

    /**
     * The server did not make a determination for this stage. Reserved for genuinely
     * degenerate cases — every stage in {@code pipelineResult} is expected to be evaluated
     * post-rollout.
     */
    NOT_EVALUATED,

    /**
     * At least one change in this stage is not in an applied state per the server's audit.
     * The stage has work pending or had work the executor did not reach this run.
     */
    NEEDS_WORK,

    /**
     * Every change in the stage is confirmed applied per the server's audit. Authoritative —
     * the executor was not invoked for this stage and does not need to be.
     */
    UP_TO_DATE
}
