package io.flamingock.internal.core.operation;

import io.flamingock.internal.core.pipeline.loaded.LoadedPipeline;

public class ExecuteArgs implements OperationArgs {

    private final LoadedPipeline pipeline;

    public ExecuteArgs(LoadedPipeline pipeline) {
        this.pipeline = pipeline;
    }

    public LoadedPipeline getPipeline() {
        return pipeline;
    }
}
