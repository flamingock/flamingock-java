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
package io.mongock.api.annotations;

import com.github.cloudyrock.mongock.ChangeSet;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * For old classes, this annotation can remain. It won't be removed from the legacy library.
 * For new classes, use io.flamingock.core.api.annotations.ChangeUnits
 * <p>
 * @see io.flamingock.core.api.annotations.ChangeUnit
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ChangeUnit {

  /**
   * Change unit's id. It will be used to identify both change entries, the one linked to @Execution and @BeforeExecution(this one with the suffix `_before`)
   * Obligatory.
   * <p>
   * Equivalent to field `id` in ChangeSet annotation
   *
   * @return Change unit's id
   * @see ChangeSet
   */
  String id();

  /**
   * Sequence that provide correct order for change unit execution. Sorted alphabetically, ascending.
   * Obligatory.
   * <p>
   * Equivalent to field `order` in ChangeSet annotation and ChangeLog,
   * as now there is only one "changeSet", annotated with @Execution
   *
   * @return Change unit's execution order
   * @see ChangeSet
   */
  String order();

  /**
   * Author of the changeset.
   * <p>
   * Equivalent to field `author` in ChangeSet annotation
   *
   * @return Change unit's author
   * @see ChangeSet
   */
  String author() default "";

  /**
   * If true, will make the entire migration to break if the change unit produce an exception or the validation doesn't
   * success. Migration will continue otherwise.
   * <p>
   * Equivalent to field `failFast` in ChangeSet annotation
   *
   * @return failFast
   * @see ChangeSet
   */
  boolean failFast() default true;

  /**
   * Executes the change set on every Mongock's execution, even if it has been run before.
   * Optional (default is false)
   * <p>
   * Equivalent to field `runAlways` in ChangeSet annotation
   *
   * @return should run always?
   * @see ChangeSet
   */
  boolean runAlways() default false;

  /**
   * Specifies the software systemVersion on which the change unit is to be applied.
   * Optional (default is 0 and means all)
   * <p>
   * Equivalent to field `systemVersion` in ChangeSet annotation
   *
   * @return systemVersion
   * @see ChangeSet
   */
  String systemVersion() default "0";

  /**
   * If true, Mongock will try to run the changeUnit in a native transaction, if possible.
   *
   * @return If the changeUnit should be run in a native transaction.
   */
  boolean transactional() default true;

}

