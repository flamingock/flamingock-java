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
import io.flamingock.internal.core.store.lock.community.CommunityLock;
import io.flamingock.internal.core.store.lock.community.CommunityLockService;
import io.flamingock.internal.core.configuration.core.CoreConfigurable;

import io.flamingock.internal.core.plan.ExecutionPlan;
import io.flamingock.internal.core.plan.ExecutionPlanner;
import io.flamingock.internal.core.store.lock.Lock;
import io.flamingock.internal.core.store.lock.LockException;
import io.flamingock.internal.core.store.lock.LockRefreshDaemon;
import io.flamingock.internal.core.pipeline.execution.ExecutableStage;
import io.flamingock.internal.core.pipeline.loaded.stage.AbstractLoadedStage;
import io.flamingock.internal.core.store.audit.community.CommunityAuditReader;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.internal.util.TimeService;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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

    @Override
    public ExecutionPlan getNextExecution(List<AbstractLoadedStage> loadedStages) throws LockException {
        Map<String, AuditEntry> auditSnapshot = auditReader.getAuditSnapshotByChangeId();
        logger.debug("Pulled remote state:\n{}", auditSnapshot);
        
        List<ExecutableStage> executableStages = loadedStages
                .stream()
                .map(loadedStage -> {
                    // Convert audit status to action plan using the new action-based architecture
                    ChangeActionMap changeActionMap = CommunityChangeActionBuilder.build(loadedStage.getTasks(), auditSnapshot);
                    return loadedStage.applyActions(changeActionMap);
                })
                .collect(Collectors.toList());

        Optional<ExecutableStage> nextStageOpt = executableStages.stream()
                .filter(ExecutableStage::isExecutionRequired)
                .findFirst();


        if (nextStageOpt.isPresent()) {
            Lock lock = acquireLock();
            if (configuration.isEnableRefreshDaemon()) {
                new LockRefreshDaemon(lock, TimeService.getDefault()).start();
            }
            String executionId = ExecutionId.getNewExecutionId();
            return ExecutionPlan.newExecution(executionId, lock, Collections.singletonList(nextStageOpt.get()));

        } else {
            return ExecutionPlan.CONTINUE(executableStages);
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
