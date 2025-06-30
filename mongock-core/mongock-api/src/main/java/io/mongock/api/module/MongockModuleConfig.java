package io.mongock.api.module;

import java.util.List;

/**
 * Represents a module that provides configuration to Mongock,
 * specifically the list of packages to be scanned for change units.
 * This allows for a clean and extensible way to add scan packages from different modules.
 */
public interface MongockModuleConfig {

  /**
   * @return A list of package paths to be scanned by Mongock for change units.
   */
  List<String> getChangeLogScanPackages();

}
