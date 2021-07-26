package com.capitalone.dashboard.request;

import com.capitalone.dashboard.model.AuditType;
import com.capitalone.dashboard.model.AutoDiscoverAuditType;
import io.swagger.annotations.ApiModelProperty;

import java.util.Set;

public class DashboardAuditRequest extends AuditReviewRequest {
    @ApiModelProperty(value = "Title", example = "coderepofix")
	private String title;
    private String businessService;
    private String businessApplication;
    @ApiModelProperty(value = "Audit Type has one of these values: ALL, CODE_REVIEW, BUILD_REVIEW, CODE_QUALITY, TEST_RESULT, PERF_TEST,ARTIFACT", example = "ALL")
	private Set<AuditType> auditType;
    @ApiModelProperty(value = "Auto discover audit type has one of these values: ALL, CODE_REVIEW, BUILD_REVIEW, CODE_QUALITY, TEST_RESULT, PERF_TEST,ARTIFACT", example = "ALL")
    private AutoDiscoverAuditType autoDiscoverAuditType;
    private ArtifactAuditRequest artifactAuditRequest;
    private String altIdentifier;
    private String identifierName;
    private String identifierVersion;
    private String identifierUrl;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBusinessService() {
        return businessService;
    }

    public void setBusinessService(String businessService) {
        this.businessService = businessService;
    }

    public String getBusinessApplication() {
        return businessApplication;
    }

    public void setBusinessApplication(String businessApplication) {
        this.businessApplication = businessApplication;
    }

    public Set<AuditType> getAuditType() {
        return auditType;
    }

    public void setAuditType(Set<AuditType> auditType) {
        this.auditType = auditType;
    }

    public ArtifactAuditRequest getArtifactAuditRequest() {
        return artifactAuditRequest;
    }

    public void setArtifactAuditRequest(ArtifactAuditRequest artifactAuditRequest) {
        this.artifactAuditRequest = artifactAuditRequest;
    }

    public AutoDiscoverAuditType getAutoDiscoverAuditType() {
        return autoDiscoverAuditType;
    }

    public void setAutoDiscoverAuditType(AutoDiscoverAuditType autoDiscoverAuditType) {
        this.autoDiscoverAuditType = autoDiscoverAuditType;
    }

    public String getAltIdentifier() {
        return altIdentifier;
    }

    public void setAltIdentifier(String altIdentifier) {
        this.altIdentifier = altIdentifier;
    }

    public String getIdentifierName() {
        return identifierName;
    }

    public void setIdentifierName(String identifierName) {
        this.identifierName = identifierName;
    }

    public String getIdentifierVersion() {
        return identifierVersion;
    }

    public void setIdentifierVersion(String identifierVersion) {
        this.identifierVersion = identifierVersion;
    }

    public String getIdentifierUrl() {
        return identifierUrl;
    }

    public void setIdentifierUrl(String identifierUrl) {
        this.identifierUrl = identifierUrl;
    }
}
