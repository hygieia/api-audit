package com.capitalone.dashboard.service;

import com.capitalone.dashboard.ApiSettings;
import com.capitalone.dashboard.evaluator.Evaluator;
import com.capitalone.dashboard.model.AuditException;
import com.capitalone.dashboard.model.AuditType;
import com.capitalone.dashboard.model.Cmdb;
import com.capitalone.dashboard.model.CollectorItem;
import com.capitalone.dashboard.model.Dashboard;
import com.capitalone.dashboard.model.DashboardAuditModel;
import com.capitalone.dashboard.model.DashboardType;
import com.capitalone.dashboard.repository.CmdbRepository;
import com.capitalone.dashboard.repository.CollectorItemRepository;
import com.capitalone.dashboard.repository.DashboardRepository;
import com.capitalone.dashboard.request.ArtifactAuditRequest;
import com.capitalone.dashboard.request.DashboardAuditRequest;
import com.capitalone.dashboard.response.AuditReviewResponse;
import com.capitalone.dashboard.response.DashboardReviewResponse;
import com.capitalone.dashboard.status.DashboardAuditStatus;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;


@Component
public class DashboardAuditServiceImpl implements DashboardAuditService {


    private final DashboardRepository dashboardRepository;
    private final CmdbRepository cmdbRepository;
    private final DashboardAuditModel auditModel;
    private final ApiSettings apiSettings;
    private final CollectorItemRepository collectorItemRepository;


//    private static final Log LOGGER = LogFactory.getLog(DashboardAuditServiceImpl.class);

    @Autowired
    public DashboardAuditServiceImpl(DashboardRepository dashboardRepository, CmdbRepository cmdbRepository, DashboardAuditModel auditModel,
                                     CollectorItemRepository collectorItemRepository,ApiSettings apiSettings) {

        this.dashboardRepository = dashboardRepository;
        this.cmdbRepository = cmdbRepository;
        this.auditModel = auditModel;
        this.apiSettings = apiSettings;
        this.collectorItemRepository = collectorItemRepository;
    }

    /**
     * Calculates audit response for a given dashboard
     *
     * @param dashboardTitle
     * @param dashboardType
     * @param businessService
     * @param businessApp
     * @param beginDate
     * @param endDate
     * @param auditTypes
     * @return @DashboardReviewResponse for a given dashboard
     * @throws AuditException
     */
    @SuppressWarnings("PMD.NPathComplexity")
    @Override
    public DashboardReviewResponse getDashboardReviewResponse(String dashboardTitle, DashboardType dashboardType, String businessService, String businessApp, long beginDate, long endDate, Set<AuditType> auditTypes) throws AuditException {

        validateParameters(dashboardTitle,dashboardType, businessService, businessApp, beginDate, endDate);

        DashboardReviewResponse dashboardReviewResponse = new DashboardReviewResponse();

        Dashboard dashboard = getDashboard(dashboardTitle, dashboardType, businessService, businessApp);
        if (dashboard == null) {
            dashboardReviewResponse.addAuditStatus(DashboardAuditStatus.DASHBOARD_NOT_REGISTERED);
            return dashboardReviewResponse;
        }

        dashboardReviewResponse.setDashboardTitle(dashboard.getTitle());
        dashboardReviewResponse.setBusinessApplication(StringUtils.isEmpty(businessApp) ? dashboard.getConfigurationItemBusAppName() : businessApp);
        dashboardReviewResponse.setBusinessService(StringUtils.isEmpty(businessService) ? dashboard.getConfigurationItemBusServName() : businessService);
        dashboardReviewResponse.setAuditEntity(null);

        if (auditTypes.contains(AuditType.ALL)) {
            auditTypes.addAll(Sets.newHashSet(AuditType.values()));
            auditTypes.remove(AuditType.ALL);
        }

        auditTypes.forEach(auditType -> {
            Evaluator evaluator = auditModel.evaluatorMap().get(auditType);
            try {
                Collection<AuditReviewResponse> auditResponse = evaluator.evaluate(dashboard, beginDate, endDate, null);
                dashboardReviewResponse.addReview(auditType, auditResponse);
                dashboardReviewResponse.addAuditStatus(auditModel.successStatusMap().get(auditType));
            } catch (AuditException e) {
                if (e.getErrorCode() == AuditException.NO_COLLECTOR_ITEM_CONFIGURED) {
                    dashboardReviewResponse.addAuditStatus(auditModel.errorStatusMap().get(auditType));
                }
            }
        });
        return dashboardReviewResponse;
    }

