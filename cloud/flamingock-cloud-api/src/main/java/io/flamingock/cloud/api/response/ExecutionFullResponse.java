package io.flamingock.cloud.api.response;

import io.flamingock.cloud.api.request.ClientSubmission;

import java.time.Instant;

public class ExecutionFullResponse {

    private String executionId;
    private long environmentId;
    private long serviceId;
    private String runnerId;
    private Instant startedAt;
    private ClientSubmission clientSubmission;
    private PipelineResponse pipeline;

    public ExecutionFullResponse() {
    }

    public ExecutionFullResponse(String executionId, long environmentId, long serviceId, String runnerId,
                                 Instant startedAt, ClientSubmission clientSubmission, PipelineResponse pipeline) {
        this.executionId = executionId;
        this.environmentId = environmentId;
        this.serviceId = serviceId;
        this.runnerId = runnerId;
        this.startedAt = startedAt;
        this.clientSubmission = clientSubmission;
        this.pipeline = pipeline;
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

    public ClientSubmission getClientSubmission() { return clientSubmission; }
    public void setClientSubmission(ClientSubmission clientSubmission) { this.clientSubmission = clientSubmission; }

    public PipelineResponse getPipeline() { return pipeline; }
    public void setPipeline(PipelineResponse pipeline) { this.pipeline = pipeline; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExecutionFullResponse that = (ExecutionFullResponse) o;
        return environmentId == that.environmentId && serviceId == that.serviceId
                && java.util.Objects.equals(executionId, that.executionId)
                && java.util.Objects.equals(runnerId, that.runnerId)
                && java.util.Objects.equals(startedAt, that.startedAt)
                && java.util.Objects.equals(clientSubmission, that.clientSubmission)
                && java.util.Objects.equals(pipeline, that.pipeline);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(executionId, environmentId, serviceId, runnerId, startedAt, clientSubmission, pipeline);
    }
}
