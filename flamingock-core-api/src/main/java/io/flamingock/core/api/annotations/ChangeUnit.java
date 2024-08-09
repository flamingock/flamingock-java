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

package io.flamingock.core.api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation replaces the old annotation @ChangeLog(and changeSet).
 * <p>
 * The main difference is that classes annotated with @ExecutableChangeUnit can only have one changeSet method, annotated with @Execution and optionally
 * another changeSet annotated with @BeforeExecution, which is run before the actual change is executed, this means, for example, that the method
 * annotated with @BeforeExecution will be out of the native transaction linked to the changeLog, however, Mongock will try to revert the changes applied
 * by the method @BeforeExecution by executing the method annotated with @RollbackBeforeExecution
 * <p>
 * The concept is basically the same, a class that wraps the logic of the migration
 * <p>
 * Classes annotated with @ExecutableChangeUnit must have the following:
 * - One(and only one) one valid constructor annotated with @ChangeUnitConstructor(mandatory if more than one constructor exist after version 6)
 * - One(and only one) method annotated with @Execution(mandatory)
 * - One(and only one) method annotated with @RollbackExecution(mandatory)
 * - At most, one method annotated with @BeforeExecution(optional)
 * - If contains a method annotated with @BeforeExecution, one(and only one) method annotated with @RollbackBeforeExecution(mandatory if @BeforeExecution present)
 * <p>
 * Please follow one of the recommended approaches depending on your use case:
 * - For existing changeLogs/changeSets created prior version 5: leave them untouched (use with the deprecated annotation)
 * <p>
 * - For new changeLogs/changeSets created  from version 5: Annotated you class migration class with the annotation @ExecutableChangeUnit
 *
 * @see ChangeUnitConstructor
 * @see Execution
 * @see BeforeExecution
 * @see RollbackExecution
 * @see RollbackBeforeExecution
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ChangeUnit {

  /**
   * Change unit's id. It will be used to identify both change entries, the one linked to @Execution and @BeforeExecution(this one with the suffix `_before`)
   * Equivalent to field `id` in ChangeSet annotation
   *
   * @return Change unit's id
   */
  String id();

  /**
   * Equivalent to field `order` in ChangeSet annotation and ChangeLog,
   * as now there is only one "changeSet", annotated with @Execution
   *
   * @return ChangeSet's author
   */
  String order();

  /**
   * Equivalent to field `author` in ChangeSet annotation
   *
   * @return ChangeSet's author
   */
  String author() default "";

  /**
   * Equivalent to field `failFast` in ChangeSet annotation
   *
   * @return ChangeSet if the ChangeLog is fail fast
   */
  boolean failFast() default true;

  /**
   * Equivalent to field `runAlways` in ChangeSet annotation
   *
   * @return ChangeSet if the ChangeLog is runAlways
   */
  @Deprecated
  boolean runAlways() default false;

  /**
   * Equivalent to field `systemVersion` in ChangeSet annotation
   *
   * @return ChangeSet if the ChangeLog is runAlways
   */
  String systemVersion() default "0";

  /**
   * If true, Mongock will try to run the changeUnit in a native transaction, if possible.
   *
   * @return If he changeUnit should be run in a native transaction.
   */
  boolean transactional() default true;

}