    @Override
    public DashboardReviewResponse getDashboardReviewResponseNextGen(DashboardAuditRequest dashboardAuditRequest) throws AuditException {
        String businessService = dashboardAuditRequest.getBusinessService();
        String businessApp = dashboardAuditRequest.getBusinessApplication();
        Set<AuditType> auditTypes = dashboardAuditRequest.getAuditType();
        long beginDate = dashboardAuditRequest.getBeginDate();
        long endDate = dashboardAuditRequest.getEndDate();
        validateParameters(dashboardAuditRequest.getTitle(),DashboardType.Team, businessService, businessApp, dashboardAuditRequest.getBeginDate(), dashboardAuditRequest.getEndDate());
        validateArtifactParameters(dashboardAuditRequest.getArtifactAuditRequest());
        // Get all artifacts configured to dashboard
        DashboardReviewResponse dashboardReviewResponse = new DashboardReviewResponse();
        Dashboard dashboard = getDashboard(dashboardAuditRequest.getTitle(), DashboardType.Team, businessService, businessApp);
        if (dashboard == null) {
            dashboardReviewResponse.addAuditStatus(DashboardAuditStatus.DASHBOARD_NOT_REGISTERED);
            return dashboardReviewResponse;
        }
        dashboardReviewResponse.setDashboardTitle(dashboard.getTitle());
        dashboardReviewResponse.setBusinessApplication(StringUtils.isEmpty(businessApp) ? dashboard.getConfigurationItemBusAppName() : businessApp);
        dashboardReviewResponse.setBusinessService(StringUtils.isEmpty(businessService) ? dashboard.getConfigurationItemBusServName() : businessService);
        dashboardReviewResponse.setAuditEntity(null);

        if (auditTypes.contains(AuditType.ALL)) {
            auditTypes.addAll(Sets.newHashSet(AuditType.ARTIFACT));
            auditTypes.remove(AuditType.ALL);
        }

        auditTypes.forEach(auditType -> {
            Evaluator evaluator = auditModel.evaluatorMap().get(auditType);
            try {
                Collection<AuditReviewResponse> auditResponse = evaluator.evaluateNextGen(dashboardAuditRequest.getArtifactAuditRequest(), dashboard, beginDate, endDate, null);
                dashboardReviewResponse.addReview(auditType, auditResponse);
                dashboardReviewResponse.addAuditStatus(auditModel.successStatusMap().get(auditType));
            } catch (AuditException e) {
                if (e.getErrorCode() == AuditException.NO_COLLECTOR_ITEM_CONFIGURED) {
                    dashboardReviewResponse.addAuditStatus(auditModel.errorStatusMap().get(auditType));
                }
            }
        });
        return dashboardReviewResponse;

    }

    @Override
    public List<CollectorItem> getSonarProjects(String description) {

        return collectorItemRepository.findByDescription(description);
    }

    private void validateParameters(String dashboardTitle, DashboardType dashboardType, String businessService, String businessApp, long beginDate, long endDate) throws AuditException{

        if (beginDate <= 0 || endDate <=0 || (beginDate >= endDate)) {
            throw new AuditException("Invalid date range", AuditException.BAD_INPUT_DATA);
        }

        if ((endDate - beginDate) > 24*60*60*1000*apiSettings.getMaxDaysRangeForQuery()) {
            throw new AuditException("Invalid date range. Maximum " + apiSettings.getMaxDaysRangeForQuery() + " days of data allowed.", AuditException.BAD_INPUT_DATA);
        }
        boolean byTitle = !StringUtils.isEmpty(dashboardTitle) && (dashboardType != null);
        boolean byBusiness = !StringUtils.isEmpty(businessService) && !StringUtils.isEmpty(businessApp);

        if (!byTitle && !byBusiness) {
            throw new AuditException("Invalid parameters. Valid query must have a title OR non-null business service and non-null business application", AuditException.BAD_INPUT_DATA);
        }
    }

    private void validateArtifactParameters(ArtifactAuditRequest artifactAuditRequest) throws AuditException{
        if (Objects.isNull(artifactAuditRequest)) {
            throw new AuditException("Artifact information is null ", AuditException.BAD_INPUT_DATA);
        }
        boolean inValid  = Stream.of(artifactAuditRequest.getArtifactName(),artifactAuditRequest.getArtifactPath(),artifactAuditRequest.getArtifactRepo(),artifactAuditRequest.getArtifactVersion(),artifactAuditRequest.getArtifactUrl()).anyMatch(Objects::isNull);
        if (inValid) {
            throw new AuditException("One/many of the values artifact name,artifact path, artifact repo, artifact version, artifact url is/are null", AuditException.BAD_INPUT_DATA);
        }
    }



    /**
     * Finds the dashboard
     *
     * @param title
     * @param type
     * @param busServ
     * @param busApp
     * @return the @Dashboard for a given title, type, business service and app
     * @throws AuditException
     */
    private Dashboard getDashboard(String title, DashboardType type, String busServ, String busApp) throws
            AuditException {
        if (!StringUtils.isEmpty(title) && (type != null)) {
            return dashboardRepository.findByTitleAndType(title, type);

        } else if (!StringUtils.isEmpty(busServ) && !StringUtils.isEmpty(busApp)) {
            Cmdb busServItem = cmdbRepository.findByConfigurationItemAndItemType(busServ, "app");
            if (busServItem == null)
                throw new AuditException("Invalid Business Service Name.", AuditException.BAD_INPUT_DATA);
            Cmdb busAppItem = cmdbRepository.findByConfigurationItemAndItemType(busApp, "component");
            if (busAppItem == null)
                throw new AuditException("Invalid Business Application Name.", AuditException.BAD_INPUT_DATA);

            return dashboardRepository.findByConfigurationItemBusServNameIgnoreCaseAndConfigurationItemBusAppNameIgnoreCase(busServItem.getConfigurationItem(), busAppItem.getConfigurationItem());
        }
        return null;
    }
}
