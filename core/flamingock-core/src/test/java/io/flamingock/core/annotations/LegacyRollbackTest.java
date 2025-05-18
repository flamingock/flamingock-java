/*
 * Copyright 2023 Flamingock (https://oss.flamingock.io)
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

package io.flamingock.core.annotations;

import io.flamingock.core.utils.TaskExecutionChecker;
import io.flamingock.core.utils.TestTaskExecution;
import io.mongock.api.annotations.BeforeExecution;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackBeforeExecution;
import io.mongock.api.annotations.RollbackExecution;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LegacyRollbackTest {

    private static final TaskExecutionChecker CHECKER = new TaskExecutionChecker();

    @Test
    @DisplayName("should run rollback")
    void shouldRunRollback() {
        TestRunner.runTest(
                ChangeUnitWithExecutionError.class,
                2,
                CHECKER,
                TestTaskExecution.BEFORE_EXECUTION,
                TestTaskExecution.EXECUTION,
                TestTaskExecution.ROLLBACK_EXECUTION);
    }


    @Test
    @DisplayName("should also run before rollback")
    void shouldRunBeforeRollback() {
        TestRunner.runTest(
                ChangeUnitWithBeforeRollbackExecution.class,
                2,
                CHECKER,
                TestTaskExecution.BEFORE_EXECUTION,
                TestTaskExecution.EXECUTION,
                TestTaskExecution.ROLLBACK_EXECUTION,
                TestTaskExecution.ROLLBACK_BEFORE_EXECUTION
        );
    }

    @Test
    @DisplayName("should not run rollback when transaction")
    void shouldNotRunRollbackWhenTransaction() {
        TestRunner.runTestWithTransaction(
                ChangeUnitWithBeforeRollbackExecution.class,
                2,
                CHECKER,
                TestTaskExecution.BEFORE_EXECUTION,
                TestTaskExecution.EXECUTION,
                TestTaskExecution.ROLLBACK_BEFORE_EXECUTION
        );
    }

    @ChangeUnit(id = "taskId", order = "1")
    public static class ChangeUnitWithExecutionError {

        @BeforeExecution
        public void beforeExecution() {
            CHECKER.markBeforeExecution();
        }

        @Execution
        public void execution() {
            CHECKER.markExecution();
            throw new RuntimeException();
        }

        @RollbackExecution
        public void rollbackExecution() {
            CHECKER.markRollBackExecution();
        }
    }

    @ChangeUnit(id = "taskId", order = "1")
    public static class ChangeUnitWithBeforeRollbackExecution {

        @BeforeExecution
        public void beforeExecution() {
            CHECKER.markBeforeExecution();
        }

        @RollbackBeforeExecution
        public void rollbackBeforeExecution() {
            CHECKER.markBeforeExecutionRollBack();
        }

        @Execution
        public void execution() {
            CHECKER.markExecution();
            throw new RuntimeException();
        }

        @RollbackExecution
        public void rollbackExecution() {
            CHECKER.markRollBackExecution();
        }
    }

}