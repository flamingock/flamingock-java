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

package io.flamingock.internal.core.runner;

import io.flamingock.commons.utils.id.RunnerId;
import io.flamingock.commons.utils.StringUtil;
import io.flamingock.internal.core.builder.core.CoreConfigurable;
import io.flamingock.internal.core.engine.ConnectionEngine;
import io.flamingock.internal.core.engine.audit.ExecutionAuditWriter;
import io.flamingock.internal.core.engine.execution.ExecutionPlanner;
import io.flamingock.internal.core.event.EventPublisher;
import io.flamingock.internal.core.pipeline.Pipeline;
import io.flamingock.internal.core.pipeline.execution.OrphanExecutionContext;
import io.flamingock.internal.core.pipeline.execution.StageExecutor;
import io.flamingock.core.context.ContextResolver;
import io.flamingock.internal.core.transaction.TransactionWrapper;

import java.util.Set;

public final class PipelineRunnerCreator {

    private PipelineRunnerCreator() {
    }

    public static Runner createWithFinalizer(RunnerId runnerId,
                                             Pipeline pipeline,
                                             ConnectionEngine engine,
                                             CoreConfigurable coreConfiguration,
                                             EventPublisher eventPublisher,
                                             ContextResolver dependencyContext,
                                             Set<Class<?>> nonGuardedTypes,
                                             boolean isThrowExceptionIfCannotObtainLock,
                                             Runnable finalizer) {
        return create(
                runnerId,
                pipeline,
                engine.getAuditWriter(),
                engine.getTransactionWrapper().orElse(null),
                engine.getExecutionPlanner(),
                coreConfiguration,
                eventPublisher,
                dependencyContext,
                nonGuardedTypes,
                isThrowExceptionIfCannotObtainLock,
                finalizer);
    }

    private static Runner create(RunnerId runnerId,
                                 Pipeline pipeline,
                                 ExecutionAuditWriter auditWriter,
                                 TransactionWrapper transactionWrapper,
                                 ExecutionPlanner executionPlanner,
                                 CoreConfigurable coreConfiguration,
                                 EventPublisher eventPublisher,
                                 ContextResolver dependencyContext,
                                 Set<Class<?>> nonGuardedTypes,
                                 boolean isThrowExceptionIfCannotObtainLock,
                                 Runnable finalizer) {
        //Instantiated here, so we don't wait until Runner.run() and fail fast
        final StageExecutor stageExecutor = new StageExecutor(dependencyContext, nonGuardedTypes, auditWriter, transactionWrapper);
        return new PipelineRunner(
                runnerId,
                pipeline,
                executionPlanner,
                stageExecutor,
                buildExecutionContext(coreConfiguration),
                eventPublisher,
                isThrowExceptionIfCannotObtainLock,
                finalizer);
    }

    private static OrphanExecutionContext buildExecutionContext(CoreConfigurable configuration) {
        return new OrphanExecutionContext(StringUtil.hostname(), configuration.getDefaultAuthor(), configuration.getMetadata());
    }

}
