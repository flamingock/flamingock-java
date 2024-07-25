/*
 * Copyright 2023 Flamingock (https://oss.flamingock.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.flamingock.core.configurator.legacy;

import io.changock.migration.api.annotations.NonLockGuarded;
import io.changock.migration.api.annotations.NonLockGuardedType;

@NonLockGuarded(NonLockGuardedType.NONE)
public class LegacyMigration {

  private String origin;

  private boolean failFast = true;

  private Integer changesCountExpectation = null;

  private LegacyMigrationMappingFields mappingFields = new LegacyMigrationMappingFields();

  private boolean runAlways = false;

  private boolean repair = false;


  public LegacyMigration() {
  }

  public LegacyMigration(String origin) {
    setOrigin(origin);
  }


  public LegacyMigrationMappingFields getMappingFields() {
    return mappingFields;
  }

  public void setMappingFields(LegacyMigrationMappingFields mappingFields) {
    this.mappingFields = mappingFields;
  }

  public Integer getChangesCountExpectation() {
    return changesCountExpectation;
  }

  public void setChangesCountExpectation(Integer changesCountExpectation) {
    this.changesCountExpectation = changesCountExpectation;
  }

  public boolean isRunAlways() {
    return runAlways;
  }

  public void setRunAlways(boolean runAlways) {
    this.runAlways = runAlways;
  }

  public String getOrigin() {
    return origin;
  }

  public void setOrigin(String origin) {
    this.origin = origin;
  }

  public boolean isFailFast() {
    return failFast;
  }

  public void setFailFast(boolean failFast) {
    this.failFast = failFast;
  }

  public boolean isRepair() {
    return repair;
  }

  public void setRepair(boolean repair) {
    this.repair = repair;
  }

}
