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
package io.flamingock.targetsystem.sql;

import io.flamingock.externalsystem.sql.api.SqlExternalSystem;
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.common.core.context.RuntimeContext;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.core.builder.FlamingockEdition;
import io.flamingock.internal.core.external.targets.TransactionalTargetSystem;
import io.flamingock.internal.core.external.targets.mark.NoOpTargetSystemAuditMarker;
import io.flamingock.internal.core.runtime.ExecutionRuntime;
import io.flamingock.internal.core.transaction.TransactionManager;
import io.flamingock.internal.common.core.external.ExecutionWrapper;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Function;

import static io.flamingock.internal.core.builder.FlamingockEdition.COMMUNITY;

public class SqlTargetSystem extends TransactionalTargetSystem<SqlTargetSystem> implements SqlExternalSystem {

    private final DataSource dataSource;

    private final ExecutionWrapper nonTxWrapper;

    private SqlTxWrapper txWrapper;

    public SqlTargetSystem(String id, DataSource dataSource) {
        super(id);
        this.dataSource = dataSource;
        this.nonTxWrapper = new ExecutionWrapper() {
            @Override
            public <CONTEXT extends RuntimeContext, RESULT> RESULT wrapExecution(CONTEXT runtimeContext, Function<CONTEXT, RESULT> operation) {
                try (Connection connection = dataSource.getConnection()) {
                    runtimeContext.addDependency(connection);
                    return operation.apply(runtimeContext);
                } catch (SQLException e) {
                    throw new FlamingockException(e);
                }
            }
        };
    }

    @Override
    public DataSource getDataSource() {
        return dataSource;
    }

    @Override
    public void initialize(ContextResolver baseContext) {
        this.validate();
        targetSystemContext.addDependency(dataSource);

        TransactionManager<Connection> txManager = getTxManager();
        txWrapper = createTxWrapper(txManager);

        //TODO: inject marker repository based on edition(baseContext.getDependencyValue(FlamingockEdition.class))
        FlamingockEdition edition = baseContext.getDependencyValue(FlamingockEdition.class).orElse(COMMUNITY);
        auditMarker = edition == COMMUNITY
                ? new NoOpTargetSystemAuditMarker(this.getId())
                : SqlAuditMarker.builder(dataSource, txManager).build();

    }

    private void validate() {
        if (dataSource == null) {
            throw new FlamingockException("The 'DataSource' instance is required.");
        }
    }

    @Override
    protected SqlTargetSystem getSelf() {
        return this;
    }

    @Override
    public ExecutionWrapper getTxWrapper() {
        return txWrapper;
    }

    /**
     * Non-transactional execution needs a JDBC {@link Connection} of its own: the change still has SQL to
     * run, it just runs outside a transaction. This wrapper borrows one connection from the
     * {@link DataSource} for the duration of the call, publishes it into the runtime so the change can
     * resolve it, and closes it afterwards — including when the operation throws.
     * <p>
     * Auto-commit is left untouched, at whatever the {@code DataSource} hands back — so under the usual
     * auto-commit default each statement commits on its own. That is the point of this path: a change
     * declared non-transactional gets no rollback, and a failure partway through leaves the statements
     * that already ran in place.
     * <p>
     * The transactional path does not go through here — see {@link #getTxWrapper()}, whose connection is
     * owned by the {@code TransactionManager} and spans the whole transaction.
     */
    @Override
    protected ExecutionWrapper getNonTxWrapper() {
        return nonTxWrapper;
    }

    private SqlTxWrapper createTxWrapper(TransactionManager<Connection> txManager) {
        return new SqlTxWrapper(txManager);
    }

    private TransactionManager<Connection> getTxManager() {
        return new TransactionManager<>(() -> {
            try {
                return dataSource.getConnection();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
