package io.flamingock.internal.core.runner;

public interface Operation<R extends AbstractOperationResult> {

    R execute();
}
