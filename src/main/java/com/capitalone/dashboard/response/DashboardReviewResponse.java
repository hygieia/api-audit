package com.capitalone.dashboard.response;

import com.capitalone.dashboard.model.;
import com.capitalone.dashboard.status.DashboardAuditStatus;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class DashboardReviewResponse<T extends AuditReviewResponse> extends AuditReviewResponse <DashboardAuditStatus> {
    private String dashboardTitle;
    private String businessService;
    private String businessApplication;

    private Map<, Collection<T>> review = new HashMap<>();


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

	public String getDashboardTitle() {
        return dashboardTitle;
    }

    public void setDashboardTitle(String dashboardTitle) {
        this.dashboardTitle = dashboardTitle;
    }

	public Map<, Collection<T>> getReview() {
		return review;
	}


	public void addReview( type, Collection<T> audit) {
		if (review.get(type) != null) {
			review.get(type).addAll(audit);
		} else {
			review.put(type, audit);
		}
	}

	public void setReview(Map<, Collection<T>> review) {
		this.review = review;
	}
}
