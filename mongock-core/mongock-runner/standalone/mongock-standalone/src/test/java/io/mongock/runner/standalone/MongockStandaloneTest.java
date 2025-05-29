package io.mongock.runner.standalone;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.*;

public class MongockStandaloneTest {

  @Test
  void shouldAddPackage_whenAddMethodCalled() {
    // Arrange
    RunnerStandaloneBuilder builder = spy(MongockStandaloneFixture.builder(false));
    String expectedPackage = "com.example.packageone.migrations";

    // Implementação simples do módulo
    class TestConfig implements MongockModuleConfig {

      @Override
      public void configure(MongockScanPackage scan) {
        scan.add(expectedPackage);
      }
    };
    TestConfig testConfig = spy(new TestConfig());

    // Act
    builder.addModuleConfig(testConfig);

    // Assert
    verify(testConfig).configure(any());
    verify(builder).addMigrationScanPackage(expectedPackage);
  }

  @Test
  void shouldAddPackage_whenAddAllMethodCalled() {
    // Arrange
    RunnerStandaloneBuilder builder = spy(MongockStandaloneFixture.builder(false));
    List<String> expectedPackageList = new ArrayList<>();
    expectedPackageList.addAll(Arrays.asList(
        "com.example.packageone.migrations",
        "com.example.packagetwo.migrations",
        "com.example.packagetree.migrations"
    ));

    // Implementação simples do módulo
    class TestConfig implements MongockModuleConfig {

      @Override
      public void configure(MongockScanPackage scan) {
        scan.addAll(expectedPackageList);
      }
    };
    TestConfig testConfig = spy(new TestConfig());

    // Act
    builder.addModuleConfig(testConfig);

    // Assert
    verify(testConfig).configure(any());
    verify(builder).addMigrationScanPackages(expectedPackageList);
  }

  @Test
  void shouldDoNothing_WhenCreatedMongockModuleConfigButNoAddMethodCalled() {
    // Arrange
    RunnerStandaloneBuilder builder = spy(MongockStandaloneFixture.builder(false));

    // Implementação simples do módulo
    class TestConfig implements MongockModuleConfig {

      @Override
      public void configure(MongockScanPackage scan) {
      }
    };
    TestConfig testConfig = spy(new TestConfig());

    // Act
    builder.addModuleConfig(testConfig);

    // Assert
    verify(testConfig).configure(any());
    verify(builder, never()).addMigrationScanPackage(any());
  }


}
