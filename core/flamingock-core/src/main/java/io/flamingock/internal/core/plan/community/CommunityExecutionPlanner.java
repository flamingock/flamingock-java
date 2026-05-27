/*
 * Copyright 2023 Flamingock (https://www.flamingock.io)
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
package io.flamingock.internal.core.plan.community;


import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.recovery.action.ChangeActionMap;
import io.flamingock.internal.common.core.response.data.PlannerVerdict;
import io.flamingock.internal.core.change.loaded.AbstractLoadedChange;
import io.flamingock.internal.core.plan.ExecutionId;
import io.flamingock.internal.core.external.store.lock.community.CommunityLock;
import io.flamingock.internal.core.external.store.lock.community.CommunityLockService;
import io.flamingock.internal.core.configuration.core.CoreConfigurable;

import io.flamingock.internal.core.plan.ExecutionPlan;
import io.flamingock.internal.core.plan.ExecutionPlanner;
import io.flamingock.internal.core.external.store.lock.Lock;
import io.flamingock.internal.core.external.store.lock.LockException;
import io.flamingock.internal.core.external.store.lock.LockRefreshDaemon;
import io.flamingock.internal.core.pipeline.execution.ExecutableStage;
import io.flamingock.internal.core.pipeline.loaded.stage.AbstractLoadedStage;
import io.flamingock.internal.core.pipeline.run.PipelineRun;
import io.flamingock.internal.core.pipeline.run.StageRun;
import io.flamingock.internal.core.pipeline.run.StageRunBlock;
import io.flamingock.internal.core.external.store.audit.community.CommunityAuditReader;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.internal.util.TimeService;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class CommunityExecutionPlanner extends ExecutionPlanner {
    private static final Logger logger = FlamingockLoggerFactory.getLogger("LocalExecution");

    private final CommunityAuditReader auditReader;
    private final CommunityLockService lockService;

    private final CoreConfigurable configuration;
    private final RunnerId instanceId;

    public static Builder builder() {
        return new Builder();
    }


    /**
     * @param instanceId the runner instance identifier
     * @param lockService lockService to persist the lock
     * @param auditReader audit reader to get current audit state
     * @param coreConfiguration core configuration settings
     */
    public CommunityExecutionPlanner(RunnerId instanceId,
                                     CommunityLockService lockService,
                                     CommunityAuditReader auditReader,
                                     CoreConfigurable coreConfiguration) {
        this.instanceId = instanceId;
        this.auditReader = auditReader;
        this.lockService = lockService;
        this.configuration = coreConfiguration;
    }

    /**
     * Gets the next execution plan using a two-phase audit read to prevent race conditions in concurrent executions.
     *
     * <p><b>How it works:</b></p>
     *
     * <ol>
     *   <li><b>Initial Audit Read (Optimistic)</b> - Reads the audit log without holding the lock to determine
     *       if any changes need execution. This avoids acquiring the lock when all changes are already executed.</li>
     *
     *   <li><b>Early Exit Check</b> - If no changes require execution based on the initial read, returns immediately
     *       without acquiring the lock.</li>
     *
     *   <li><b>Lock Acquisition</b> - Acquires the lock to ensure only one instance executes changes.
     *       If another instance holds the lock, this call blocks until the lock becomes available.</li>
     *
     *   <li><b>Validated Audit Read (With Lock)</b> - Re-reads the audit log while holding the lock to get
     *       the authoritative state. This detects if another instance executed changes while we waited for the lock.</li>
     *
     *   <li><b>Plan Validation</b> - Rebuilds the execution plan with the validated audit data and checks if
     *       execution is still needed. If another instance already executed the changes, releases the lock
     *       and returns without executing.</li>
     *
     *   <li><b>Concurrent Execution Detection</b> - Logs any differences between the initial and validated plans
     *       to track when concurrent instances attempted to execute the same changes.</li>
     *
     *   <li><b>Lock Refresh Daemon</b> - If enabled, starts a background daemon to periodically refresh the lock
     *       during long-running executions to prevent lock expiration.</li>
     *
     *   <li><b>Execution</b> - Returns an execution plan containing the next stage to execute while holding the lock.
     *       The lock will be released after execution completes.</li>
     * </ol>
     *
     * <p><b>Concurrent Execution Handling:</b></p>
     * <pre>
     * Instance A: Read audit → Get lock → Re-read → Execute → Release
     * Instance B: Read audit → Wait for lock → Get lock → Re-read → Detect already executed → Release
     * </pre>
     *
     * <p><b>Error Handling:</b> If any exception occurs after acquiring the lock, the lock is released
     * in the catch block to prevent lock leaks.</p>
     *
     * @param pipelineRun the in-flight run aggregate. The planner walks
     *                    {@link PipelineRun#getStageBlocks()} in dependency order and plans from
     *                    the first non-terminal block. Blocks already terminal+successful are
     *                    skipped; if a terminal block has failures, the planner returns
     *                    {@code ABORT} so the operation stops before later-dependent blocks run.
     * @return ExecutionPlan: {@code newExecution} with a single stage when there's work,
     *         {@code CONTINUE} when the pipeline is fully done, or {@code ABORT} when an earlier
     *         block has failures and downstream work must not proceed.
     * @throws LockException if unable to acquire the distributed lock within the configured timeout
     */
    @Override
    public ExecutionPlan getNextExecution(PipelineRun pipelineRun) throws LockException {
        // Always-walk: every iteration starts by stamping the planner's view onto every stage in
        // the pipeline (not just the active block). The result is that the final report carries
        // authoritative info for every stage the planner could evaluate — even on ABORT, future
        // blocks render as [UP TO DATE] when the audit confirms them, rather than [NOT REACHED].
        //
        // Defensive merge in the writer methods means re-walking each iteration never overwrites
        // operation-written state or duplicates per-change records.
        while (true) {
            Map<String, AuditEntry> initialSnapshot = auditReader.getAuditSnapshotByChangeId();
            logger.debug("Pulled initial remote state:\n{}", initialSnapshot);
            stampSnapshotFacts(pipelineRun, initialSnapshot);

            BlockSelection selection = selectActiveBlock(pipelineRun);
            if (selection.isAborted()) {
                return ExecutionPlan.ABORT();
            }
            if (!selection.getActiveBlock().isPresent()) {
                return ExecutionPlan.CONTINUE();
            }

            StageRunBlock activeBlock = selection.getActiveBlock().get();
            List<AbstractLoadedStage> loadedStages = eligibleLoadedStagesFor(activeBlock);
            List<ExecutableStage> initialStages = buildExecutableStages(loadedStages, initialSnapshot);

            if (!hasExecutableStages(initialStages)) {
                // Defensive — should be unreachable: stampSnapshotFacts already marked the
                // active block UP_TO_DATE if it had no executable work, and selectActiveBlock
                // would have advanced past it.
                continue;
            }

            return planWorkUnderLock(loadedStages, initialStages);
        }
    }

    private ExecutionPlan planWorkUnderLock(List<AbstractLoadedStage> loadedStages,
                                            List<ExecutableStage> initialStages) throws LockException {
        Lock lock = acquireLock();

        try {
            Map<String, AuditEntry> validatedSnapshot = auditReader.getAuditSnapshotByChangeId();

            List<ExecutableStage> validatedStages = buildExecutableStages(loadedStages, validatedSnapshot);

            Optional<ExecutableStage> nextStageOpt = getFirstExecutableStage(validatedStages);

            if (!nextStageOpt.isPresent()) {
                logger.info(
                    "Execution plan invalidated after lock acquisition. " +
                    "All changes were executed by another instance during lock wait. " +
                    "Releasing lock and continuing."
                );
                lock.release();
                return ExecutionPlan.CONTINUE();
            }

            logPlanChanges(initialStages, validatedStages);

            lock.startDaemonIfEnabled();

            String executionId = ExecutionId.getNewExecutionId();
            return ExecutionPlan.newExecution(
                executionId,
                lock,
                Collections.singletonList(nextStageOpt.get())
            );

        } catch (Exception e) {
            logger.error("Error during execution planning - releasing lock", e);
            lock.release();
            throw e;
        }
    }

    /**
     * Walks {@link PipelineRun#getStageBlocks()} in dependency order and decides what to do:
     * <ul>
     *   <li>First non-terminal block → that's the active block. Plan from it.</li>
     *   <li>Block is terminal + has failures → ABORT (downstream blocks must not run).</li>
     *   <li>Block is terminal + successful → skip and check the next block.</li>
     *   <li>All blocks terminal + successful → pipeline is fully done, return ALL_DONE.</li>
     * </ul>
     */
    private static BlockSelection selectActiveBlock(PipelineRun pipelineRun) {
        for (StageRunBlock block : pipelineRun.getStageBlocks()) {
            if (!block.isTerminal()) {
                return BlockSelection.proceed(block);
            }
            if (block.hasFailures()) {
                return BlockSelection.ABORT;
            }
            // terminal + successful → move to next block
        }
        return BlockSelection.ALL_DONE;
    }

    /**
     * Eligible loaded stages within the active block: every stage in the block whose runtime
     * state is not yet a terminal failed shape ({@code Failed} / {@code BlockedForMI}). Within
     * a non-terminal block these are the stages still candidates for execution.
     */
    private static List<AbstractLoadedStage> eligibleLoadedStagesFor(StageRunBlock activeBlock) {
        return activeBlock.getStageRuns().stream()
                .filter(stageRun -> !stageRun.getState().isFailed())
                .map(StageRun::getLoadedStage)
                .collect(Collectors.toList());
    }

    /**
     * Walks every stage in the pipeline and reflects the audit's view onto the {@link PipelineRun}
     * via the planner-side writers:
     * <ul>
     *   <li>Per-change: for each change whose audit entry is in a successfully-applied terminal
     *       status, add an {@code ALREADY_APPLIED} {@link io.flamingock.internal.common.core.response.data.ChangeResult}.
     *       Defensive merge in {@code markStageAlreadyAppliedFromAudit} ensures we never overwrite
     *       an operation-written record (it skips IDs the operation has already recorded for,
     *       regardless of that record's status).</li>
     *   <li>Per-stage verdict: {@code UP_TO_DATE} when every loaded change has a record (any
     *       writer) and every record is {@code ALREADY_APPLIED} or {@code APPLIED}; otherwise
     *       {@code NEEDS_WORK}. Verdict transitions are monotone-forward.</li>
     * </ul>
     *
     * <p>Walks <strong>every</strong> stage on every iteration — including stages whose state has
     * moved off {@code NOT_STARTED}. The defensive merge plus monotone-verdict invariants make
     * this safe, and it picks up audit information the operation doesn't know about: external
     * CLI marking ({@code mark-as-applied}) during a run, third-party audit-tool writes, and the
     * upcoming parallel-stage feature where multiple instances apply different (non-dependent)
     * changes from the same stage concurrently — the audit carries the full picture even though
     * this instance's {@link PipelineRun} only knows what it applied.
     */
    private static void stampSnapshotFacts(PipelineRun pipelineRun, Map<String, AuditEntry> auditSnapshot) {
        for (StageRun stageRun : pipelineRun.getStageRuns()) {
            List<String> alreadyAppliedIds = alreadyAppliedChangeIds(stageRun, auditSnapshot);
            if (!alreadyAppliedIds.isEmpty()) {
                pipelineRun.markStageAlreadyAppliedFromAudit(stageRun.getName(), alreadyAppliedIds);
            }
            PlannerVerdict verdict = computeVerdict(stageRun);
            pipelineRun.markStageVerdict(stageRun.getName(), verdict);
        }
    }

    /**
     * Returns the change IDs declared on the loaded stage whose audit entries indicate a
     * "successfully applied" terminal status. The planner uses this list to populate
     * {@code ALREADY_APPLIED} per-change records.
     */
    private static List<String> alreadyAppliedChangeIds(StageRun stageRun, Map<String, AuditEntry> auditSnapshot) {
        List<String> ids = new ArrayList<>();
        for (AbstractLoadedChange change : stageRun.getLoadedStage().getChanges()) {
            AuditEntry entry = auditSnapshot.get(change.getId());
            if (entry == null) {
                continue;
            }
            AuditEntry.Status status = entry.getState();
            if (status == AuditEntry.Status.APPLIED
                    || status == AuditEntry.Status.MANUAL_MARKED_AS_APPLIED) {
                ids.add(change.getId());
            }
        }
        return ids;
    }

    /**
     * Computes the planner's verdict for a stage based on its current per-change records vs the
     * loaded change count. {@code UP_TO_DATE} when every loaded change has a record and every
     * record is in an applied terminal status; {@code NEEDS_WORK} otherwise.
     */
    private static PlannerVerdict computeVerdict(StageRun stageRun) {
        int loadedCount = stageRun.getLoadedStage().getChanges() != null
                ? stageRun.getLoadedStage().getChanges().size()
                : 0;
        List<io.flamingock.internal.common.core.response.data.ChangeResult> records =
                stageRun.getResult().getChanges();
        if (records == null || records.size() < loadedCount) {
            return PlannerVerdict.NEEDS_WORK;
        }
        for (io.flamingock.internal.common.core.response.data.ChangeResult c : records) {
            if (c == null) {
                return PlannerVerdict.NEEDS_WORK;
            }
            if (!c.isApplied() && !c.isAlreadyApplied()) {
                return PlannerVerdict.NEEDS_WORK;
            }
        }
        return PlannerVerdict.UP_TO_DATE;
    }

    /**
     * Compact three-state value: ABORT (singleton), ALL_DONE (singleton), or PROCEED with an
     * active block. Avoids leaking the "decision shape" outside the planner.
     */
    private static final class BlockSelection {
        private static final BlockSelection ABORT = new BlockSelection(null, true);
        private static final BlockSelection ALL_DONE = new BlockSelection(null, false);

        static BlockSelection proceed(StageRunBlock block) {
            return new BlockSelection(block, false);
        }

        private final StageRunBlock activeBlock;
        private final boolean aborted;

        private BlockSelection(StageRunBlock activeBlock, boolean aborted) {
            this.activeBlock = activeBlock;
            this.aborted = aborted;
        }

        public Optional<StageRunBlock> getActiveBlock() {
            return Optional.ofNullable(activeBlock);
        }

        public boolean isAborted() {
            return aborted;
        }
    }

    private Lock acquireLock() {
        return CommunityLock.getLock(
                configuration.getLockAcquiredForMillis(),
                configuration.getLockQuitTryingAfterMillis(),
                configuration.getLockTryFrequencyMillis(),
                instanceId,
                lockService,
                TimeService.getDefault(),
                configuration.isEnableRefreshDaemon()
        );
    }

    /**
     * Builds executable stages from audit snapshot.
     *
     * @param loadedStages the loaded stages to process
     * @param auditSnapshot the audit snapshot containing change states
     * @return list of executable stages
     */
    private List<ExecutableStage> buildExecutableStages(
            List<AbstractLoadedStage> loadedStages,
            Map<String, AuditEntry> auditSnapshot) {

        return loadedStages.stream()
                .map(loadedStage -> {
                ChangeActionMap changeActionMap = CommunityChangeActionBuilder.build(
                        loadedStage.getChanges(),
                        auditSnapshot
                    );
                    return loadedStage.applyActions(changeActionMap);
                })
                .collect(Collectors.toList());
    }

    /**
     * Checks if any stage requires execution.
     *
     * @param stages the list of executable stages
     * @return true if at least one stage requires execution
     */
    private boolean hasExecutableStages(List<ExecutableStage> stages) {
        return stages.stream().anyMatch(ExecutableStage::isExecutionRequired);
    }

    /**
     * Gets the first executable stage.
     *
     * @param stages the list of executable stages
     * @return optional containing the first executable stage, or empty if none
     */
    private Optional<ExecutableStage> getFirstExecutableStage(List<ExecutableStage> stages) {
        return stages.stream()
                .filter(ExecutableStage::isExecutionRequired)
                .findFirst();
    }

    /**
     * Logs differences between initial and validated plans to detect concurrent executions.
     *
     * @param initialStages the initially planned stages
     * @param validatedStages the validated stages after lock acquisition
     */
    private void logPlanChanges(List<ExecutableStage> initialStages, List<ExecutableStage> validatedStages) {
        long initialCount = countExecutableChanges(initialStages);
        long validatedCount = countExecutableChanges(validatedStages);

        if (initialCount != validatedCount) {
            logger.warn(
                "Execution plan changed during lock acquisition: {} -> {} executable changes. " +
                "This indicates concurrent execution - {} changes were executed by another instance.",
                initialCount,
                validatedCount,
                initialCount - validatedCount
            );
        } else {
            logger.debug("Execution plan validated after lock acquisition: {} executable changes", validatedCount);
        }
    }

    /**
     * Counts the number of executable changes across all stages.
     *
     * @param stages the list of stages
     * @return total count of executable changes
     */
    private long countExecutableChanges(List<ExecutableStage> stages) {
        return stages.stream()
                .filter(ExecutableStage::isExecutionRequired)
                .mapToLong(stage -> stage.getChanges().size())
                .sum();
    }


    public static class Builder {
        private RunnerId runnerId;
        private CommunityAuditReader auditReader;
        private CommunityLockService lockService;
        private CoreConfigurable coreConfigurable;

        private Builder() {
        }

        public Builder setLockService(CommunityLockService lockService) {
            this.lockService = lockService;
            return this;
        }

        public Builder setAuditReader(CommunityAuditReader auditReader) {
            this.auditReader = auditReader;
            return this;
        }

        public Builder setRunnerId(RunnerId runnerId) {
            this.runnerId = runnerId;
            return this;
        }

        public Builder setCoreConfigurable(CoreConfigurable coreConfigurable) {
            this.coreConfigurable = coreConfigurable;
            return this;
        }

        public CommunityExecutionPlanner build() {
            return new CommunityExecutionPlanner(runnerId, lockService, auditReader, coreConfigurable);
        }
    }

}
