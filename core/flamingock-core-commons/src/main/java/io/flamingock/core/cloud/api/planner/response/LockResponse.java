package io.flamingock.core.cloud.api.planner.response;

public class LockResponse {

    private String key;

    private String owner;

    private String acquisitionId;

    private long acquiredForMillis;


    public LockResponse() {
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getAcquisitionId() {
        return acquisitionId;
    }

    public void setAcquisitionId(String acquisitionId) {
        this.acquisitionId = acquisitionId;
    }

    public long getAcquiredForMillis() {
        return acquiredForMillis;
    }

    public void setAcquiredForMillis(long acquiredForMillis) {
        this.acquiredForMillis = acquiredForMillis;
    }
}
