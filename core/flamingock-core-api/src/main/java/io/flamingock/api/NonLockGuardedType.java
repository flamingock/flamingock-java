/*
 * Copyright 2025 Flamingock (https://oss.flamingock.io)
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
 
package io.flamingock.api;

public enum NonLockGuardedType {
  /**
   * Indicates the returned object shouldn't be decorated for lock guard. So clean instance is returned.
   * But still the method needs to bbe lock-guarded
   */
  RETURN,

  /**
   * Indicates the method shouldn't be lock-guarded, but still should decorate the returned object(if applies)
   */
  METHOD,

  /**
   * Indicates the method shouldn't be lock-guarded neither the returned object should be decorated for lock guard.
   */
  NONE
}
