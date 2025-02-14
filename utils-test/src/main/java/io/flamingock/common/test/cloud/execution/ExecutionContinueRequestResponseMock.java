package io.flamingock.common.test.cloud.execution;


import io.flamingock.common.test.cloud.mock.MockRequestResponseTask;
import io.flamingock.common.test.cloud.MockRunnerServer;

public class ExecutionContinueRequestResponseMock extends ExecutionBaseRequestResponseMock {

    public ExecutionContinueRequestResponseMock(long acquiredForMillis, MockRequestResponseTask... stages) {
        super(null, acquiredForMillis, null, stages);
    }

    public ExecutionContinueRequestResponseMock(MockRequestResponseTask... stages) {
        super(null, MockRunnerServer.DEFAULT_ACQUIRED_FOR_MILLIS, null, stages);
    }
}
