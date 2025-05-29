package io.mongock.runner.springboot.config;

import io.mongock.runner.springboot.MongockScanPackage;
import io.mongock.runner.springboot.RunnerSpringbootBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;

import java.util.*;

import static org.mockito.Mockito.*;

public class MongockContextTest {

  @Test
  void shouldAddPackagesToBuilder_whenMongockScanPackageAnnotationIsPresent() {
    // Arrange
    MongockContext context = new MongockContext();
    ApplicationContext springContext = mock(ApplicationContext.class);
    RunnerSpringbootBuilder builder = mock(RunnerSpringbootBuilder.class);

    @MongockScanPackage({
        "com.example.packageone.migrations",
        "com.example.packagetwo.migrations"
    })
    @Configuration
    class TestConfiguration {}

    Map<String, Object> beans = new HashMap<>();
    beans.put("bean", new TestConfiguration());
    when(springContext.getBeansWithAnnotation(MongockScanPackage.class)).thenReturn(beans);

    // Act
    context.addPackagesFromAnnotationMongockScanPackage(builder, springContext);

    // Assert
    verify(builder).addMigrationScanPackages(Arrays.asList(
        "com.example.packageone.migrations",
        "com.example.packagetwo.migrations"
    ));
  }

  @Test
  void shouldDoNothing_WhenMongockScanPackageIsNotPresent() {
    // Arrange
    MongockContext context = new MongockContext();
    ApplicationContext springContext = mock(ApplicationContext.class);
    RunnerSpringbootBuilder builder = mock(RunnerSpringbootBuilder.class);

    // Act
    context.addPackagesFromAnnotationMongockScanPackage(builder, springContext);

    // Assert
    verify(builder, never()).addMigrationScanPackages(any());
  }

}
