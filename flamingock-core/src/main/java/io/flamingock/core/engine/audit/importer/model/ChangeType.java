package io.flamingock.core.engine.audit.importer.model;

import io.flamingock.core.engine.audit.writer.AuditEntry;

public enum ChangeType {
  EXECUTION, BEFORE_EXECUTION;

  public AuditEntry.ExecutionType toAuditType() {
    if (this == ChangeType.BEFORE_EXECUTION) {
      return AuditEntry.ExecutionType.BEFORE_EXECUTION;
    }
    return AuditEntry.ExecutionType.EXECUTION;
  }
}
