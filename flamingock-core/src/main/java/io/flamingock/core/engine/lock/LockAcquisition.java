package io.flamingock.core.engine.lock;

import io.flamingock.commons.utils.RunnerId;

public class LockAcquisition {

    private final RunnerId owner;

    private final long acquiredForMillis;

    public LockAcquisition(RunnerId owner, long acquiredForMillis) {
        this.owner = owner;
        this.acquiredForMillis = acquiredForMillis;
    }

    public long getAcquiredForMillis() {
        return acquiredForMillis;
    }

    public RunnerId getOwner() {
        return owner;
    }

    public boolean doesBelongTo(RunnerId owner) {
        return owner.equals(this.owner);
    }
}
