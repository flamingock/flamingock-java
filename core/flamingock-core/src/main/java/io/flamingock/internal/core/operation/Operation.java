package io.flamingock.internal.core.operation;

public interface Operation<R extends AbstractOperationResult> {

    R execute();
}
