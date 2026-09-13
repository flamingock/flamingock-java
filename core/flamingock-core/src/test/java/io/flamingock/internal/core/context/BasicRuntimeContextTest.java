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

import io.flamingock.internal.common.core.context.Dependency;
import io.flamingock.internal.common.core.transaction.TransactionWrapper;
import io.flamingock.internal.common.core.context.RuntimeContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Tests for {@link BasicRuntimeContext} — the minimal {@link RuntimeContext} used when a
 * {@link TransactionWrapper} is needed outside change execution.
 */
class BasicRuntimeContextTest {

    /**
     * Stand-in for a transaction-scoped handle (e.g. a Mongo {@code ClientSession}).
     */
    private static final class Session {
        private final String name;

        private Session(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return "Session(" + name + ")";
        }
    }

    @Test
    @DisplayName("Exposes the session id it was created with")
    void shouldExposeSessionId() {
        assertEquals("change-1", new BasicRuntimeContext("change-1").getSessionId());
    }

    @Test
    @DisplayName("Resolves a dependency injected after construction")
    void shouldResolveInjectedDependency() {
        BasicRuntimeContext runtimeContext = new BasicRuntimeContext("change-1");
        Session session = new Session("injected");

        runtimeContext.addDependency(new Dependency(Session.class, session, false));

        assertEquals(session, runtimeContext.getContext().getRequiredDependencyValue(Session.class));
    }

    @Test
    @DisplayName("Resolves from the base context and lets injected dependencies take precedence over it")
    void shouldPreferInjectedDependencyOverBaseContext() {
        SimpleContext baseContext = new SimpleContext();
        baseContext.addDependency(new Dependency(Session.class, new Session("base"), false));
        baseContext.addDependency(new Dependency(String.class, "only-in-base", false));

        BasicRuntimeContext runtimeContext = new BasicRuntimeContext("change-1", baseContext);
        Session injected = new Session("injected");
        runtimeContext.addDependency(new Dependency(Session.class, injected, false));

        assertEquals(injected, runtimeContext.getContext().getRequiredDependencyValue(Session.class));
        assertEquals("only-in-base", runtimeContext.getContext().getRequiredDependencyValue(String.class));
    }

    @Test
    @DisplayName("A context layer takes precedence over everything added before it")
    void shouldPreferLatestContextLayer() {
        SimpleContext baseContext = new SimpleContext();
        baseContext.addDependency(new Dependency(Session.class, new Session("base"), false));

        BasicRuntimeContext runtimeContext = new BasicRuntimeContext("change-1", baseContext);
        runtimeContext.addDependency(new Dependency(Session.class, new Session("injected"), false));

        SimpleContext layer = new SimpleContext();
        Session fromLayer = new Session("layer");
        layer.addDependency(new Dependency(Session.class, fromLayer, false));
        runtimeContext.addContextLayer(layer);

        assertEquals(fromLayer, runtimeContext.getContext().getRequiredDependencyValue(Session.class));
    }

    @Test
    @DisplayName("Resolves every dependency of a bulk injection")
    void shouldResolveBulkInjectedDependencies() {
        BasicRuntimeContext runtimeContext = new BasicRuntimeContext("change-1");
        Session session = new Session("injected");

        runtimeContext.addDependencies(Arrays.asList(
                new Dependency(Session.class, session, false),
                new Dependency(String.class, "bulk", false)));

        assertEquals(session, runtimeContext.getContext().getRequiredDependencyValue(Session.class));
        assertEquals("bulk", runtimeContext.getContext().getRequiredDependencyValue(String.class));
    }

    @Test
    @DisplayName("Carries a transaction wrapper's injected session through to the wrapped operation")
    void shouldCarrySessionInjectedByTransactionWrapper() {
        Session opened = new Session("opened-by-wrapper");
        // Stands in for a real wrapper: starts a "transaction", publishes its session, runs the operation
        TransactionWrapper txWrapper = new TransactionWrapper() {
            @Override
            public <CONTEXT extends RuntimeContext, RESULT> RESULT wrapInTransaction(CONTEXT runtimeContext,
                                                                                     Function<CONTEXT, RESULT> operation) {
                runtimeContext.addDependency(new Dependency(Session.class, opened, false));
                return operation.apply(runtimeContext);
            }
        };

        BasicRuntimeContext runtimeContext = new BasicRuntimeContext("change-1");
        assertFalse(runtimeContext.getContext().getDependency(Session.class).isPresent(),
                "The session must not be resolvable before the wrapper opens the transaction");

        Session seenByOperation = txWrapper.wrapInTransaction(runtimeContext,
                ctx -> ctx.getContext().getRequiredDependencyValue(Session.class));

        assertEquals(opened, seenByOperation);
    }
}
