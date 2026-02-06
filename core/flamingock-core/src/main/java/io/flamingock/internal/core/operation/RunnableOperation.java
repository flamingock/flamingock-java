package io.flamingock.internal.core.operation;

public class RunnableOperation<T extends OperationArgs, R extends AbstractOperationResult> {
    private final Operation<T,R> operation;
    private final T args;

    public RunnableOperation(Operation<T, R> operation, T args) {
        this.operation = operation;
        this.args = args;
    }

    public R run() {
        return operation.execute(args);
    }
}
