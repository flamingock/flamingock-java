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
package io.flamingock.template.sql;

import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.Rollback;
import io.flamingock.api.template.AbstractChangeTemplate;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import io.flamingock.template.sql.util.SqlStatementParser;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;

public class SqlTemplate extends AbstractChangeTemplate<Void, String, String> {

    private final Logger logger = FlamingockLoggerFactory.getLogger(SqlTemplate.class);

    public SqlTemplate() {
        super();
    }

    @Apply
    public void apply(Connection connection) throws SQLException {
        execute(connection, applyPayload);
    }

    @Rollback
    public void rollback(Connection connection) throws SQLException {
        execute(connection, rollbackPayload);
    }

    private void execute(Connection connection, String sql) throws SQLException {
        if (connection == null) {
            throw new IllegalArgumentException("connection is null");
        }
        if (connection.isClosed()) {
            throw new IllegalArgumentException("connection is closed");
        }

        if (sql == null || sql.trim().isEmpty()) {
            throw new IllegalArgumentException("SQL payload is null or empty");
        }

        List<String> statements = SqlStatementParser.splitStatements(sql);

        // Group statements by command type for intelligent batching
        Map<String, List<String>> groupedStatements = new HashMap<>();
        for (String stmt : statements) {
            String trimmed = stmt.trim();
            if (trimmed.isEmpty()) continue;
            String command = SqlStatementParser.getCommand(trimmed);
            groupedStatements.computeIfAbsent(command, k -> new ArrayList<>()).add(trimmed);
        }

        // Execute each group
        for (Map.Entry<String, List<String>> entry : groupedStatements.entrySet()) {
            List<String> group = entry.getValue();
            if (group.size() == 1) {
                // Single statement, execute individually
                SqlStatementParser.executeSingle(connection, group.get(0));
            } else {
                // Multiple statements of same type, batch them
                SqlStatementParser.executeMany(connection, group);
            }
        }
    }
}
