package io.utils;

import io.flamingock.core.runtime.dependency.DependencyInjectable;
import io.flamingock.core.task.descriptor.TaskDescriptor;
import io.flamingock.core.transaction.TransactionWrapper;

import java.util.function.Supplier;

public class EmptyTransactionWrapper implements TransactionWrapper {

    private boolean called = false;
    @Override
    public <T> T wrapInTransaction(TaskDescriptor taskDescriptor, DependencyInjectable dependencyInjectable, Supplier<T> operation) {
        called = true;
        return operation.get();
    }

    public boolean isCalled() {
        return called;
    }


}
