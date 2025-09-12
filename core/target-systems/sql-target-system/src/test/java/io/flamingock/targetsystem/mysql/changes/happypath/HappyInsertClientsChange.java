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
package io.flamingock.targetsystem.mysql.changes.happypath;

import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.TargetSystem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

@TargetSystem( id = "mysql-ts")
@Change(id = "insert-clients", order = "002")
public class HappyInsertClientsChange {

    @Apply
    public void execution(Connection connection) throws SQLException {
        String sql = "INSERT INTO client_table (name, email) VALUES (?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, "John Doe");
            stmt.setString(2, "john.doe@example.com");
            stmt.executeUpdate();
        }
    }
}