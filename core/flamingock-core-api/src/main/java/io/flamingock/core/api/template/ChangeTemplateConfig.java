package io.flamingock.core.api.template;

import io.flamingock.core.api.annotations.NonLockGuarded;
import io.flamingock.core.api.annotations.NonLockGuardedType;

@NonLockGuarded(NonLockGuardedType.NONE)
public class ChangeTemplateConfig<EXECUTION, ROLLBACK> {

    protected EXECUTION execution;
    protected ROLLBACK rollback;

    public ChangeTemplateConfig() {
    }

    public ChangeTemplateConfig(EXECUTION execution, ROLLBACK rollback) {
        this.execution = execution;
        this.rollback = rollback;
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
        return "ChangeTemplateConfig{" + "execution=" + execution +
                ", rollback=" + rollback +
                '}';
    }
}
