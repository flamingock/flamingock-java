package io.flamingock.cloud.api.response;

public class TokenExchangeResponse {

    private String jwt;

    private long organisationId;
    private String organisationName;

    private long projectId;
    private String projectName;

    private long environmentId;
    private String environmentName;

    private long serviceId;
    private String serviceName;

    private long credentialId;

    public TokenExchangeResponse() {
    }

    public TokenExchangeResponse(long organisationId, String organisationName, long projectId, String projectName,
                                 long environmentId, String environmentName, long serviceId, String serviceName,
                                 long credentialId, String jwt) {
        this.organisationId = organisationId;
        this.organisationName = organisationName;
        this.projectId = projectId;
        this.projectName = projectName;
        this.environmentId = environmentId;
        this.environmentName = environmentName;
        this.serviceId = serviceId;
        this.serviceName = serviceName;
        this.credentialId = credentialId;
        this.jwt = jwt;
    }

    public long getOrganisationId() { return organisationId; }
    public void setOrganisationId(long organisationId) { this.organisationId = organisationId; }

    public String getOrganisationName() { return organisationName; }
    public void setOrganisationName(String organisationName) { this.organisationName = organisationName; }

    public long getProjectId() { return projectId; }
    public void setProjectId(long projectId) { this.projectId = projectId; }

    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }

    public long getEnvironmentId() { return environmentId; }
    public void setEnvironmentId(long environmentId) { this.environmentId = environmentId; }

    public String getEnvironmentName() { return environmentName; }
    public void setEnvironmentName(String environmentName) { this.environmentName = environmentName; }

    public long getServiceId() { return serviceId; }
    public void setServiceId(long serviceId) { this.serviceId = serviceId; }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public long getCredentialId() { return credentialId; }
    public void setCredentialId(long credentialId) { this.credentialId = credentialId; }

    public String getJwt() { return jwt; }
    public void setJwt(String jwt) { this.jwt = jwt; }
}
