package io.mongock.runner.standalone;

import io.mongock.api.config.MongockConfiguration;
import io.mongock.runner.core.executor.ExecutorBuilder;
import io.mongock.runner.core.executor.ExecutorBuilderDefault;
import io.mongock.runner.core.executor.changelog.ChangeLogService;
import io.mongock.runner.standalone.base.StandaloneBuilderBase;

import static io.mongock.runner.core.builder.BuilderType.COMMUNITY;
import io.mongock.runner.core.executor.changelog.ChangeLogServiceBase;

public final class MongockStandalone {

  //TODO javadoc
  public static RunnerStandaloneBuilder builder() {
    return new RunnerStandaloneBuilderImpl(new ExecutorBuilderDefault(), new ChangeLogService(), new MongockConfiguration());
  }

  public static class RunnerStandaloneBuilderImpl extends StandaloneBuilderBase<RunnerStandaloneBuilderImpl, MongockConfiguration>
      implements RunnerStandaloneBuilder {

    RunnerStandaloneBuilderImpl(ExecutorBuilder<MongockConfiguration> executorFactory, ChangeLogServiceBase changeLogService, MongockConfiguration config) {
      super(COMMUNITY, executorFactory, changeLogService, config);
    }

    @Override
    public RunnerStandaloneBuilderImpl getInstance() {
      return this;
    }

    @Override
    public RunnerStandaloneBuilder addModuleConfig(MongockModuleConfig mongockModuleConfig) {
      mongockModuleConfig.configure(new MongockScanPackage(this));
      return this;
    }
  }


}
