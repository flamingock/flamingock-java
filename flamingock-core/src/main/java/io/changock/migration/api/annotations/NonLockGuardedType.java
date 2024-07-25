package io.changock.migration.api.annotations;

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
