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

package io.flamingock.community.internal;


import io.flamingock.community.internal.lock.LocalLock;
import io.flamingock.community.internal.lock.LocalLockService;
import io.flamingock.core.configurator.core.CoreConfigurable;
import io.flamingock.core.engine.audit.AuditReader;
import io.flamingock.core.engine.audit.writer.AuditStageStatus;
import io.flamingock.core.engine.execution.ExecutionPlan;
import io.flamingock.core.engine.execution.ExecutionPlanner;
import io.flamingock.core.engine.lock.Lock;
import io.flamingock.core.engine.lock.LockException;
import io.flamingock.core.engine.lock.LockRefreshDaemon;
import io.flamingock.core.pipeline.ExecutableStage;
import io.flamingock.core.pipeline.Pipeline;
import io.flamingock.core.runner.RunnerId;
import io.flamingock.core.util.TimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
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
    protected ExecutionPlan getNextExecution(Pipeline pipeline) throws LockException {
        AuditStageStatus currentAuditStageStatus = auditReader.getAuditStageStatus();
        logger.debug("Pulled remote state:\n{}", currentAuditStageStatus);

        List<ExecutableStage> executableStages = pipeline
                .getLoadedStages()
                .stream()
                .map(loadedStage -> loadedStage.applyState(currentAuditStageStatus))
                .collect(Collectors.toList());

        if (executableStages.stream().anyMatch(ExecutableStage::doesRequireExecution)) {
            Lock lock = acquireLock();
            if (configuration.isEnableRefreshDaemon()) {
                new LockRefreshDaemon(lock, TimeService.getDefault()).start();
            }
            String executionId = UUID.randomUUID().toString();
            return ExecutionPlan.newExecution(executionId, lock, executableStages);

        } else {
            return ExecutionPlan.CONTINUE();
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
