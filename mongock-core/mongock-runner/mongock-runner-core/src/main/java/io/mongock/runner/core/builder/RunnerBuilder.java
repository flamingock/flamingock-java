package io.mongock.runner.core.builder;

import io.mongock.api.config.MongockConfiguration;
import io.mongock.runner.core.builder.roles.*;
import io.mongock.runner.core.event.EventPublisher;

@SuppressWarnings("all")
public interface RunnerBuilder<
    SELF extends RunnerBuilder<SELF, CONFIG>,
    CONFIG extends MongockConfiguration>
    extends
		ChangeLogScanner<SELF, CONFIG>,
		MigrationWriter<SELF, CONFIG>,
		LegacyMigrator<SELF, CONFIG>,
		DriverConnectable<SELF, CONFIG>,
		SystemVersionable<SELF, CONFIG>,
		DependencyInjectable<SELF>,
		ServiceIdentificable<SELF, CONFIG>,
    MongockRunnable<SELF, CONFIG>,
        Transactioner<SELF, CONFIG>,
		TransactionStrategiable<SELF, CONFIG>,
    ModuleConfigurationProvider<SELF, CONFIG> {
  
  SELF setEventPublisher(EventPublisher eventPublisher);
}
