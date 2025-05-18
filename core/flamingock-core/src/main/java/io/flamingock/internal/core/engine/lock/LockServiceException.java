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

package io.flamingock.internal.core.engine.lock;

/**
 * @since 04/04/2018
 */
public class LockServiceException extends RuntimeException {

  /**
   * Condition to update/insert lock
   */
  private final String acquireLockQuery;

  /**
   * NewLock entity
   */
  private final String newLockEntity;

  /**
   * Further db error detail
   */
  private final String dbErrorDetail;

  public LockServiceException(String acquireLockQuery, String newLockEntity, String dbErrorDetail) {
    this.acquireLockQuery = acquireLockQuery;
    this.newLockEntity = newLockEntity;
    this.dbErrorDetail = dbErrorDetail;
  }

  public String getAcquireLockQuery() {
    return acquireLockQuery;
  }

  public String getNewLockEntity() {
    return newLockEntity;
  }

  public String getErrorDetail() {
    return dbErrorDetail;
  }

  @Override
  public String getMessage() {
    return toString();
  }

  @Override
  public String toString() {
    return "LockPersistenceException{" +
        ", acquireLockQuery='" + acquireLockQuery + '\'' +
        ", newLockEntity='" + newLockEntity + '\'' +
        ", dbErrorDetail='" + dbErrorDetail + '\'' +
        "} ";
  }
}
