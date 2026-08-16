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
 * Per-change status carried in both directions:
 *
 * <ul>
 *   <li>In {@code ChangeRequest.currentStatus}: the client tells the server what its
 *       operation-side {@code ChangeResult.status} currently holds, so the server can apply
 *       its "client report is informational input" rule without contradicting the operation's
 *       prior writes.</li>
 *   <li>In {@code ChangeResultResponse.status}: the server's synthesised per-change status
 *       reconciled from audit + client report. Mirrors the core-side {@code ChangeStatus}
 *       enum and feeds the client's {@code PipelineRun} writers verbatim.</li>
 * </ul>
 *
 * <p>Source of truth for the synthesis is the audit store on the server; the client's report
 * is informational input the server respects (e.g. a {@code FAILED} report breaks the retry
 * loop on a still-pending change).
 */
public enum CloudChangeStatus {

    /**
     * No positive information: the operation did not process this change this run (executor
     * stopped on an earlier failure, stage was unreached, etc.) and the server's audit holds
     * no terminal entry.
     */
    NOT_REACHED,

    /**
     * The change was applied during this run by the operation; the server's audit confirms
     * it. Distinct from {@link #ALREADY_APPLIED} because the client correctly reports the
     * act of applying it this run.
     */
    APPLIED,

    /**
     * The server's audit confirms the change is already applied from a prior run (or from a
     * CLI {@code mark-as-applied} / external write). The operation did not have to invoke
     * the executor for this change.
     */
    ALREADY_APPLIED,

    /**
     * The change failed during execution this run. The server respects the failure and uses
     * the configured recovery strategy to decide whether to re-offer it.
     */
    FAILED,

    /**
     * The change failed during execution this run and was successfully rolled back
     * (transactional auto-rollback).
     */
    ROLLED_BACK
}
