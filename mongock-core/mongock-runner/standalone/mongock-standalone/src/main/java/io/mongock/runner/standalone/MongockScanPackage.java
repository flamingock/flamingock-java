package io.mongock.runner.standalone;


import java.util.List;

public class MongockScanPackage {

  private RunnerStandaloneBuilder builder;

  public MongockScanPackage(RunnerStandaloneBuilder builder) {
    this.builder = builder;
  }

  public void add(String packageName) {
    if (packageName != null && !packageName.isEmpty()) {
      builder.addMigrationScanPackage(packageName);
    }
  }

  public void addAll(List<String> packageNames) {
    if (!packageNames.isEmpty()) {
      builder.addMigrationScanPackages(packageNames);
    }
  }

}
