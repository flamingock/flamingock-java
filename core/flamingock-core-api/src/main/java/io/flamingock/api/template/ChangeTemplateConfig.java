package io.flamingock.api.template;

import io.flamingock.api.annotations.NonLockGuarded;
import io.flamingock.api.annotations.NonLockGuardedType;

@NonLockGuarded(NonLockGuardedType.NONE)
public class ChangeTemplateConfig<SHARED, EXECUTION, ROLLBACK> {

    protected SHARED shared;
    protected EXECUTION execution;
    protected ROLLBACK rollback;

    public ChangeTemplateConfig() {
    }

    public ChangeTemplateConfig(SHARED shared, EXECUTION execution, ROLLBACK rollback) {
        this.shared = shared;
        this.execution = execution;
        this.rollback = rollback;
    }

    public SHARED getShared() {
        return shared;
    }

    public void setShared(SHARED shared) {
        this.shared = shared;
    }

    public EXECUTION getExecution() {
        return execution;
    }

    public void setExecution(EXECUTION execution) {
        this.execution = execution;
    }

    public ROLLBACK getRollback() {
        return rollback;
    }

    public void setRollback(ROLLBACK rollback) {
        this.rollback = rollback;
    }


    @Override
    public String toString() {
        return "ChangeTemplateConfig{" + "shared=" + shared +
                ", execution=" + execution +
                ", rollback=" + rollback +
                '}';
    }
}
