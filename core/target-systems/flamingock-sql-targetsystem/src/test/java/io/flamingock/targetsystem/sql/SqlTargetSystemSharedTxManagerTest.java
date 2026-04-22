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
package io.flamingock.targetsystem.sql;

import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.core.builder.FlamingockEdition;
import io.flamingock.internal.core.transaction.TransactionManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SqlTargetSystemSharedTxManagerTest {

    @Test
    @DisplayName("SqlTxWrapper and SqlTargetSystemAuditMarker should share the same TransactionManager instance")
    void txWrapperAndAuditMarkerShouldShareSameTxManager() throws Exception {
        DataSource dataSource = mockDataSource();

        ContextResolver contextResolver = mock(ContextResolver.class);
        when(contextResolver.getDependencyValue(FlamingockEdition.class))
                .thenReturn(Optional.of(FlamingockEdition.CLOUD));

        SqlTargetSystem targetSystem = new SqlTargetSystem("test-sql", dataSource);
        targetSystem.initialize(contextResolver);

        TransactionManager<?> txManagerFromWrapper = extractField(targetSystem.getTxWrapper(), "txManager");
        TransactionManager<?> txManagerFromMarker = extractField(targetSystem.getAuditMarker(), "txManager");

        assertNotNull(txManagerFromWrapper);
        assertNotNull(txManagerFromMarker);
        assertSame(txManagerFromWrapper, txManagerFromMarker,
                "SqlTxWrapper and SqlTargetSystemAuditMarker must share the same TransactionManager instance");
    }

    private static DataSource mockDataSource() throws Exception {
        ResultSet emptyResultSet = mock(ResultSet.class);
        when(emptyResultSet.next()).thenReturn(false);

        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(metadata.getDatabaseProductName()).thenReturn("MySQL");
        when(metadata.getTables(any(), any(), anyString(), any(String[].class))).thenReturn(emptyResultSet);

        Statement statement = mock(Statement.class);
        when(statement.execute(anyString())).thenReturn(false);

        Connection connection = mock(Connection.class);
        when(connection.getMetaData()).thenReturn(metadata);
        when(connection.createStatement()).thenReturn(statement);

        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);
        return dataSource;
    }

    @SuppressWarnings("unchecked")
    private static <T> T extractField(Object target, String fieldName) throws Exception {
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return (T) field.get(target);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName + " not found in " + target.getClass().getName());
    }
}
