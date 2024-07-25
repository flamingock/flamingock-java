package io.utils;

import io.flamingock.core.api.annotations.BeforeExecution;
import io.flamingock.core.api.annotations.Execution;
import io.flamingock.core.api.annotations.RollbackBeforeExecution;
import io.flamingock.core.api.annotations.RollbackExecution;

public class TestExecutionTrackerTask {


    public static final TaskExecutionChecker checker = new TaskExecutionChecker();


    @BeforeExecution
    public void beforeExecution() {
        checker.markBeforeExecution();
    }

    @RollbackBeforeExecution
    public void rollbackBeforeExecution() {
        checker.markBeforeExecutionRollBack();
    }

    @Execution
    public void execution() {
        checker.markExecution();
    }

    @RollbackExecution
    public void rollbackExecution() {
        checker.markRollBackExecution();
    }
}
