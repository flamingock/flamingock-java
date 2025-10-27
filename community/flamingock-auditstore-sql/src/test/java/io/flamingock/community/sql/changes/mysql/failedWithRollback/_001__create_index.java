/*
 * Copyright 2025 Flamingock (https://www.flamingock.io)
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
package io.flamingock.community.sql.changes.mysql.failedWithRollback;

import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.TargetSystem;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

@TargetSystem(id = "sql")
@Change(id = "create-index", transactional = false, author = "aperezdieppa")
public class _001__create_index {

	@Apply
	public void execution(Connection connection) throws SQLException {
		try (Statement stmt = connection.createStatement()) {
			stmt.executeUpdate("CREATE INDEX idx_standalone_index ON test_table(field1, field2)");
		}
	}
}
