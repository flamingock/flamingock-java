/*
 * Copyright 2023 Flamingock (https://oss.flamingock.io)
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

package io.flamingock.core.local;


import io.flamingock.core.local.lock.LocalLock;
import io.flamingock.core.local.lock.LocalLockService;
import io.flamingock.core.api.metadata.FlamingockMetadata;
import io.flamingock.core.configurator.core.CoreConfigurable;
import io.flamingock.core.engine.audit.AuditReader;
import io.flamingock.core.engine.audit.writer.AuditStageStatus;
import io.flamingock.core.engine.execution.ExecutionPlan;
import io.flamingock.core.engine.execution.ExecutionPlanner;
import io.flamingock.core.engine.lock.Lock;
import io.flamingock.core.engine.lock.LockException;
import io.flamingock.core.engine.lock.LockRefreshDaemon;
import io.flamingock.core.pipeline.ExecutableStage;
import io.flamingock.core.pipeline.LoadedStage;
import io.flamingock.core.pipeline.Pipeline;
import io.flamingock.commons.utils.RunnerId;
import io.flamingock.commons.utils.TimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class LocalExecutionPlanner extends ExecutionPlanner {
    private static final Logger logger = LoggerFactory.getLogger(LocalExecutionPlanner.class);

    private final AuditReader auditReader;
    private final LocalLockService lockService;

    private final CoreConfigurable configuration;
    private final RunnerId instanceId;


    /**
     * @param lockService    lockService to persist the lock
     * @param auditReader
     * @param coreConfiguration
     */
    public LocalExecutionPlanner(RunnerId instanceId,
                                 LocalLockService lockService,
                                 AuditReader auditReader,
                                 CoreConfigurable coreConfiguration) {
        this.instanceId = instanceId;
        this.auditReader = auditReader;
        this.lockService = lockService;
        this.configuration = coreConfiguration;
    }

    @Override
    public ExecutionPlan getNextExecution(List<LoadedStage> loadedStages) throws LockException {
        AuditStageStatus currentAuditStageStatus = auditReader.getAuditStageStatus();
        logger.debug("Pulled remote state:\n{}", currentAuditStageStatus);
        List<ExecutableStage> executableStages = loadedStages
                .stream()
                .map(loadedStage -> loadedStage.applyState(currentAuditStageStatus))
                .collect(Collectors.toList());

        Optional<ExecutableStage> nextStageOpt = executableStages.stream()
                .filter(ExecutableStage::isExecutionRequired)
                .findFirst();


        if (nextStageOpt.isPresent()) {
            Lock lock = acquireLock();
            if (configuration.isEnableRefreshDaemon()) {
                new LockRefreshDaemon(lock, TimeService.getDefault()).start();
            }
            String executionId = UUID.randomUUID().toString();
            return ExecutionPlan.newExecution(executionId, lock, Collections.singletonList(nextStageOpt.get()));

        } else {
            return ExecutionPlan.CONTINUE(executableStages);
        }
    }

    private Lock acquireLock() {
        return LocalLock.getLock(
                configuration.getLockAcquiredForMillis(),
                configuration.getLockQuitTryingAfterMillis(),
                configuration.getLockTryFrequencyMillis(),
                instanceId,
                lockService,
                TimeService.getDefault()
        );
    }

}
