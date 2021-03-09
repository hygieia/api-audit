package com.capitalone.dashboard.response;

import com.capitalone.dashboard.model.AutoDiscovery;
import com.capitalone.dashboard.status.AutoDiscoverAuditStatus;

import java.util.List;

public class AutoDiscoverAuditResponse extends AuditReviewResponse<AutoDiscoverAuditStatus> {
    private List<AutoDiscovery> autoDiscoveries;

    public List<AutoDiscovery> getAutoDiscoveries() {
        return autoDiscoveries;
    }

    public void setAutoDiscoveries(List<AutoDiscovery> autoDiscoveries) {
        this.autoDiscoveries = autoDiscoveries;
    }
}
