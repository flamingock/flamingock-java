package io.changock.migration.api.annotations;

/**
 * For old classes, this annotation can remain. It won't be removed from the legacy library.
 * For new classes, please use io.flamingock.core.api.annotations
 * <p>
 * @see io.flamingock.core.api.annotations.NonLockGuarded
 */
@Deprecated
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
