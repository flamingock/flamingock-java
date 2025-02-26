package io.flamingock.core.engine.audit.importer.model;

import java.util.Date;

//TODO rename to MongockLegacyAuditEntry
public class ChangeEntry {

    protected String executionId;
    protected String changeId;
    protected String author;
    protected Date timestamp;
    protected ChangeState state;
    protected ChangeType type;
    protected String changeLogClass;
    protected String changeSetMethod;
    protected Object metadata;
    protected long executionMillis;
    protected String executionHostname;
    protected String errorTrace;
    protected Boolean systemChange;
    protected Date originalTimestamp;

//    public ChangeEntry() {
//    }

    public ChangeEntry(String executionId,
                       String changeId,
                       String author,
                       Date timestamp,
                       ChangeState state,
                       ChangeType type,
                       String changeLogClass,
                       String changeSetMethod,
                       Object metadata,
                       long executionMillis,
                       String executionHostname,
                       String errorTrace,
                       Boolean systemChange,
                       Date originalTimestamp) {
        this.executionId = executionId;
        this.changeId = changeId;
        this.author = author;
        this.timestamp = timestamp;
        this.state = state;
        this.type = type;
        this.changeLogClass = changeLogClass;
        this.changeSetMethod = changeSetMethod;
        this.metadata = metadata;
        this.executionMillis = executionMillis;
        this.executionHostname = executionHostname;
        this.errorTrace = errorTrace;
        this.systemChange = systemChange;
        this.originalTimestamp = originalTimestamp;
    }

    public String getExecutionId() {
        return executionId;
    }

    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    public String getChangeId() {
        return changeId;
    }

    public void setChangeId(String changeId) {
        this.changeId = changeId;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public ChangeState getState() {
        return state;
    }

    public void setState(ChangeState state) {
        this.state = state;
    }

    public ChangeType getType() {
        return type;
    }

    public void setType(ChangeType type) {
        this.type = type;
    }

    public String getChangeLogClass() {
        return changeLogClass;
    }

    public void setChangeLogClass(String changeLogClass) {
        this.changeLogClass = changeLogClass;
    }

    public String getChangeSetMethod() {
        return changeSetMethod;
    }

    public void setChangeSetMethod(String changeSetMethod) {
        this.changeSetMethod = changeSetMethod;
    }

    public Object getMetadata() {
        return metadata;
    }

    public void setMetadata(Object metadata) {
        this.metadata = metadata;
    }

    public long getExecutionMillis() {
        return executionMillis;
    }

    public void setExecutionMillis(long executionMillis) {
        this.executionMillis = executionMillis;
    }

    public String getExecutionHostname() {
        return executionHostname;
    }

    public void setExecutionHostname(String executionHostname) {
        this.executionHostname = executionHostname;
    }

    public String getErrorTrace() {
        return errorTrace;
    }

    public void setErrorTrace(String errorTrace) {
        this.errorTrace = errorTrace;
    }

    public Boolean getSystemChange() {
        return systemChange;
    }

    public void setSystemChange(Boolean systemChange) {
        this.systemChange = systemChange;
    }

    public Date getOriginalTimestamp() {
        return originalTimestamp;
    }

    public void setOriginalTimestamp(Date originalTimestamp) {
        this.originalTimestamp = originalTimestamp;
    }
}
