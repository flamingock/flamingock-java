package io.flamingock.cloud.api.request;

public class LockAcquisitionRequest {

    private long acquiredForMillis;
    private String lastAcquisitionId;
    private Long elapsedMillis;

    public LockAcquisitionRequest() {
    }

    public LockAcquisitionRequest(long acquiredForMillis, String lastAcquisitionId, Long elapsedMillis) {
        this.acquiredForMillis = acquiredForMillis;
        this.lastAcquisitionId = lastAcquisitionId;
        this.elapsedMillis = elapsedMillis;
    }

    public long getAcquiredForMillis() {
        return acquiredForMillis;
    }

    public void setAcquiredForMillis(long acquiredForMillis) {
        this.acquiredForMillis = acquiredForMillis;
    }

    public String getLastAcquisitionId() {
        return lastAcquisitionId;
    }

    public void setLastAcquisitionId(String lastAcquisitionId) {
        this.lastAcquisitionId = lastAcquisitionId;
    }

    public Long getElapsedMillis() {
        return elapsedMillis;
    }

    public void setElapsedMillis(Long elapsedMillis) {
        this.elapsedMillis = elapsedMillis;
    }
}
