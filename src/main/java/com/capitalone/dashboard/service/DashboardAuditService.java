package com.capitalone.dashboard.service;

import com.capitalone.dashboard.model.AuditException;
import com.capitalone.dashboard.model.AuditType;
import com.capitalone.dashboard.model.AutoDiscoverAuditType;
import com.capitalone.dashboard.model.CollectorItem;
import com.capitalone.dashboard.model.DashboardType;
import com.capitalone.dashboard.request.DashboardAuditRequest;
import com.capitalone.dashboard.response.DashboardReviewResponse;
import org.json.simple.JSONObject;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface DashboardAuditService {

    DashboardReviewResponse getDashboardReviewResponse(String dashboardTitle, DashboardType dashboardType, String businessService, String businessApp, long beginDate, long endDate, Set<AuditType> auditTypes, AutoDiscoverAuditType autoDiscoverAuditTypes, String altIdentifier, String identifierName, Map<?,?> data) throws AuditException;

    DashboardReviewResponse getDashboardReviewResponseNextGen(DashboardAuditRequest dashboardAuditRequest) throws AuditException;

    List<CollectorItem> getSonarProjects(String name);

    JSONObject getAuditReport(DashboardAuditRequest dashboardAuditRequest) throws AuditException;
}
