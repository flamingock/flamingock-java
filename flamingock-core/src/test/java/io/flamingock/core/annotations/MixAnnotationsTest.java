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

import io.flamingock.core.api.annotations.ChangeUnit;
import io.flamingock.core.api.annotations.Execution;
import io.flamingock.core.api.annotations.RollbackExecution;
import io.flamingock.core.pipeline.execution.ExecutionContext;
import io.flamingock.core.utils.TaskExecutionChecker;
import io.flamingock.core.utils.TestTaskExecution;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

class MixAnnotationsTest {

    private static final TaskExecutionChecker CHECKER = new TaskExecutionChecker();


    @Test
    @DisplayName("should fail when new ChangeUnit wih old execution")
    void shouldFailWhenNewChangeUnitAndOldExecution() {
        IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class, () ->
                TestRunner.runTest(
                        NewChangeUnitWithOldExecution.class,
                        1,
                        CHECKER,
                        TestTaskExecution.EXECUTION));

        Assertions.assertTrue(ex.getMessage().contains("without io.flamingock.core.api.annotations.Execution method"));
    }

    @Test
    @DisplayName("should fail when new ChangeUnit wih old rollback")
    void shouldFailWhenNewChangeUnitAndOldRollback() {
        IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class, () ->
                TestRunner.runTest(
                        NewChangeUnitWithOldRollback.class,
                        1,
                        CHECKER,
                        TestTaskExecution.EXECUTION));

        Assertions.assertTrue(ex.getMessage().contains("rollback method should be annotated with new API[io.flamingock.core.api.annotations.RollbackExecution], instead of legacy API[io.mongock.api.annotations.RollbackExecution] "));
    }

    @Test
    @DisplayName("should fail when old ChangeUnit wih new execution")
    void shouldFailWhenOdChangeUnitAndNewExecution() {
        IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class, () ->
                TestRunner.runTest(
                        OldChangeUnitWithNewExecution.class,
                        1,
                        CHECKER,
                        TestTaskExecution.EXECUTION));

        Assertions.assertTrue(ex.getMessage().startsWith("You are using new API for Execution annotation in your changeUnit class"));
        Assertions.assertTrue(ex.getMessage().endsWith("however your class is annotated with legacy ChangeUnit annotation[io.mongock.api.annotations.Execution]. It's highly recommended to use the new API[in package io.flamingock.core.api.annotations], unless it's a legacy changeUnit created with Mongock"));
    }

    @Test
    @DisplayName("should fail when old ChangeUnit wih old execution and new rollback")
    void shouldFailWhenOdChangeUnitAndOldExecutionAndNewRollback() {
        IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class, () ->
                TestRunner.runTest(
                        OldChangeUnitWithOldExecutionAndNewRollback.class,
                        1,
                        CHECKER,
                        TestTaskExecution.EXECUTION));

        Assertions.assertTrue(ex.getMessage().startsWith("You are using new API for RollbackExecution annotation in your changeUnit"));
        Assertions.assertTrue(ex.getMessage().endsWith("however your class is annotated with legacy ChangeUnit annotation[io.mongock.api.annotations.RollbackExecution]. It's highly recommended to use the new API[in package io.flamingock.core.api.annotations], unless it's a legacy changeUnit created with Mongock"));
    }

    @Test
    @DisplayName("should fail when old ChangeUnit wih new execution and old rollback")
    void shouldFailWhenOdChangeUnitAndNewExecutionAndOldRollback() {
        IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class, () ->
                TestRunner.runTest(
                        OldChangeUnitWithNewExecutionAndOldRollback.class,
                        1,
                        CHECKER,
                        TestTaskExecution.EXECUTION));

        Assertions.assertTrue(ex.getMessage().startsWith("You are using new API for Execution annotation in your changeUnit class"));
        Assertions.assertTrue(ex.getMessage().endsWith("however your class is annotated with legacy ChangeUnit annotation[io.mongock.api.annotations.Execution]. It's highly recommended to use the new API[in package io.flamingock.core.api.annotations], unless it's a legacy changeUnit created with Mongock"));
    }




    @ChangeUnit(id = "taskId", order = "1")
    public static class NewChangeUnitWithOldExecution {

        @io.mongock.api.annotations.Execution
        public void execution() {
            CHECKER.markExecution();
        }

        //added but it shouldn't be executed
        @RollbackExecution
        public void rollbackExecution() {
            CHECKER.markRollBackExecution();
        }
    }

    @ChangeUnit(id = "taskId", order = "1")
    public static class NewChangeUnitWithOldRollback {

        @Execution
        public void execution() {
            CHECKER.markExecution();
        }

        //added but it shouldn't be executed
        @io.mongock.api.annotations.RollbackExecution
        public void rollbackExecution() {
            CHECKER.markRollBackExecution();
        }
    }

    @io.mongock.api.annotations.ChangeUnit(id = "taskId", order = "1")
    public static class OldChangeUnitWithNewExecution {

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

    @io.mongock.api.annotations.ChangeUnit(id = "taskId", order = "1")
    public static class OldChangeUnitWithOldExecutionAndNewRollback {

        @io.mongock.api.annotations.Execution
        public void execution() {
            CHECKER.markExecution();
        }

        //added but it shouldn't be executed
        @RollbackExecution
        public void rollbackExecution() {
            CHECKER.markRollBackExecution();
        }
    }



    @io.mongock.api.annotations.ChangeUnit(id = "taskId", order = "1")
    public static class OldChangeUnitWithNewExecutionAndOldRollback {

        @Execution
        public void execution() {
            CHECKER.markExecution();
        }

        //added but it shouldn't be executed
        @io.mongock.api.annotations.RollbackExecution
        public void rollbackExecution() {
            CHECKER.markRollBackExecution();
        }
    }
}