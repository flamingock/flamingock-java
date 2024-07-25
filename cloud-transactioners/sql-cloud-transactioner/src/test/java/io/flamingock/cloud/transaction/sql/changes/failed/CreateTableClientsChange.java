/*
 * Copyright 2023 Flamingock ("https://oss.flamingock.io")
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.flamingock.cloud.transaction.sql.changes.failed;

import io.changock.migration.api.annotations.NonLockGuarded;
import io.flamingock.core.api.annotations.ChangeUnit;
import io.flamingock.core.api.annotations.Execution;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

@ChangeUnit(id = "create-table-clients", order = "1")
public class CreateTableClientsChange {

    @Execution
    public void execution(@NonLockGuarded Connection connection) throws SQLException {

        try(Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE CLIENTS_2 (" +
                    "id INTEGER not NULL, " +
                    " name VARCHAR(255), " +
                    " PRIMARY KEY ( id )" +
                    ")");
        }
    }



}
