package com.capitalone.dashboard.service;

import com.capitalone.dashboard.ApiSettings;
import com.capitalone.dashboard.evaluator.Evaluator;
import com.capitalone.dashboard.logging.LoggingFilter;
import com.capitalone.dashboard.model.AuditException;
import com.capitalone.dashboard.model.AuditType;
import com.capitalone.dashboard.model.AutoDiscoverAuditType;
import com.capitalone.dashboard.model.AutoDiscovery;
import com.capitalone.dashboard.model.Cmdb;
import com.capitalone.dashboard.model.CollectorItem;
import com.capitalone.dashboard.model.Dashboard;
import com.capitalone.dashboard.model.DashboardAuditModel;
import com.capitalone.dashboard.model.DashboardType;
import com.capitalone.dashboard.model.AuditReport;
import com.capitalone.dashboard.repository.CmdbRepository;
import com.capitalone.dashboard.repository.CollectorItemRepository;
import com.capitalone.dashboard.repository.DashboardRepository;
import com.capitalone.dashboard.repository.AuditReportRepository;
import com.capitalone.dashboard.request.ArtifactAuditRequest;
import com.capitalone.dashboard.request.DashboardAuditRequest;
import com.capitalone.dashboard.response.AuditReviewResponse;
import com.capitalone.dashboard.response.AutoDiscoverAuditResponse;
import com.capitalone.dashboard.response.DashboardReviewResponse;
import com.capitalone.dashboard.status.AutoDiscoverAuditStatus;
import com.capitalone.dashboard.status.DashboardAuditStatus;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections4.IterableUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Component
public class DashboardAuditServiceImpl implements DashboardAuditService {


    private final DashboardRepository dashboardRepository;
    private final CmdbRepository cmdbRepository;
    private final DashboardAuditModel auditModel;
    private final ApiSettings apiSettings;
    private final CollectorItemRepository collectorItemRepository;
    private final AuditReportRepository auditReportRepository;

    private static final Logger LOGGER = LoggerFactory.getLogger(DashboardAuditServiceImpl.class);

