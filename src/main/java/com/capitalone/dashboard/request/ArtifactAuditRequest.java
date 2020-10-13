package com.capitalone.dashboard.request;

import com.capitalone.dashboard.model.AuditType;
import io.swagger.annotations.ApiModelProperty;

import java.util.Set;

public class ArtifactAuditRequest {
    @ApiModelProperty(value = "artifactName", example = "go1.6.2.tar.gz")
	private  String artifactName;
    @ApiModelProperty(value = "artifactVersion", example = "1.6.2")
    private String artifactVersion;
    @ApiModelProperty(value = "artifactRepo", example = "artifactory repository name where go1.6.2.tar.gz is uploaded ")
    private String artifactRepo;
    @ApiModelProperty(value = "artifactPath", example = "golang/go/archive/go1.6.2.tar.gz")
    private String artifactPath;
    @ApiModelProperty(value = "artifactUrl", example = "artifactory instance url")
    private String artifactUrl;

    public String getArtifactName() {
        return artifactName;
    }

    public void setArtifactName(String artifactName) {
        this.artifactName = artifactName;
    }

    public String getArtifactVersion() {
        return artifactVersion;
    }

    public void setArtifactVersion(String artifactVersion) {
        this.artifactVersion = artifactVersion;
    }

    public String getArtifactRepo() {
        return artifactRepo;
    }

    public void setArtifactRepo(String artifactRepo) {
        this.artifactRepo = artifactRepo;
    }

    public String getArtifactPath() {
        return artifactPath;
    }

    public void setArtifactPath(String artifactPath) {
        this.artifactPath = artifactPath;
    }

    public String getArtifactUrl() {
        return artifactUrl;
    }

    public void setArtifactUrl(String artifactUrl) {
        this.artifactUrl = artifactUrl;
    }
}
