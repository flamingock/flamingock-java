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

import io.flamingock.internal.common.core.context.Dependency;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.core.runtime.ExecutionRuntime;
import io.flamingock.internal.core.task.navigation.step.FailedStep;
import io.flamingock.internal.core.transaction.TransactionWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Function;

public class SqlTxWrapper implements TransactionWrapper {
    private static final Logger logger = LoggerFactory.getLogger("SqlTxWrapper");

    private final DataSource dataSource;

    public SqlTxWrapper(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public <T> T wrapInTransaction(ExecutionRuntime executionRuntime, Function<ExecutionRuntime, T> operation) {

        try (Connection connection = dataSource.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            try {
                connection.setAutoCommit(false);
                executionRuntime.addDependency(new Dependency(connection));
                T result = operation.apply(executionRuntime);
                if (result instanceof FailedStep) {
                    connection.rollback();
                } else {
                    connection.commit();
                }
                return result;
            } catch (Exception e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackEx) {
                    logger.warn(String.format("Rollback failed: %s", rollbackEx.getMessage()), rollbackEx);
                }
                throw new FlamingockException(e);
            } finally {
                try {
                    connection.setAutoCommit(originalAutoCommit);
                } catch (SQLException setAutoCommitEx) {
                    logger.warn(String.format("Failed to restore autoCommit: %s", setAutoCommitEx.getMessage()), setAutoCommitEx);
                }            }

        } catch (SQLException e) {
            throw new FlamingockException(e);
        }
    }

}