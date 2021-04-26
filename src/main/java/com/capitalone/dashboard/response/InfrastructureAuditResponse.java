package com.capitalone.dashboard.response;

import com.capitalone.dashboard.model.InfrastructureScan;
import com.capitalone.dashboard.status.InfrastructureAuditStatus;

import java.util.List;

public class InfrastructureAuditResponse extends AuditReviewResponse<InfrastructureAuditStatus> {

    private String url;
    private long lastExecutionTime;
    private List<InfrastructureScan> infrastructureScans;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public long getLastExecutionTime() {
        return lastExecutionTime;
    }

    public void setLastExecutionTime(long lastExecutionTime) {
        this.lastExecutionTime = lastExecutionTime;
    }

    public List<InfrastructureScan> getInfrastructureScans() {
        return infrastructureScans;
    }

    public void setInfrastructureScans(List<InfrastructureScan> infrastructureScans) {
        this.infrastructureScans = infrastructureScans;
    }
}
