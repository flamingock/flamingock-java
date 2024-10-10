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
