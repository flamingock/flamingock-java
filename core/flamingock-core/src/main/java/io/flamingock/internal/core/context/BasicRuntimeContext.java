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
package io.flamingock.internal.core.context;

import io.flamingock.internal.common.core.context.Context;
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.common.core.context.Dependency;
import io.flamingock.internal.common.core.context.RuntimeContext;

import java.util.Collection;

/**
 * Minimal {@link RuntimeContext}: a session id plus a layered, writable dependency context.
 * <p>
 * It exists for the cases where a {@link io.flamingock.internal.common.core.transaction.TransactionWrapper}
 * is needed outside change execution — the wrapper contract requires a {@code RuntimeContext} to publish
 * its transaction-scoped dependencies into, but the caller has no change to run and therefore no use for
 * the reflection machinery of {@code ExecutionRuntime}.
 * <p>
 * The canonical case is the audit store wrapping its own writes: the wrapper starts the transaction and
 * injects the session handle, and the caller reads it back to enlist its own operations in that same
 * transaction.
 *
 * <pre>{@code
 * BasicRuntimeContext runtimeContext = new BasicRuntimeContext(sessionId);
 * txWrapper.wrapInTransaction(runtimeContext, ctx -> {
 *     ClientSession session = ctx.getContext().getRequiredDependencyValue(ClientSession.class);
 *     auditRepository.write(session, auditEntry);
 *     journalRepository.append(session, journalEvent);
 *     return Result.OK();
 * });
 * }</pre>
 * <p>
 * Not thread-safe: like {@code ExecutionRuntime}, an instance is meant to be created and consumed within
 * a single transaction scope.
 */
public class BasicRuntimeContext implements RuntimeContext {

    private final String sessionId;

    private Context dependencyContext;

    /**
     * Creates a context with no base dependencies — only what the transaction wrapper, or the caller,
     * injects into it.
     *
     * @param sessionId identifies the transaction scope; transaction wrappers use it to key their session
     */
    public BasicRuntimeContext(String sessionId) {
        this(sessionId, new SimpleContext());
    }

    /**
     * Creates a context backed by an existing context, which is used for resolution only. Dependencies
     * injected afterwards land in a writable layer on top and take precedence over {@code baseContext}.
     *
     * @param sessionId   identifies the transaction scope
     * @param baseContext read-only fallback used when a dependency is not found in the writable layers
     */
    public BasicRuntimeContext(String sessionId, ContextResolver baseContext) {
        this.sessionId = sessionId;
        this.dependencyContext = new PriorityContext(new SimpleContext(), baseContext);
    }

    @Override
    public String getSessionId() {
        return sessionId;
    }

    @Override
    public ContextResolver getContext() {
        return dependencyContext;
    }

    @Override
    public void addContextLayer(ContextResolver contextOnTop) {
        Context priorityContext = new PriorityContext(new SimpleContext(), contextOnTop);
        dependencyContext = new PriorityContext(priorityContext, dependencyContext);
    }

    @Override
    public void addDependencies(Collection<? extends Dependency> dependencies) {
        dependencyContext.addDependencies(dependencies);
    }

    @Override
    public void addDependency(Dependency dependency) {
        dependencyContext.addDependency(dependency);
    }
}
