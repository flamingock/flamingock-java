package io.flamingock.springboot;

import io.flamingock.internal.core.runner.Runner;
import io.flamingock.internal.core.runner.RunnerBuilder;

public class DummyRunnerBuilder implements RunnerBuilder {
    @Override
    public Runner build() {
        return null;
    }
}
