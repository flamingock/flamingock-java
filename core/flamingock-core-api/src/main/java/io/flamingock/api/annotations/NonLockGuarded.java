package io.flamingock.api.annotations;


import io.flamingock.api.NonLockGuardedType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


@Target({ElementType.METHOD, ElementType.TYPE, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface NonLockGuarded {


  /**
   * <p>Indicates the grade of non-lock-guard applied to a method.
   * Does not have any effect at class level.
   * </p>
   *
   * @return value
   */
  NonLockGuardedType[] value() default {NonLockGuardedType.METHOD, NonLockGuardedType.RETURN};


}
