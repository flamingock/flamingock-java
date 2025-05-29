package io.mongock.runner.springboot.config;

import io.mongock.runner.springboot.MongockScanPackage;
import io.mongock.runner.springboot.RunnerSpringbootBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;

import java.util.*;

import static org.mockito.Mockito.*;

public class MongockScanPackageTest {

  @Test
  void shouldAddPackagesToBuilder_whenMongockScanPackageAnnotationIsPresentAndProvidePackages() {
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
    String expectedBeanName = "testConfigurationBean";
    beans.put(expectedBeanName, new TestConfiguration());
    when(springContext.getBeansWithAnnotation(MongockScanPackage.class)).thenReturn(beans);
    List<String> packagesToScan = Arrays.asList(
        "com.example.packageone.migrations",
        "com.example.packagetwo.migrations"
    );

    // Act
    context.addPackagesFromAnnotationMongockScanPackage(builder, springContext);

    // Assert
    verify(builder, times(1)).addMigrationScanPackages(packagesToScan);
    verify(springContext, times(1)).getBeansWithAnnotation(MongockScanPackage.class);
    Assertions.assertEquals(1, springContext.getBeansWithAnnotation(MongockScanPackage.class).size());

    Object bean = springContext.getBeansWithAnnotation(MongockScanPackage.class).get(expectedBeanName);
    MongockScanPackage annotation = bean.getClass().getAnnotation(MongockScanPackage.class);
    Assertions.assertEquals(packagesToScan.size(), annotation.value().length);
  }

  @Test
  void shouldDoNothing_WhenAnnotationMongockScanPackageIsNotPresent() {
    // Arrange
    MongockContext context = new MongockContext();
    ApplicationContext springContext = mock(ApplicationContext.class);
    RunnerSpringbootBuilder builder = mock(RunnerSpringbootBuilder.class);
    when(springContext.getBeansWithAnnotation(MongockScanPackage.class)).thenReturn(Collections.emptyMap());

    // Act
    context.addPackagesFromAnnotationMongockScanPackage(builder, springContext);

    // Assert
    verify(builder, never()).addMigrationScanPackages(any());
    verify(springContext, times(1)).getBeansWithAnnotation(MongockScanPackage.class);
  }

}
