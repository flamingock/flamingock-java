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
package io.flamingock.targetsystem.mysql;

import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.core.builder.FlamingockEdition;
import io.flamingock.internal.core.transaction.TransactionManager;
import io.flamingock.internal.core.runtime.ExecutionRuntime;
import io.flamingock.internal.core.targets.mark.NoOpTargetSystemAuditMarker;
import io.flamingock.internal.core.targets.mark.TargetSystemAuditMarker;
import io.flamingock.internal.core.targets.TransactionalTargetSystem;
import io.flamingock.internal.core.transaction.TransactionWrapper;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Function;

public class SqlTargetSystem extends TransactionalTargetSystem<SqlTargetSystem> {
    private static final String FLAMINGOCK_ON_GOING_TASKS = "FLAMINGOCK_ONGOING_TASKS";
    private TargetSystemAuditMarker taskStatusRepository;
    private DataSource dataSource;
    private SqlTxWrapper txWrapper;

    public SqlTargetSystem(String id) {
        super(id);
    }

    public SqlTargetSystem withDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
        targetSystemContext.addDependency(dataSource);
        return this;
    }

    @Override
    public void initialize(ContextResolver baseContext) {
        FlamingockEdition edition = baseContext.getDependencyValue(FlamingockEdition.class)
                .orElse(FlamingockEdition.CLOUD);

        DataSource dataSource = targetSystemContext.getDependencyValue(DataSource.class)
                .orElseGet(() -> baseContext.getRequiredDependencyValue(DataSource.class));

        TransactionManager<Connection> txManager = new TransactionManager<>(() -> {
            try {
                return dataSource.getConnection();
            }catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });

        txWrapper = new SqlTxWrapper(txManager);

        taskStatusRepository = edition == FlamingockEdition.COMMUNITY
                ? new NoOpTargetSystemAuditMarker(this.getId())
                : SqlTargetSystemAuditMarker.builder(dataSource, txManager)
                .withTableName(FLAMINGOCK_ON_GOING_TASKS)
                .withAutoCreate(autoCreate)
                .build();
    }

    @Override
    protected SqlTargetSystem getSelf() {
        return this;
    }

    @Override
    public <T> T applyChange(Function<ExecutionRuntime, T> changeApplier, ExecutionRuntime executionRuntime) {
        try {
            executionRuntime.addDependency(dataSource.getConnection());
        } catch (SQLException e) {
            throw new FlamingockException(e);
        }
        return changeApplier.apply(executionRuntime);
    }

    @Override
    public TargetSystemAuditMarker getOnGoingTaskStatusRepository() {
        return taskStatusRepository;
    }

    @Override
    public TransactionWrapper getTxWrapper() {
        return txWrapper;
    }

    @Override
    public boolean isSameTxResourceAs(TransactionalTargetSystem<?> other) {
        if(!(other instanceof SqlTargetSystem)) {
            return false;
        }
        DataSource otherDataSource = ((SqlTargetSystem) other).dataSource;
        if(otherDataSource == null) {
            return false;
        }
        return otherDataSource.equals(this.dataSource);
    }
}
