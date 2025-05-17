package io.flamingock.core.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class TaskExecutionChecker {

    private final List<TestTaskExecution> history = new ArrayList<>();

    public TaskExecutionChecker() {
    }

    public void reset() {
        history.clear();
    }

    public boolean isExecuted() {
        return history.contains(TestTaskExecution.EXECUTION);
    }

    public void markExecution() {
        history.add(TestTaskExecution.EXECUTION);
    }

    public boolean isRolledBack() {
        return history.contains(TestTaskExecution.ROLLBACK_EXECUTION);
    }

    public void markRollBackExecution() {
        history.add(TestTaskExecution.ROLLBACK_EXECUTION);
    }

    public boolean isBeforeExecuted() {
        return history.contains(TestTaskExecution.BEFORE_EXECUTION);
    }

    public void markBeforeExecution() {
        history.add(TestTaskExecution.BEFORE_EXECUTION);
    }

    public boolean isBeforeExecutionRolledBack() {
        return history.contains(TestTaskExecution.ROLLBACK_BEFORE_EXECUTION);
    }

    public void markBeforeExecutionRollBack() {
        history.add(TestTaskExecution.ROLLBACK_BEFORE_EXECUTION);
    }

    public void checkOrderStrict(TestTaskExecution execution, TestTaskExecution... otherExecutions) {
        List<TestTaskExecution> allExecutions = new ArrayList<>();
        allExecutions.add(execution);
        allExecutions.addAll(Arrays.asList(otherExecutions));
        checkOrderStrict(allExecutions);
    }

    public void checkOrderStrict(List<TestTaskExecution> executions) {
        if(executions.size() != history.size()) {
            throw new RuntimeException(String.format("(strict)Expected executions[%d] doesn't match actual executions[%d]" +
                            "\nexpected:\n\t%s" +
                            "\nactual:\n\t%s\n",
                    executions.size(),
                    history.size(),
                    executions.stream().map(TestTaskExecution::name).collect(Collectors.joining(",\n\t")),
                    history.stream().map(TestTaskExecution::name).collect(Collectors.joining(",\n\t"))
            ));
        }
        checkExecutions(executions);
    }


    public void checkOrder(TestTaskExecution execution, TestTaskExecution... otherExecutions) {

        List<TestTaskExecution> allExecutions = new ArrayList<>();
        allExecutions.add(execution);
        allExecutions.addAll(Arrays.asList(otherExecutions));

        checkExecutions(allExecutions);
    }

    private void checkExecutions(List<TestTaskExecution> allExecutions) {
        for (int index = 0; index < allExecutions.size(); index++) {
            if (history.size() <= index) {
                throw new RuntimeException(String.format("history[%d executions] shorter than expected history",
                        history.size()));
            }

            if (allExecutions.get(index) != history.get(index)) {
                throw new RuntimeException(String.format("Execution not matched at index[%d]. Expected[%s] actual[%s]",
                        index, allExecutions.get(index), history.get(index)));
            }
        }
    }

}
