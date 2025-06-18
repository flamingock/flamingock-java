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

package io.flamingock.cloud.transaction.sql.changes.happypath;


import io.flamingock.api.annotations.ChangeUnit;
import io.flamingock.api.annotations.Execution;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

@ChangeUnit(id = "create-table-clients", order = "001", transactional = false)
public class _1_create_clients_table_happy {

    @Execution
    public void execution(Connection connection) throws SQLException {

        try(Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE CLIENTS (" +
                    "id INTEGER not NULL, " +
                    " name VARCHAR(255), " +
                    " PRIMARY KEY ( id )" +
                    ")");
        }
    }



}
