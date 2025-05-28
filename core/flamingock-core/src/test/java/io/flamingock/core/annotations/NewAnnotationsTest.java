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

import io.flamingock.core.api.annotations.Change;
import io.flamingock.core.api.annotations.Execution;
import io.flamingock.core.api.annotations.RollbackExecution;
import io.flamingock.core.utils.TaskExecutionChecker;
import io.flamingock.core.utils.TestTaskExecution;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NewAnnotationsTest {

    private static final TaskExecutionChecker CHECKER = new TaskExecutionChecker();


    @Test
    @DisplayName("should run only execution")
    void shouldRunOnlyExecution() {
        TestRunner.runTest(
                SingleChangeUnit.class,
                1,
                CHECKER,
                TestTaskExecution.EXECUTION);
    }

    @Test
    @DisplayName("should run rollback")
    void shouldRunRollback() {
        TestRunner.runTest(
                ChangeUnitWithExecutionError.class,
                1,
                CHECKER,
                TestTaskExecution.EXECUTION,
                TestTaskExecution.ROLLBACK_EXECUTION);
    }

    @Test
    @DisplayName("should not run rollback when transaction")
    void shouldNotRunRollbackWhenTransaction() {
        TestRunner.runTestWithTransaction(
                ChangeUnitWithExecutionError.class,
                1,
                CHECKER,
                TestTaskExecution.EXECUTION
        );
    }

    @Change(id = "taskId", order = "001")
    public static class SingleChangeUnit {

        @Execution
        public void execution() {
            CHECKER.markExecution();
        }

        //added but it shouldn't be executed
        @RollbackExecution
        public void rollbackExecution() {
            CHECKER.markRollBackExecution();
        }
    }

    @Change(id = "taskId", order = "001")
    public static class ChangeUnitWithExecutionError {

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