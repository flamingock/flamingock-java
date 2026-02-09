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
package io.flamingock.internal.core.operation;

import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.common.core.recovery.Resolution;
import io.flamingock.internal.core.builder.args.FlamingockArguments;
import io.flamingock.internal.core.configuration.core.CoreConfigurable;
import io.flamingock.internal.core.event.EventPublisher;
import io.flamingock.internal.core.external.store.audit.AuditPersistence;
import io.flamingock.internal.core.external.targets.TargetSystemManager;
import io.flamingock.internal.core.operation.audit.AuditFixArgs;
import io.flamingock.internal.core.operation.audit.AuditFixOperation;
import io.flamingock.internal.core.operation.audit.AuditFixResult;
import io.flamingock.internal.core.operation.audit.AuditListArgs;
import io.flamingock.internal.core.operation.audit.AuditListOperation;
import io.flamingock.internal.core.operation.audit.AuditListResult;
import io.flamingock.internal.core.operation.execute.ExecuteArgs;
import io.flamingock.internal.core.operation.execute.ExecuteOperation;
import io.flamingock.internal.core.operation.execute.ExecuteResult;
import io.flamingock.internal.core.operation.issue.IssueGetArgs;
import io.flamingock.internal.core.operation.issue.IssueGetOperation;
import io.flamingock.internal.core.operation.issue.IssueGetResult;
import io.flamingock.internal.core.operation.issue.IssueListArgs;
import io.flamingock.internal.core.operation.issue.IssueListOperation;
import io.flamingock.internal.core.operation.issue.IssueListResult;
import io.flamingock.internal.core.pipeline.execution.OrphanExecutionContext;
import io.flamingock.internal.core.pipeline.execution.StageExecutor;
import io.flamingock.internal.core.pipeline.loaded.LoadedPipeline;
import io.flamingock.internal.core.plan.ExecutionPlanner;
import io.flamingock.internal.util.StringUtil;
import io.flamingock.internal.util.id.RunnerId;

import java.util.Set;

public class OperationFactory {

    private static final String ARG_HISTORY = "flamingock.audit.history";
    private static final String ARG_SINCE = "flamingock.audit.since";
    private static final String ARG_EXTENDED = "flamingock.audit.extended";
    private static final String ARG_CHANGE_ID = "flamingock.change-id";
    private static final String ARG_RESOLUTION = "flamingock.resolution";
    private static final String ARG_GUIDANCE = "flamingock.guidance";

    private final RunnerId runnerId;
    private final FlamingockArguments flamingockArgs;
    private final LoadedPipeline pipeline;
    private final AuditPersistence persistence;
    private final ExecutionPlanner executionPlanner;
    private final TargetSystemManager targetSystemManager;
    private final CoreConfigurable coreConfiguration;
    private final EventPublisher eventPublisher;
    private final ContextResolver dependencyContext;
    private final Set<Class<?>> nonGuardedTypes;
    private final boolean isThrowExceptionIfCannotObtainLock;
    private final Runnable finalizer;

    public OperationFactory(RunnerId runnerId,
                             FlamingockArguments flamingockArgs,
                             LoadedPipeline pipeline,
                             AuditPersistence persistence,
                             ExecutionPlanner executionPlanner,
                             TargetSystemManager targetSystemManager,
                             CoreConfigurable coreConfiguration,
                             EventPublisher eventPublisher,
                             ContextResolver dependencyContext,
                             Set<Class<?>> nonGuardedTypes,
                             boolean isThrowExceptionIfCannotObtainLock,
                             Runnable finalizer) {
        this.runnerId = runnerId;
        this.flamingockArgs = flamingockArgs;
        this.pipeline = pipeline;
        this.persistence = persistence;
        this.executionPlanner = executionPlanner;
        this.targetSystemManager = targetSystemManager;
        this.coreConfiguration = coreConfiguration;
        this.eventPublisher = eventPublisher;
        this.dependencyContext = dependencyContext;
        this.nonGuardedTypes = nonGuardedTypes;
        this.isThrowExceptionIfCannotObtainLock = isThrowExceptionIfCannotObtainLock;
        this.finalizer = finalizer;
    }

    public RunnableOperation<?, ?> getOperation() {
        switch (flamingockArgs.getOperation()) {
            case EXECUTE_APPLY:
                return getExecuteOperation();
            case AUDIT_LIST:
                return getAuditListOperation();
            case AUDIT_FIX:
                return getAuditFixOperation();
            case ISSUE_LIST:
                return getIssueListOperation();
            case ISSUE_GET:
                return getIssueGetOperation();
            default:
                throw new UnsupportedOperationException(String.format("Operation %s not supported", flamingockArgs.getOperation()));
        }
    }

    private RunnableOperation<AuditListArgs, AuditListResult> getAuditListOperation() {
        boolean history = flamingockArgs.getBooleanOr(ARG_HISTORY, false);
        java.time.LocalDateTime since = flamingockArgs.getDateTimeOr(ARG_SINCE, null);
        boolean extended = flamingockArgs.getBooleanOr(ARG_EXTENDED, false);
        AuditListOperation auditListOperation = new AuditListOperation(persistence);
        return new RunnableOperation<>(auditListOperation, new AuditListArgs(history, since, extended));
    }

    private RunnableOperation<AuditFixArgs, AuditFixResult> getAuditFixOperation() {
        String changeId = flamingockArgs.getStringOrThrow(ARG_CHANGE_ID,
                "Change ID is required for AUDIT_FIX operation");
        Resolution resolution = flamingockArgs.getEnumOrThrow(ARG_RESOLUTION, Resolution.class,
                "Resolution is required for AUDIT_FIX operation.");
        AuditFixOperation auditFixOperation = new AuditFixOperation(persistence);
        return new RunnableOperation<>(auditFixOperation, new AuditFixArgs(changeId, resolution));
    }

    private RunnableOperation<IssueListArgs, IssueListResult> getIssueListOperation() {
        IssueListOperation issueListOperation = new IssueListOperation(persistence);
        return new RunnableOperation<>(issueListOperation, new IssueListArgs());
    }

    private RunnableOperation<IssueGetArgs, IssueGetResult> getIssueGetOperation() {
        String changeId = flamingockArgs.getStringOr(ARG_CHANGE_ID, null);
        boolean guidance = flamingockArgs.getBooleanOr(ARG_GUIDANCE, false);
        IssueGetOperation issueGetOperation = new IssueGetOperation(persistence);
        return new RunnableOperation<>(issueGetOperation, new IssueGetArgs(changeId, guidance));
    }

    private RunnableOperation<ExecuteArgs, ExecuteResult> getExecuteOperation() {
        final StageExecutor stageExecutor = new StageExecutor(dependencyContext, nonGuardedTypes, persistence, targetSystemManager, null);
        ExecuteOperation executeOperation = new ExecuteOperation(
                runnerId,
                executionPlanner,
                stageExecutor,
                buildExecutionContext(coreConfiguration),
                eventPublisher,
                isThrowExceptionIfCannotObtainLock,
                finalizer);
        return new RunnableOperation<>(executeOperation, new ExecuteArgs(pipeline));
    }

    private static OrphanExecutionContext buildExecutionContext(CoreConfigurable configuration) {
        return new OrphanExecutionContext(StringUtil.hostname(), configuration.getMetadata());
    }
}
