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

package io.flamingock.cloud.transaction.sql;

public enum SqlDialect {

    //TODO upsert operation(currently handling SQLIntegrityConstraintViolationException)
    MYSQL("ONGOING_TASKS",
            "CREATE TABLE ONGOING_TASKS (task_id VARCHAR(255) not NULL, operation VARCHAR(20), PRIMARY KEY ( task_id ))",
            "SELECT task_id, operation FROM ONGOING_TASKS",
            "REPLACE INTO ONGOING_TASKS (task_id, operation) values(?, ?)",
            "DELETE FROM ONGOING_TASKS WHERE task_id=?"
    );

    private final String ongoingTasksTableName;
    private final String createOngoingTasksTable;
    private final String selectIdOngoingTask;
    private final String upsertOngoingTask;
    private final String deleteOngoingTask;

    SqlDialect(String ongoingTasksTableName,
               String createOngoingTasksTable,
               String selectIdOngoingTask,
               String upsertOngoingTask,
               String deleteOngoingTask) {
        this.ongoingTasksTableName = ongoingTasksTableName;
        this.createOngoingTasksTable = createOngoingTasksTable;
        this.selectIdOngoingTask = selectIdOngoingTask;
        this.upsertOngoingTask = upsertOngoingTask;
        this.deleteOngoingTask = deleteOngoingTask;
    }

    public String getOngoingTasksTableName() {
        return ongoingTasksTableName;
    }

    public String getCreateOngoingTasksTable() {
        return createOngoingTasksTable;
    }

    public String getSelectIdOngoingTask() {
        return selectIdOngoingTask;
    }

    public String getUpsertOngoingTask() {
        return upsertOngoingTask;
    }

    public String getDeleteOngoingTask() {
        return deleteOngoingTask;
    }
}
