package io.flamingock.internal.core.builder.runner;

import io.flamingock.internal.core.runner.AbstractOperationResult;
import io.flamingock.internal.core.runner.Operation;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;

public class DefaultRunner implements Runner {
    private static final Logger logger = FlamingockLoggerFactory.getLogger("PipelineRunner");

    private final RunnerId runnerId;

    private final Runnable finalizer;
    private final Operation<?> operation;

    public DefaultRunner(RunnerId runnerId,
                         Runnable finalizer,
                         Operation<?> operation) {
        this.runnerId = runnerId;
        this.operation = operation;
        this.finalizer = finalizer;
    }

    @Override
    public void run() {
        try {
            AbstractOperationResult result = operation.execute();
        } catch (Throwable throwable) {
            throw new RuntimeException("blabla");
        } finally {
            finalizer.run();
        }
    }

}
