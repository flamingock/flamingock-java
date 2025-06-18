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

package io.flamingock.internal.core.task.navigation.navigator;

import io.flamingock.internal.core.engine.audit.ExecutionAuditWriter;
import io.flamingock.internal.core.engine.lock.Lock;
import io.flamingock.internal.core.pipeline.execution.TaskSummarizer;
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.core.transaction.TransactionWrapper;

import java.util.Set;

public interface StepNavigatorBuilder {


    StepNavigatorBuilder setSummarizer(TaskSummarizer summarizer);

    StepNavigatorBuilder setAuditWriter(ExecutionAuditWriter auditWriter);

    StepNavigatorBuilder setLock(Lock lock);

    StepNavigatorBuilder setDependencyContext(ContextResolver staticContext);

    StepNavigatorBuilder setNonGuardedTypes(Set<Class<?>> types);

    StepNavigatorBuilder setTransactionWrapper(TransactionWrapper transactionWrapper);

    StepNavigator build();


    abstract class AbstractStepNavigator implements StepNavigatorBuilder {


        protected TaskSummarizer summarizer = null;
        protected ExecutionAuditWriter auditWriter = null;

        protected Lock lock = null;

        protected ContextResolver staticContext;

        protected Set<Class<?>> nonGuardedTypes;

        protected TransactionWrapper transactionWrapper = null;

        public AbstractStepNavigator() {
        }

        @Override
        public StepNavigatorBuilder setSummarizer(TaskSummarizer summarizer) {
            this.summarizer = summarizer;
            return this;
        }

        @Override
        public StepNavigatorBuilder setAuditWriter(ExecutionAuditWriter auditWriter) {
            this.auditWriter = auditWriter;
            return this;
        }

        @Override
        public StepNavigatorBuilder setDependencyContext(ContextResolver staticContext) {
            this.staticContext = staticContext;
            return this;
        }

        @Override
        public StepNavigatorBuilder setNonGuardedTypes(Set<Class<?>> nonGuardedTypes) {
            this.nonGuardedTypes = nonGuardedTypes;
            return this;
        }


        @Override
        public StepNavigatorBuilder setLock(Lock lock) {
            this.lock = lock;
            return this;
        }

        @Override
        public StepNavigatorBuilder setTransactionWrapper(TransactionWrapper transactionWrapper) {
            this.transactionWrapper = transactionWrapper;
            return this;
        }

    }

}
