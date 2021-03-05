package com.capitalone.dashboard.evaluator;

import com.capitalone.dashboard.model.AuditException;
import com.capitalone.dashboard.model.AutoDiscoverAuditType;
import com.capitalone.dashboard.model.AutoDiscoveredEntry;
import com.capitalone.dashboard.model.AutoDiscovery;
import com.capitalone.dashboard.model.AutoDiscoveryStatusType;
import com.capitalone.dashboard.model.CollectorItem;
import com.capitalone.dashboard.model.Dashboard;
import com.capitalone.dashboard.repository.AutoDiscoveryRepository;
import com.capitalone.dashboard.request.ArtifactAuditRequest;
import com.capitalone.dashboard.response.AutoDiscoverAuditResponse;
import com.capitalone.dashboard.status.AutoDiscoverAuditStatus;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class AutoDiscoverEvaluator extends Evaluator<AutoDiscoverAuditResponse> {

    private final AutoDiscoveryRepository autoDiscoveryRepository;

    @Autowired
    public AutoDiscoverEvaluator(AutoDiscoveryRepository autoDiscoveryRepository) {
        this.autoDiscoveryRepository = autoDiscoveryRepository;
    }

    @Override
    public Collection<AutoDiscoverAuditResponse> evaluate(Dashboard dashboard, long beginDate, long endDate, Map<?, ?> data) throws AuditException {
        String title = dashboard.getTitle();
        List<AutoDiscovery> autoDiscoveries = autoDiscoveryRepository.findByMetaDataTitle(title);
        if (CollectionUtils.isEmpty(autoDiscoveries)) {
            return Stream.of(getErrorResponse(AutoDiscoverAuditStatus.AUTO_DISCOVER_EVIDENCES_NOT_FOUND)).collect(Collectors.toList());
        }
        return Stream.of(getAutoDiscoverAuditResponse(autoDiscoveries)).collect(Collectors.toList());
    }

    @Override
    public Collection<AutoDiscoverAuditResponse> evaluateNextGen(ArtifactAuditRequest artifactAuditRequest, Dashboard dashboard, long beginDate, long endDate, Map<?, ?> data) throws AuditException {
        String title = dashboard.getTitle();
        List<AutoDiscovery> autoDiscoveries = autoDiscoveryRepository.findByMetaDataTitle(title);
        if (CollectionUtils.isEmpty(autoDiscoveries)) {
            return Stream.of(getErrorResponse(AutoDiscoverAuditStatus.AUTO_DISCOVER_EVIDENCES_NOT_FOUND)).collect(Collectors.toList());
        }
        return Stream.of(getAutoDiscoverAuditResponse(autoDiscoveries)).collect(Collectors.toList());
    }

    @Override
    public AutoDiscoverAuditResponse evaluate(CollectorItem collectorItem, long beginDate, long endDate, Map<?, ?> data) {
        return null;
    }

    private AutoDiscoverAuditResponse getAutoDiscoverAuditResponse(List<AutoDiscovery> autoDiscoveries) {
        AutoDiscoverAuditResponse autoDiscoverAuditResponse = new AutoDiscoverAuditResponse();
        autoDiscoveries.forEach(autoDiscovery -> {
                entryExists(autoDiscoverAuditResponse, autoDiscovery.getDeploymentEntries(),AutoDiscoverAuditStatus.AUTO_DISCOVER_DEPLOY_EVIDENCES_FOUND);
                entryExists(autoDiscoverAuditResponse, autoDiscovery.getArtifactEntries(),AutoDiscoverAuditStatus.AUTO_DISCOVER_ARTIFACT_EVIDENCES_FOUND);
                entryExists(autoDiscoverAuditResponse, autoDiscovery.getPerformanceTestEntries(),AutoDiscoverAuditStatus.AUTO_DISCOVER_PERF_TEST_EVIDENCES_FOUND);
                entryExists(autoDiscoverAuditResponse, autoDiscovery.getCodeRepoEntries(),AutoDiscoverAuditStatus.AUTO_DISCOVER_CODE_REPO_URLS_FOUND);
                entryExists(autoDiscoverAuditResponse, autoDiscovery.getFunctionalTestEntries(),AutoDiscoverAuditStatus.AUTO_DISCOVER_FUNCTIONAL_TEST_EVIDENCES_FOUND);
                entryExists(autoDiscoverAuditResponse, autoDiscovery.getFeatureEntries(),AutoDiscoverAuditStatus.AUTO_DISCOVER_FEATURE_EVIDENCES_FOUND);
                entryExists(autoDiscoverAuditResponse, autoDiscovery.getStaticCodeEntries(),AutoDiscoverAuditStatus.AUTO_DISCOVER_STATIC_CODE_EVIDENCES_FOUND);
                entryExists(autoDiscoverAuditResponse, autoDiscovery.getSecurityScanEntries(),AutoDiscoverAuditStatus.AUTO_DISCOVER_SECURITY_SCAN_EVIDENCES_FOUND);
                entryExists(autoDiscoverAuditResponse, autoDiscovery.getLibraryScanEntries(),AutoDiscoverAuditStatus.AUTO_DISCOVER_LIBRARY_POLICY_EVIDENCES_FOUND);
                entryExists(autoDiscoverAuditResponse, autoDiscovery.getBuildEntries(),AutoDiscoverAuditStatus.AUTO_DISCOVER_BUILD_EVIDENCES_FOUND);

        });
        autoDiscoverAuditResponse.setAutoDiscoveries(autoDiscoveries);
        return autoDiscoverAuditResponse;
    }

    private void entryExists(AutoDiscoverAuditResponse autoDiscoverAuditResponse, List<AutoDiscoveredEntry> autoDiscoveredEntries, AutoDiscoverAuditStatus autoDiscoverAuditStatus) {
        if (CollectionUtils.isNotEmpty(autoDiscoveredEntries)) {
            autoDiscoveredEntries.forEach(autoDiscoveredEntry -> {
                if (autoDiscoveredEntry.getStatus() == AutoDiscoveryStatusType.AWAITING_USER_RESPONSE) {
                    autoDiscoverAuditResponse.addAuditStatus(autoDiscoverAuditStatus);
                }
            });
        }
    }


    private AutoDiscoverAuditResponse getErrorResponse(AutoDiscoverAuditStatus auditStatus) {
        AutoDiscoverAuditResponse autoDiscoverAuditResponse = new AutoDiscoverAuditResponse();
        autoDiscoverAuditResponse.addAuditStatus(auditStatus);
        autoDiscoverAuditResponse.setErrorMessage("No evidences discovered ");
        return autoDiscoverAuditResponse;
    }


}
