package io.flamingock.cloud.api.response;

import java.time.Instant;

public class ExecutionResponse {

    private String executionId;
    private long environmentId;
    private long serviceId;
    private String runnerId;
    private Instant startedAt;

    public ExecutionResponse() {
    }

    public ExecutionResponse(String executionId, long environmentId, long serviceId, String runnerId, Instant startedAt) {
        this.executionId = executionId;
        this.environmentId = environmentId;
        this.serviceId = serviceId;
        this.runnerId = runnerId;
        this.startedAt = startedAt;
    }

    public String getExecutionId() { return executionId; }
    public void setExecutionId(String executionId) { this.executionId = executionId; }

    public long getEnvironmentId() { return environmentId; }
    public void setEnvironmentId(long environmentId) { this.environmentId = environmentId; }

    public long getServiceId() { return serviceId; }
    public void setServiceId(long serviceId) { this.serviceId = serviceId; }

    public String getRunnerId() { return runnerId; }
    public void setRunnerId(String runnerId) { this.runnerId = runnerId; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExecutionResponse that = (ExecutionResponse) o;
        return environmentId == that.environmentId && serviceId == that.serviceId
                && java.util.Objects.equals(executionId, that.executionId)
                && java.util.Objects.equals(runnerId, that.runnerId)
                && java.util.Objects.equals(startedAt, that.startedAt);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(executionId, environmentId, serviceId, runnerId, startedAt);
    }
}
