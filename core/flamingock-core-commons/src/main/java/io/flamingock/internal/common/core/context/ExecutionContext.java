package io.flamingock.internal.common.core.context;


public interface ExecutionContext extends ContextProvider, DependencyInjectable, LayeredDependencyContext {

    String getSessionId();

}
