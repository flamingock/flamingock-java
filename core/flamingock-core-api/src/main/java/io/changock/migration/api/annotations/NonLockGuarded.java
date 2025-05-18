package io.changock.migration.api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * For old classes, this annotation can remain. It won't be removed from the legacy library.
 * For new classes, please use io.flamingock.core.api.annotations
 * <p>
 * @see io.flamingock.core.api.annotations.NonLockGuarded
 */
@Deprecated
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