    @Autowired
    public DashboardAuditServiceImpl(DashboardRepository dashboardRepository, CmdbRepository cmdbRepository, DashboardAuditModel auditModel,
                                     CollectorItemRepository collectorItemRepository, AuditReportRepository auditReportRepository, ApiSettings apiSettings) {

        this.dashboardRepository = dashboardRepository;
        this.cmdbRepository = cmdbRepository;
        this.auditModel = auditModel;
        this.apiSettings = apiSettings;
        this.collectorItemRepository = collectorItemRepository;
        this.auditReportRepository = auditReportRepository;
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
    public DashboardReviewResponse getDashboardReviewResponse(String dashboardTitle, DashboardType dashboardType, String businessService, String businessApp, long beginDate, long endDate ,Set<AuditType> auditTypes, AutoDiscoverAuditType autoDiscoverAuditType, String altIdentifier, String identifierName, Map<?,?> data) throws AuditException {

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
                Map<String, String> evaluatorData = new HashMap<>();
                if(auditType == AuditType.FEATURE_TEST){
                    evaluatorData.put("identifierVersion", (String)data.get("identifierVersion"));
                    evaluatorData.put("featureTestThreshold",(String)data.get("featureTestThreshold"));
                }
                Collection<AuditReviewResponse> auditResponse = evaluator.evaluate(dashboard, beginDate, endDate, evaluatorData, altIdentifier, identifierName);
                if(auditType == AuditType.AUTO_DISCOVER){
                    setAutoDiscoverAuditResponse(autoDiscoverAuditType, dashboardReviewResponse, auditType, auditResponse);
                }else{
                    dashboardReviewResponse.addReview(auditType, auditResponse);
                    dashboardReviewResponse.addAuditStatus(auditModel.successStatusMap().get(auditType));
                }

            } catch (AuditException e) {
                if (e.getErrorCode() == AuditException.NO_COLLECTOR_ITEM_CONFIGURED) {
                    dashboardReviewResponse.addAuditStatus(auditModel.errorStatusMap().get(auditType));
                }
            }
        });
        return dashboardReviewResponse;
    }

    private void setAutoDiscoverAuditResponse(AutoDiscoverAuditType autoDiscoverAuditType, DashboardReviewResponse dashboardReviewResponse, AuditType auditType, Collection<AuditReviewResponse> auditResponse) {
        AutoDiscoverAuditResponse autoDiscoverAuditResponse = (AutoDiscoverAuditResponse) Iterables.get(auditResponse,0);
        AutoDiscoverAuditResponse modified = new AutoDiscoverAuditResponse();
        if(autoDiscoverAuditResponse.getAuditStatuses().contains(AutoDiscoverAuditStatus.AUTO_DISCOVER_EVIDENCES_NOT_FOUND)){
            dashboardReviewResponse.addReview(auditType,  Stream.of(autoDiscoverAuditResponse).collect(Collectors.toList()));
            dashboardReviewResponse.addAuditStatus(auditModel.successStatusMap().get(auditType));
        }else{
            AutoDiscovery autoDiscovery = autoDiscoverAuditResponse.getAutoDiscoveries().get(0);
            AutoDiscovery updated = new AutoDiscovery();
            updated.setMetaData(autoDiscovery.getMetaData());
            updated.setModifiedTimestamp(autoDiscovery.getModifiedTimestamp());
            setCodeRepoEntries(autoDiscoverAuditType, autoDiscovery, updated, modified);
            setBuildEntries(autoDiscoverAuditType, autoDiscovery, updated, modified);
            setSecurityScanEntries(autoDiscoverAuditType, autoDiscovery, updated, modified);
            setDeployEntries(autoDiscoverAuditType, autoDiscovery, updated, modified);
            setLibraryPolicyEntries(autoDiscoverAuditType, autoDiscovery, updated, modified);
            setTestEntries(autoDiscoverAuditType, autoDiscovery, updated, modified);
            setArtifactEntries(autoDiscoverAuditType, autoDiscovery, updated, modified);
            setCodeQualityEntries(autoDiscoverAuditType, autoDiscovery, updated, modified);
            setPerfTestEntries(autoDiscoverAuditType, autoDiscovery, updated, modified);
            modified.setAutoDiscoveries(Stream.of(updated).collect(Collectors.toList()));
            dashboardReviewResponse.addReview(auditType,  Stream.of(modified).collect(Collectors.toList()));
            dashboardReviewResponse.addAuditStatus(auditModel.successStatusMap().get(auditType));
        }

    }

    private void setPerfTestEntries(AutoDiscoverAuditType autoDiscoverAuditType, AutoDiscovery autoDiscovery, AutoDiscovery updated, AutoDiscoverAuditResponse modified) {
        if(AutoDiscoverAuditType.PERF_TEST.equals(autoDiscoverAuditType) || AutoDiscoverAuditType.ALL.equals(autoDiscoverAuditType)){
            if(CollectionUtils.isNotEmpty(autoDiscovery.getPerformanceTestEntries())){
                updated.setPerformanceTestEntries(autoDiscovery.getPerformanceTestEntries());
                modified.addAuditStatus(AutoDiscoverAuditStatus.AUTO_DISCOVER_PERF_TEST_EVIDENCES_FOUND);
            }
        }
    }

    private void setCodeQualityEntries(AutoDiscoverAuditType autoDiscoverAuditType, AutoDiscovery autoDiscovery, AutoDiscovery updated, AutoDiscoverAuditResponse modified) {
        if(AutoDiscoverAuditType.CODE_QUALITY.equals(autoDiscoverAuditType) || AutoDiscoverAuditType.ALL.equals(autoDiscoverAuditType)){
            if(CollectionUtils.isNotEmpty(autoDiscovery.getStaticCodeEntries())) {
                updated.setStaticCodeEntries(autoDiscovery.getStaticCodeEntries());
                modified.addAuditStatus(AutoDiscoverAuditStatus.AUTO_DISCOVER_STATIC_CODE_EVIDENCES_FOUND);
            }
        }
    }

    private void setArtifactEntries(AutoDiscoverAuditType autoDiscoverAuditType, AutoDiscovery autoDiscovery, AutoDiscovery updated, AutoDiscoverAuditResponse modified) {
        if(AutoDiscoverAuditType.ARTIFACT.equals(autoDiscoverAuditType) || AutoDiscoverAuditType.ALL.equals(autoDiscoverAuditType)){
            if(CollectionUtils.isNotEmpty(autoDiscovery.getArtifactEntries())) {
                updated.setArtifactEntries(autoDiscovery.getArtifactEntries());
                modified.addAuditStatus(AutoDiscoverAuditStatus.AUTO_DISCOVER_ARTIFACT_EVIDENCES_FOUND);
            }
        }
    }

    private void setTestEntries(AutoDiscoverAuditType autoDiscoverAuditType, AutoDiscovery autoDiscovery, AutoDiscovery updated, AutoDiscoverAuditResponse modified) {
        if(AutoDiscoverAuditType.TEST_RESULT.equals(autoDiscoverAuditType) || AutoDiscoverAuditType.ALL.equals(autoDiscoverAuditType)){
            if(CollectionUtils.isNotEmpty(autoDiscovery.getFunctionalTestEntries())){
                updated.setFunctionalTestEntries(autoDiscovery.getFunctionalTestEntries());
                modified.addAuditStatus(AutoDiscoverAuditStatus.AUTO_DISCOVER_FUNCTIONAL_TEST_EVIDENCES_FOUND);
            }
           if(CollectionUtils.isNotEmpty(autoDiscovery.getFeatureEntries())){
               updated.setFeatureEntries(autoDiscovery.getFeatureEntries());
               modified.addAuditStatus(AutoDiscoverAuditStatus.AUTO_DISCOVER_FEATURE_EVIDENCES_FOUND);
           }
        }
    }

    private void setLibraryPolicyEntries(AutoDiscoverAuditType autoDiscoverAuditType, AutoDiscovery autoDiscovery, AutoDiscovery updated, AutoDiscoverAuditResponse modified) {
        if(AutoDiscoverAuditType.LIBRARY_POLICY.equals(autoDiscoverAuditType) || AutoDiscoverAuditType.ALL.equals(autoDiscoverAuditType)){
            if(CollectionUtils.isNotEmpty(autoDiscovery.getLibraryScanEntries())){
                updated.setLibraryScanEntries(autoDiscovery.getLibraryScanEntries());
                modified.addAuditStatus(AutoDiscoverAuditStatus.AUTO_DISCOVER_LIBRARY_POLICY_EVIDENCES_FOUND);
            }
        }
    }

    private void setDeployEntries(AutoDiscoverAuditType autoDiscoverAuditType, AutoDiscovery autoDiscovery, AutoDiscovery updated, AutoDiscoverAuditResponse modified) {
        if(AutoDiscoverAuditType.DEPLOY.equals(autoDiscoverAuditType) || AutoDiscoverAuditType.ALL.equals(autoDiscoverAuditType)){
            if(CollectionUtils.isNotEmpty(autoDiscovery.getDeploymentEntries())) {
                updated.setDeploymentEntries(autoDiscovery.getDeploymentEntries());
                modified.addAuditStatus(AutoDiscoverAuditStatus.AUTO_DISCOVER_DEPLOY_EVIDENCES_FOUND);
            }
        }
    }

    private void setSecurityScanEntries(AutoDiscoverAuditType autoDiscoverAuditType, AutoDiscovery autoDiscovery, AutoDiscovery updated, AutoDiscoverAuditResponse modified) {
        if(AutoDiscoverAuditType.STATIC_SECURITY_ANALYSIS.equals(autoDiscoverAuditType) || AutoDiscoverAuditType.ALL.equals(autoDiscoverAuditType)){
            if(CollectionUtils.isNotEmpty(autoDiscovery.getSecurityScanEntries())) {
                updated.setSecurityScanEntries(autoDiscovery.getSecurityScanEntries());
                modified.addAuditStatus(AutoDiscoverAuditStatus.AUTO_DISCOVER_SECURITY_SCAN_EVIDENCES_FOUND);
            }
        }
    }

    private void setBuildEntries(AutoDiscoverAuditType autoDiscoverAuditType, AutoDiscovery autoDiscovery, AutoDiscovery updated, AutoDiscoverAuditResponse modified) {
        if(AutoDiscoverAuditType.BUILD_REVIEW.equals(autoDiscoverAuditType) || AutoDiscoverAuditType.ALL.equals(autoDiscoverAuditType)){
            if(CollectionUtils.isNotEmpty(autoDiscovery.getBuildEntries())) {
                updated.setBuildEntries(autoDiscovery.getBuildEntries());
                modified.addAuditStatus(AutoDiscoverAuditStatus.AUTO_DISCOVER_BUILD_EVIDENCES_FOUND);
            }
        }
    }

    private void setCodeRepoEntries(AutoDiscoverAuditType autoDiscoverAuditType, AutoDiscovery autoDiscovery, AutoDiscovery updated, AutoDiscoverAuditResponse modified) {
        if(AutoDiscoverAuditType.CODE_REVIEW.equals(autoDiscoverAuditType) || AutoDiscoverAuditType.ALL.equals(autoDiscoverAuditType)) {
            if(CollectionUtils.isNotEmpty(autoDiscovery.getCodeRepoEntries())) {
                updated.setCodeRepoEntries(autoDiscovery.getCodeRepoEntries());
                modified.addAuditStatus(AutoDiscoverAuditStatus.AUTO_DISCOVER_CODE_REPO_URLS_FOUND);
            }
        }
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

    @Override
    public JSONObject getAuditReport(DashboardAuditRequest dashboardAuditRequest) {
        String businessService = dashboardAuditRequest.getBusinessService();
        String businessApplication = dashboardAuditRequest.getBusinessApplication();
        String identifierName = dashboardAuditRequest.getIdentifierName();
        String identifierVersion = dashboardAuditRequest.getIdentifierVersion();
        String identifierUrl = dashboardAuditRequest.getIdentifierUrl();
        AuditType auditType = Objects.isNull(dashboardAuditRequest.getAuditType()) ? null : dashboardAuditRequest.getAuditType().iterator().next();

        AuditReport auditReport = auditReportRepository.findTop1ByBusinessApplicationAndBusinessServiceAndAuditTypeAndIdentifierNameAndIdentifierVersionAndIdentifierUrlOrderByTimestampDesc(
                businessApplication, businessService, auditType, identifierName, identifierVersion, identifierUrl);
        if(ObjectUtils.isEmpty(auditReport)) {
        	auditReport = auditReportRepository.findTop1ByAuditTypeAndIdentifierNameAndIdentifierVersionAndIdentifierUrlOrderByTimestampDesc(auditType, identifierName, 
            		identifierVersion, identifierUrl);
        }
        if (Objects.nonNull(auditReport) && !ObjectUtils.isEmpty(auditReport.getAuditResponse())) {
            return (JSONObject) auditReport.getAuditResponse();
        }else{
            JSONObject response;
            if (StringUtils.isEmpty(businessService) || StringUtils.isEmpty(businessApplication)) {
                response = createLookupResponseWhenEmpty(DashboardAuditStatus.DASHBOARD_AUDIT_NO_DATA, businessApplication, businessService);
            } else {
                Cmdb busServItem = cmdbRepository.findByConfigurationItemAndItemType(businessService, "app");
                Cmdb busAppItem = cmdbRepository.findByConfigurationItemAndItemType(businessApplication, "component");
                if(Objects.isNull(busServItem)){
                    response = createLookupResponseWhenEmpty(DashboardAuditStatus.DASHBOARD_INVALID_BA, businessApplication, businessService);
                } else if(Objects.isNull(busAppItem)){
                    response= createLookupResponseWhenEmpty(DashboardAuditStatus.DASHBOARD_INVALID_COMPONENT, businessApplication, businessService);
                } else if(!componentInfoMatch(busServItem, businessApplication)){
                    response = createLookupResponseWhenEmpty(DashboardAuditStatus.DASHBOARD_COMPONENT_BA_MISMATCH, businessApplication, businessService);
                }else{
                    response = createLookupResponseWhenEmpty(DashboardAuditStatus.DASHBOARD_AUDIT_NO_DATA, businessApplication, businessService);
                }
            }
            return response;
        }
    }

    private JSONObject createLookupResponseWhenEmpty(DashboardAuditStatus dashboardAuditStatus, String businessApplication, String businessService){
        JSONObject auditResponse = new JSONObject();
        auditResponse.put("businessApplication", businessApplication);
        auditResponse.put("businessService", businessService);
        auditResponse.put("auditStatuses", Collections.singleton(dashboardAuditStatus));
        auditResponse.put("lastUpdated", 0);
        auditResponse.put("auditEntity", new JSONObject());
        auditResponse.put("review", new JSONObject());
        return auditResponse;
    }

    private boolean componentInfoMatch(Cmdb businessApplication, String businessService){
        if(businessApplication.getComponents() == null){
            return false;
        }

        if(businessApplication.getComponents().contains(businessService)){
            return true;
        }

        return false;
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

            Iterable<Dashboard> dashboards = dashboardRepository.findAllByConfigurationItemBusServNameAndConfigurationItemBusAppName(busServItem.getConfigurationItem(), busAppItem.getConfigurationItem());
            if (!IterableUtils.isEmpty(dashboards)) {
                return dashboards.iterator().next();
            }
        }
        return null;
    }
}
