package io.flamingock.springboot;

import io.flamingock.internal.common.core.metadata.FlamingockMetadata;
import io.flamingock.internal.core.runner.RunnerBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;


class FlamingockAutoConfigurationTests {

    private static final String PROFILE = "non-cli";   // adjust if Constants.NON_CLI_PROFILE differs
    private static final String META_INF_PATH = "META-INF/flamingock/full-pipeline.json";

    /**
     * Helper that builds a context runner with a custom class-loader
     * containing the supplied JSON as the metadata file.
     */
    private ApplicationContextRunner contextWithJson(String json, @TempDir Path dir)
            throws IOException {

        ApplicationContextRunner applicationContextRunner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FlamingockAutoConfiguration.class));

        // Create META-INF/flamingock directory structure
        if(json != null) {
            Path metaInfDir = dir.resolve("META-INF/flamingock");
            Files.createDirectories(metaInfDir);
            Files.writeString(metaInfDir.resolve("full-pipeline.json"), json);
            // Build a new class loader that exposes the temp directory
            URL[] urls = { dir.toUri().toURL() };
            ClassLoader cl = new URLClassLoader(urls, getClass().getClassLoader());
            applicationContextRunner = applicationContextRunner.withClassLoader(cl);
        }

        return applicationContextRunner
                .withBean("flamingock-builder", RunnerBuilder.class, DummyRunnerBuilder::new)
                .withPropertyValues("spring.profiles.active=" + PROFILE);
    }

    private ApplicationContextRunner contextEmpty() throws IOException {
        return contextWithJson(null, null);
    }

    @BeforeEach
    public void setup() {
        // restore real supplier so other tests are unaffected
        OnFlamingockEnabledCondition.restoreMetadataSupplier();
    }

    @Test
    void runnerBeanRegisteredWhenSetupDefault(@TempDir Path dir) throws IOException {
        String json = """
                      {
                        "setup": "DEFAULT"
                      }
                      """;

        contextWithJson(json, dir).run(ctx ->
                assertThat(ctx).hasBean("flamingock-runner"));
    }

    @Test
    void runnerBeanRegisteredWhenSetupAbsent(@TempDir Path dir) throws IOException {
        String json = "{}";   // no 'setup' key

        contextWithJson(json, dir).run(ctx ->
                assertThat(ctx).hasBean("flamingock-runner"));
    }

    @Test
    void runnerBeanNotRegisteredWhenSetupBuilder() throws IOException {
        // replace the supplier so the Condition "sees" BUILDER
        OnFlamingockEnabledCondition.setMetadataSupplier(
                () -> Optional.of(new FlamingockMetadata(null, "BUILDER", null))
        );

        contextEmpty().run(ctx -> assertThat(ctx).doesNotHaveBean("flamingock-runner"));
    }
}
