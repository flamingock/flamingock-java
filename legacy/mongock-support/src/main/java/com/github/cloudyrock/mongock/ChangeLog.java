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


import io.mongock.api.annotations.ChangeUnit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * From Mongock version 5, this annotation is deprecated and shouldn't be used (remains in code for backwards compatibility).
 *
 * Please follow one of the recommended approaches depending on your use case:
 *  - For existing changeLogs/changeSets created prior version 5: leave them untouched (use with the deprecated annotation).
 *
 *  - For new changeLogs/changeSets created  from version 5: Annotated you class migration class with the annotation @ChangeUnit.
 * 
 * For more details please visit the <a href="https://docs.mongock.io/v5/migration/index.html">migration guide</a>.
 *
 * @see ChangeUnit
 */
@Deprecated
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ChangeLog {
  /**
   * Sequence that provide an order for changelog classes.
   * If not set, then canonical name of the class is taken and sorted alphabetically, ascending.
   *
   * @return order
   */
  String order() default "";

  /**
   * If true, will make the entire migration to break if the changeLog produce an exception or the validation doesn't
   * success. Migration will continue otherwise.
   *
   * @return failFast
   */
  boolean failFast() default true;
}
