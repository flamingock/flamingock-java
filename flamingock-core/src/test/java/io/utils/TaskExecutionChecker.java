package io.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import static io.utils.TestTaskExecution.BEFORE_EXECUTION;
import static io.utils.TestTaskExecution.EXECUTION;
import static io.utils.TestTaskExecution.ROLLBACK_BEFORE_EXECUTION;
import static io.utils.TestTaskExecution.ROLLBACK_EXECUTION;

public class TaskExecutionChecker {

    private final List<TestTaskExecution> history = new ArrayList<>();

    public TaskExecutionChecker() {
    }

    public void reset() {
        history.clear();
    }

    public boolean isExecuted() {
        return history.contains(EXECUTION);
    }

    public void markExecution() {
        history.add(EXECUTION);
    }

    public boolean isRolledBack() {
        return history.contains(ROLLBACK_EXECUTION);
    }

    public void markRollBackExecution() {
        history.add(ROLLBACK_EXECUTION);
    }

    public boolean isBeforeExecuted() {
        return history.contains(BEFORE_EXECUTION);
    }

    public void markBeforeExecution() {
        history.add(BEFORE_EXECUTION);
    }

    public boolean isBeforeExecutionRolledBack() {
        return history.contains(ROLLBACK_BEFORE_EXECUTION);
    }

    public void markBeforeExecutionRollBack() {
        history.add(ROLLBACK_BEFORE_EXECUTION);
    }

    public void checkOrderStrict(TestTaskExecution execution, TestTaskExecution... otherExecutions) {
        List<TestTaskExecution> allExecutions = new ArrayList<>();
        allExecutions.add(execution);
        allExecutions.addAll(Arrays.asList(otherExecutions));
        if(allExecutions.size() != history.size()) {
            throw new RuntimeException(String.format("(strict)Expected executions[%d] doesn't match actual executions[%d]" +
                    "\nexpected:\n\t%s" +
                    "\nactual:\n\t%s\n",
                    allExecutions.size(),
                    history.size(),
                    allExecutions.stream().map(TestTaskExecution::name).collect(Collectors.joining(",\n\t")),
                    history.stream().map(TestTaskExecution::name).collect(Collectors.joining(",\n\t"))
                    ));
        }
        checkExecutions(allExecutions);
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
