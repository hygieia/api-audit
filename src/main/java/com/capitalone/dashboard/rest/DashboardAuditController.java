package com.capitalone.dashboard.rest;

import com.capitalone.dashboard.model.AuditException;
import com.capitalone.dashboard.model.AutoDiscoverAuditType;
import com.capitalone.dashboard.model.CollectorItem;
import com.capitalone.dashboard.model.DashboardType;
import com.capitalone.dashboard.request.DashboardAuditRequest;
import com.capitalone.dashboard.response.DashboardReviewResponse;
import com.capitalone.dashboard.service.DashboardAuditService;
import com.capitalone.dashboard.util.CommonConstants;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.List;
import java.util.Objects;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

@RestController
public class DashboardAuditController {

    private final HttpServletRequest httpServletRequest;
    private final DashboardAuditService dashboardAuditService;

    private static final Logger LOGGER = LoggerFactory.getLogger(DashboardAuditController.class);

    @Autowired
    public DashboardAuditController(HttpServletRequest httpServletRequest, DashboardAuditService dashboardAuditService) {

		this.httpServletRequest = httpServletRequest;
        this.dashboardAuditService = dashboardAuditService;
	}

    /**
     * Dashboard review
     *     - Check which widgets are configured
     *     - Check whether repo and build point to same repository
     * @param request incoming request
     * @return response entity
     * @throws AuditException audit exception
     */
    @RequestMapping(value = "/dashboardReview", method = GET, produces = APPLICATION_JSON_VALUE)
	@ApiOperation(value = "Dashboard Review", notes = "This endpoint validates that your artifact is meeting the quality gate threshold established in Sonar and returns an appropriate audit status based on whether the threshold has been met or not.", response = DashboardReviewResponse.class, responseContainer = "List")
    public ResponseEntity<DashboardReviewResponse> dashboardReview(@Valid DashboardAuditRequest request) throws AuditException {
        request.setClientReference(httpServletRequest.getHeader(CommonConstants.HEADER_CLIENT_CORRELATION_ID));
        String requester = httpServletRequest.getHeader(CommonConstants.HEADER_API_USER);
        DashboardReviewResponse dashboardReviewResponse = dashboardAuditService.getDashboardReviewResponse(request.getTitle(), DashboardType.Team,
                request.getBusinessService(), request.getBusinessApplication(),
                request.getBeginDate(), request.getEndDate(), request.getAuditType(), Objects.nonNull(request.getAutoDiscoverAuditType())? request.getAutoDiscoverAuditType() : AutoDiscoverAuditType.ALL);
        String request_audit_types =  CollectionUtils.isEmpty(request.getAuditType()) ? "[ALL]" : request.getAuditType().toString();
        String response_message = "auditStatuses:"+dashboardReviewResponse.getAuditStatuses().toString();
        LOGGER.info("correlation_id="+request.getClientReference() +", application=hygieia, service=api-audit, uri=" + httpServletRequest.getRequestURI() +
                ", requester=" + requester + ", response_status=success, response_code=" + HttpStatus.OK.value()+", response_status_message="+response_message +", businessService="+request.getBusinessService()+
                ", businessApplication="+request.getBusinessApplication() + ", auditType="+request_audit_types);
        dashboardReviewResponse.setClientReference(request.getClientReference());
        return ResponseEntity.ok().body(dashboardReviewResponse);
    }

    /**
     * Dashboard review
     *     - Check which widgets are configured
     *     - Check whether repo and build point to same repository
     * @param request incoming request
     * @return response entity
     * @throws AuditException audit exception
     */
    @RequestMapping(value = "/nextgen/dashboardReview", method = POST, consumes = "application/json", produces = APPLICATION_JSON_VALUE)
    @ApiOperation(value = "NextGen Dashboard Review", notes = "This endpoint validates that your artifact is meeting the quality gate threshold established in Sonar and returns an appropriate audit status based on whether the threshold has been met or not.", response = DashboardReviewResponse.class, responseContainer = "List")
    public ResponseEntity<DashboardReviewResponse> nextGenDashboardReview(@Valid @RequestBody DashboardAuditRequest request) throws AuditException {
        DashboardReviewResponse dashboardReviewResponse = dashboardAuditService.getDashboardReviewResponseNextGen(request);
        dashboardReviewResponse.setClientReference(request.getClientReference());
        return ResponseEntity.ok().body(dashboardReviewResponse);
    }

    @RequestMapping(value = "/sonarComponent", method = GET, produces = APPLICATION_JSON_VALUE)
    @ApiOperation(value = "Sonar Component", notes = "This endpoint gets component Id for sonar project name", response = CollectorItem.class, responseContainer = "List")
    public ResponseEntity<List<CollectorItem>> sonarComponent(@Valid String projectName) throws AuditException {

        List<CollectorItem> sonarProjects = dashboardAuditService.getSonarProjects(projectName);
        return ResponseEntity
                .ok()
                .body(sonarProjects);
    }

}

