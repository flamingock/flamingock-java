/*
 * Copyright 2025 Flamingock (https://www.flamingock.io)
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
package com.github.cloudyrock.mongock;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * From Mongock version 5, this annotation is deprecated and shouldn't be used (remains in code for backwards compatibility).
 * Please follow one of the recommended approaches depending on your use case:
 *  - For existing changeLogs/changeSets created prior version 5: leave them untouched (use with the deprecated annotation).
 *  - For new changeLogs/changeSets created from version 5: use ChangeUnit annotated methods (@Execution / @RollbackExecution).
 * For more details please visit the <a href="https://docs.mongock.io/v5/migration/index.html">migration guide</a>.
 *
 * @see ChangeLog
 */
@Deprecated
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ChangeSet {

  /**
   * Author of the changeset.
   * Obligatory
   *
   * @return author
   */
  String author();  // must be set

  /**
   * Unique ID of the changeset.
   * Obligatory
   *
   * @return unique id
   */
  String id();// must be set

  /**
   * Sequence that provide correct order for changeSets. Sorted alphabetically, ascending.
   * Obligatory.
   *
   * @return ordering
   */
  String order();// must be set

  /**
   * Executes the change set on every Mongock's execution, even if it has been run before.
   * Optional (default is false)
   *
   * @return should run always?
   */
  boolean runAlways() default false;

  /**
   * Specifies the software systemVersion on which the ChangeSet is to be applied.
   * Optional (default is 0 and means all)
   *
   * @return systemVersion
   */
  String systemVersion() default "0";

  /**
   * If true, will make the entire migration to break if the changeSet produce an exception or the validation doesn't
   * success. Migration will continue otherwise.
   *
   * @return failFast
   */
  boolean failFast() default true;

}
