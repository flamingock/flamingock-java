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
import io.flamingock.internal.core.external.store.audit.community.CommunityAuditReader;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.internal.util.TimeService;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;

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
     * @param loadedStages the list of loaded stages containing all defined changes
     * @return ExecutionPlan containing either stages to execute (with lock held) or CONTINUE (no lock)
     * @throws LockException if unable to acquire the distributed lock within the configured timeout
     */
    @Override
    public ExecutionPlan getNextExecution(List<AbstractLoadedStage> loadedStages) throws LockException {
        Map<String, AuditEntry> initialSnapshot = auditReader.getAuditSnapshotByChangeId();
        logger.debug("Pulled initial remote state:\n{}", initialSnapshot);

        List<ExecutableStage> initialStages = buildExecutableStages(loadedStages, initialSnapshot);

        if (!hasExecutableStages(initialStages)) {
            return ExecutionPlan.CONTINUE(initialStages);
        }

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
                return ExecutionPlan.CONTINUE(validatedStages);
            }

            logPlanChanges(initialStages, validatedStages);

            if (configuration.isEnableRefreshDaemon()) {
                new LockRefreshDaemon(lock, TimeService.getDefault()).start();
            }

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

    private Lock acquireLock() {
        return CommunityLock.getLock(
                configuration.getLockAcquiredForMillis(),
                configuration.getLockQuitTryingAfterMillis(),
                configuration.getLockTryFrequencyMillis(),
                instanceId,
                lockService,
                TimeService.getDefault()
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
                        loadedStage.getTasks(),
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
        long initialCount = countExecutableTasks(initialStages);
        long validatedCount = countExecutableTasks(validatedStages);

        if (initialCount != validatedCount) {
            logger.warn(
                "Execution plan changed during lock acquisition: {} -> {} executable tasks. " +
                "This indicates concurrent execution - {} tasks were executed by another instance.",
                initialCount,
                validatedCount,
                initialCount - validatedCount
            );
        } else {
            logger.debug("Execution plan validated after lock acquisition: {} executable tasks", validatedCount);
        }
    }

    /**
     * Counts the number of executable tasks across all stages.
     *
     * @param stages the list of stages
     * @return total count of executable tasks
     */
    private long countExecutableTasks(List<ExecutableStage> stages) {
        return stages.stream()
                .filter(ExecutableStage::isExecutionRequired)
                .mapToLong(stage -> stage.getTasks().size())
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
