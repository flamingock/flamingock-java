package io.mongock.runner.springboot.config;

import io.mongock.driver.api.driver.ChangeSetDependency;
import io.mongock.driver.api.driver.ConnectionDriver;
import io.mongock.driver.api.entry.ChangeEntryService;
import io.mongock.driver.api.lock.LockManager;
import io.mongock.runner.springboot.MongockScanPackage;
import io.mongock.runner.springboot.RunnerSpringbootBuilder;
import io.mongock.runner.springboot.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.*;

import static org.mockito.Mockito.*;

public class MongockContextTest {

  private ChangeEntryService changeEntryService;
  private LockManager lockManager;
  private ConnectionDriver driver;
  private CallVerifier callVerifier;
  private ApplicationContext springContext;

  @BeforeEach
  public void setUp() {
    lockManager = mock(LockManager.class);
    changeEntryService = mock(ChangeEntryService.class);
    driver = mock(ConnectionDriver.class);
    when(driver.getLockManager()).thenReturn(lockManager);
    when(driver.getChangeEntryService()).thenReturn(changeEntryService);

    callVerifier = new CallVerifier();
    Set<ChangeSetDependency> dependencySet = new HashSet<>();
    dependencySet.add(new ChangeSetDependency(CallVerifier.class, callVerifier, false));
    when(driver.getDependencies()).thenReturn(dependencySet);


    Environment environment = mock(Environment.class);
    when(environment.getActiveProfiles()).thenReturn(new String[]{"profileIncluded1", "profileIncluded2"});
    springContext = mock(ApplicationContext.class);
    when(springContext.getEnvironment()).thenReturn(environment);
    when(springContext.getBean(Environment.class)).thenReturn(environment);
    when(springContext.getBean(TemplateForTest.class)).thenReturn(new TemplateForTestImpl());
    when(springContext.getBean(InterfaceDependency.class)).thenReturn(new InterfaceDependencyImpl());
    when(springContext.getBean(ClassNotInterfaced.class)).thenReturn(new ClassNotInterfaced());
  }

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
