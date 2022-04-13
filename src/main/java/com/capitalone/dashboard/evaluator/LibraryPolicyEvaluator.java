package com.capitalone.dashboard.evaluator;

import com.capitalone.dashboard.ApiSettings;
import com.capitalone.dashboard.model.AuditException;
import com.capitalone.dashboard.model.CollectorItem;
import com.capitalone.dashboard.model.CollectorType;
import com.capitalone.dashboard.model.Dashboard;
import com.capitalone.dashboard.model.LibraryPolicyResult;
import com.capitalone.dashboard.model.LibraryPolicyThreatDisposition;
import com.capitalone.dashboard.model.LibraryPolicyThreatLevel;
import com.capitalone.dashboard.model.LibraryPolicyType;
import com.capitalone.dashboard.repository.LibraryPolicyResultsRepository;
import com.capitalone.dashboard.request.ArtifactAuditRequest;
import com.capitalone.dashboard.response.LibraryPolicyAuditResponse;
import com.capitalone.dashboard.status.LibraryPolicyAuditStatus;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.collections.SetUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class LibraryPolicyEvaluator extends Evaluator<LibraryPolicyAuditResponse> {

    private final LibraryPolicyResultsRepository libraryPolicyResultsRepository;

    private final ApiSettings apiSettings;

    private final long DEFAULT_BEGIN_DATE = 0;

    @Autowired
    public LibraryPolicyEvaluator(LibraryPolicyResultsRepository libraryPolicyResultsRepository, ApiSettings apiSettings) {
        this.libraryPolicyResultsRepository = libraryPolicyResultsRepository;
        this.apiSettings = apiSettings;
    }

    @Override
    public Collection<LibraryPolicyAuditResponse> evaluate(Dashboard dashboard, long beginDate, long endDate, Map<?, ?> data,  String altIdentifier, String identifierName) throws AuditException {

        List<CollectorItem> libraryPolicyItems = getCollectorItemsByAltIdentifier(dashboard, CollectorType.LibraryPolicy, altIdentifier, data);
        if (CollectionUtils.isEmpty(libraryPolicyItems)) {
            throw new AuditException("No library policy project configured", AuditException.NO_COLLECTOR_ITEM_CONFIGURED);
        }

        return libraryPolicyItems.stream().map(item -> evaluate(item, beginDate, endDate, data)).collect(Collectors.toList());
    }

    @Override
    public Collection<LibraryPolicyAuditResponse> evaluateNextGen(ArtifactAuditRequest artifactAuditRequest, Dashboard dashboard, long beginDate, long endDate, Map<?, ?> data) throws AuditException {
        return null;
    }


    @Override
    public LibraryPolicyAuditResponse evaluate(CollectorItem collectorItem, long beginDate, long endDate, Map<?, ?> data) {
        boolean enforceTimestamp = false;
        if (org.apache.commons.collections4.MapUtils.isNotEmpty(data)) {
            try {
                enforceTimestamp = BooleanUtils.isTrue((Boolean) data.get("enforceTimestamp"));
            } catch (Exception e) {/* do nothing */}
        }
        return getLibraryPolicyAuditResponse(collectorItem, enforceTimestamp, beginDate, endDate);
    }

    /**
     * Reusable method for constructing the LibraryPolicyAuditResponse object
     *
     * @param collectorItem Collector item
     * @return SecurityReviewAuditResponse
     */
    private LibraryPolicyAuditResponse getLibraryPolicyAuditResponse(CollectorItem collectorItem, boolean enforceTimestamp, long beginDate, long endDate) {
        LibraryPolicyResult returnPolicyResult = enforceTimestamp ? getResultByTimestamp(collectorItem, beginDate, endDate) :
                libraryPolicyResultsRepository.findTopByCollectorItemIdOrderByEvaluationTimestampDesc(collectorItem.getId());

        LibraryPolicyAuditResponse libraryPolicyAuditResponse = new LibraryPolicyAuditResponse();
        libraryPolicyAuditResponse.setAuditEntity(collectorItem.getOptions());

        if(Objects.isNull(returnPolicyResult)){
            libraryPolicyAuditResponse.addAuditStatus(LibraryPolicyAuditStatus.LIBRARY_POLICY_INVALID_SCAN);
            libraryPolicyAuditResponse.setLibraryPolicyResult(returnPolicyResult);
            libraryPolicyAuditResponse.setLastExecutionTime(Objects.nonNull(returnPolicyResult) ? returnPolicyResult.getEvaluationTimestamp() : 0L );
            return libraryPolicyAuditResponse;
        } else {
            libraryPolicyAuditResponse.setLibraryPolicyResult(returnPolicyResult);
            libraryPolicyAuditResponse.setLastExecutionTime(returnPolicyResult.getEvaluationTimestamp());
            //threats by type
            Set<LibraryPolicyResult.Threat> securityThreats = !MapUtils.isEmpty(returnPolicyResult.getThreats()) ? returnPolicyResult.getThreats().get(LibraryPolicyType.Security) : SetUtils.EMPTY_SET;
            Set<LibraryPolicyResult.Threat> licenseThreats = !MapUtils.isEmpty(returnPolicyResult.getThreats()) ? returnPolicyResult.getThreats().get(LibraryPolicyType.License) : SetUtils.EMPTY_SET;


            boolean isOk = true;
            //License Threats
            if (CollectionUtils.isNotEmpty(licenseThreats) && licenseThreats.stream().anyMatch(threat -> Objects.equals(threat.getLevel(), LibraryPolicyThreatLevel.Critical) && hasViolations(threat,settings.getCriticalLicenseVulnerabilitiesAge()))) {
                libraryPolicyAuditResponse.addAuditStatus(LibraryPolicyAuditStatus.LIBRARY_POLICY_FOUND_CRITICAL_LICENSE);
                isOk = false;
            }

            if (CollectionUtils.isNotEmpty(licenseThreats) && licenseThreats.stream().anyMatch(threat -> Objects.equals(threat.getLevel(), LibraryPolicyThreatLevel.High) && hasViolations(threat,settings.getHighLicenseVulnerabilitiesAge()))) {
                libraryPolicyAuditResponse.addAuditStatus(LibraryPolicyAuditStatus.LIBRARY_POLICY_FOUND_HIGH_LICENSE);
                isOk = false;
            }

            //Security Threats
            if (CollectionUtils.isNotEmpty(securityThreats) && securityThreats.stream().anyMatch(threat -> Objects.equals(threat.getLevel(), LibraryPolicyThreatLevel.Critical) && hasViolations(threat,settings.getCriticalSecurityVulnerabilitiesAge()))) {
                libraryPolicyAuditResponse.addAuditStatus(LibraryPolicyAuditStatus.LIBRARY_POLICY_FOUND_CRITICAL_SECURITY);
                isOk = false;
            }

            if (CollectionUtils.isNotEmpty(securityThreats) && securityThreats.stream().anyMatch(threat -> Objects.equals(threat.getLevel(), LibraryPolicyThreatLevel.High) && hasViolations(threat,settings.getHighSecurityVulnerabilitiesAge()))) {
                libraryPolicyAuditResponse.addAuditStatus(LibraryPolicyAuditStatus.LIBRARY_POLICY_FOUND_HIGH_SECURITY);
                isOk = false;
            }

            if (isOk) {
                libraryPolicyAuditResponse.addAuditStatus(LibraryPolicyAuditStatus.LIBRARY_POLICY_AUDIT_OK);
            }
        }
        return libraryPolicyAuditResponse;
    }

    private LibraryPolicyResult getResultByTimestamp(CollectorItem collectorItem, long beginDate, long endDate) {
        LibraryPolicyResult libraryPolicyResult = null;
        if (Objects.isNull(collectorItem)) return libraryPolicyResult;
        beginDate = (apiSettings.isIgnoreBeginDateForLibraryPolicy() || beginDate == DEFAULT_BEGIN_DATE) ? DEFAULT_BEGIN_DATE : beginDate - 1;
        List<LibraryPolicyResult> libraryPolicyResults = libraryPolicyResultsRepository.findByCollectorItemIdAndEvaluationTimestampIsBetweenOrderByTimestampDesc(collectorItem.getId(), beginDate, endDate);
        if (CollectionUtils.isEmpty(libraryPolicyResults)) return libraryPolicyResult;
        libraryPolicyResults.sort(Comparator.comparing(LibraryPolicyResult::getEvaluationTimestamp).reversed());
        return libraryPolicyResults.get(0);
    }

    private boolean hasViolations(LibraryPolicyResult.Threat threat, int age) {
        if (MapUtils.isEmpty(threat.getDispositionCounts())) {
            return threat.getCount() > 0;
        }
        return threat.getDispositionCounts().containsKey(LibraryPolicyThreatDisposition.Open) &&
                (threat.getDispositionCounts().get(LibraryPolicyThreatDisposition.Open) > 0) && (threat.getMaxAge() > age);
    }

    public void setSettings(ApiSettings settings) {
        this.settings = settings;
    }
}
