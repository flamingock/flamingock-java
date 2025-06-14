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

package io.flamingock.internal.core.engine;

import io.flamingock.core.context.ContextContributor;
import io.flamingock.core.context.ContextInjectable;
import io.flamingock.core.context.Dependency;
import io.flamingock.internal.core.engine.audit.ExecutionAuditWriter;
import io.flamingock.internal.core.engine.execution.ExecutionPlanner;
import io.flamingock.core.system.SystemModuleContributor;
import io.flamingock.internal.core.transaction.TransactionWrapper;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

public interface ConnectionEngine extends SystemModuleContributor, ContextContributor {
    ExecutionAuditWriter getAuditWriter();

    ExecutionPlanner getExecutionPlanner();

    Optional<? extends TransactionWrapper> getTransactionWrapper();

    @Override
    default void contributeToContext(ContextInjectable contextInjectable) {
        contextInjectable.addDependency(new Dependency(ExecutionAuditWriter.class, getAuditWriter()));
    }

    default Runnable getCloser() {
        return () -> {
        };
    }

    default Set<Class<?>> getNonGuardedTypes() {
        return Collections.emptySet();
    }
}
