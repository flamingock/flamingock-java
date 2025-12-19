package io.flamingock.springboot.testsupport;

import io.flamingock.internal.core.builder.AbstractChangeRunnerBuilder;
import io.flamingock.support.FlamingockTestSupport;
import io.flamingock.support.stages.GivenStage;

public class FlamingockSpringBootTestSupport {

    private final AbstractChangeRunnerBuilder<?, ?> builderFromContext;

    FlamingockSpringBootTestSupport(AbstractChangeRunnerBuilder<?,?> builderFromContext) {
        this.builderFromContext = builderFromContext;
    }

    public GivenStage givenBuilderFromContext() {
        return FlamingockTestSupport.givenBuilder(builderFromContext);
    }

    public GivenStage givenBuilder(AbstractChangeRunnerBuilder<?,?> builderFromContextOverwrite) {
        return FlamingockTestSupport.givenBuilder(builderFromContextOverwrite);
    }
}
