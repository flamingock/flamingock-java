package io.flamingock.core.engine.audit.importer.model;

import io.flamingock.core.engine.audit.writer.AuditEntry;

public enum ChangeState {
  EXECUTED, FAILED, ROLLED_BACK, ROLLBACK_FAILED, IGNORED;

  public AuditEntry.Status toAuditStatus() {
    switch (this) {
      case FAILED: return AuditEntry.Status.EXECUTION_FAILED;
      case ROLLED_BACK: return AuditEntry.Status.ROLLED_BACK;
      case ROLLBACK_FAILED: return AuditEntry.Status.ROLLBACK_FAILED;
      default: return AuditEntry.Status.EXECUTED;
    }
  }

}
