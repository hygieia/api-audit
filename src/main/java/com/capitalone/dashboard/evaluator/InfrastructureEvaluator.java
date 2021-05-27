package com.capitalone.dashboard.evaluator;

import com.capitalone.dashboard.model.AuditException;
import com.capitalone.dashboard.model.CollectorItem;
import com.capitalone.dashboard.model.CollectorType;
import com.capitalone.dashboard.model.Dashboard;
import com.capitalone.dashboard.model.InfrastructureScan;
import com.capitalone.dashboard.model.Vulnerability;
import com.capitalone.dashboard.repository.InfrastructureScanRepository;
import com.capitalone.dashboard.request.ArtifactAuditRequest;
import com.capitalone.dashboard.response.InfrastructureAuditResponse;
import com.capitalone.dashboard.status.InfrastructureAuditStatus;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.HashMap;
import java.util.Comparator;
import java.util.stream.Collectors;

@Component
public class InfrastructureEvaluator extends Evaluator<InfrastructureAuditResponse> {

    private static final String BUSINESS_SERVICE = "businessService";
    public static final String BUSINESS_COMPONENT = "businessComponent";
    private final InfrastructureScanRepository infrastructureScanRepository;


    @Autowired
    public InfrastructureEvaluator(InfrastructureScanRepository infrastructureScanRepository) {
        this.infrastructureScanRepository = infrastructureScanRepository;
    }

    @Override
    public Collection<InfrastructureAuditResponse> evaluate(Dashboard dashboard, long beginDate, long endDate, Map<?, ?> data) throws AuditException {

        List<CollectorItem> infrastructureScanItems = getCollectorItems(dashboard, CollectorType.InfrastructureScan);
        if (CollectionUtils.isEmpty(infrastructureScanItems)) {
            throw new AuditException("No Infrastructure scan items configured", AuditException.NO_COLLECTOR_ITEM_CONFIGURED);
        }
        return infrastructureScanItems.stream().map(item -> evaluate(item, beginDate, endDate, getBusinessItemsMap(dashboard))).collect(Collectors.toList());
    }

    @Override
    public Collection<InfrastructureAuditResponse> evaluateNextGen(ArtifactAuditRequest artifactAuditRequest, Dashboard dashboard, long beginDate, long endDate, Map<?, ?> data) throws AuditException {
        return null;
    }


    @Override
    public InfrastructureAuditResponse evaluate(CollectorItem collectorItem, long beginDate, long endDate, Map<?, ?> data) {
        return getInfrastructureScanResponse(collectorItem, beginDate, endDate, data);
    }


    private InfrastructureAuditResponse getInfrastructureScanResponse(CollectorItem collectorItem, long beginDate, long endDate, Map<?, ?> data) {
        String businessService = (String) data.get(BUSINESS_SERVICE);
        String businessComponent = (String) data.get(BUSINESS_COMPONENT);
        InfrastructureAuditResponse infrastructureAuditResponse = new InfrastructureAuditResponse();
        infrastructureAuditResponse.setAuditEntity(collectorItem.getOptions());
        infrastructureAuditResponse.setLastUpdated(collectorItem.getLastUpdated());

        List<InfrastructureScan> infrastructureScans = infrastructureScanRepository.findByCollectorItemIdAndTimestampIsBetweenOrderByTimestampDesc(collectorItem.getId(), beginDate - 1, endDate + 1);
        List<InfrastructureScan> filteredForBAP = StringUtils.isEmpty(businessComponent) ? Collections.EMPTY_LIST :
                infrastructureScans.stream().filter(infrastructureScan -> infrastructureScan.getBusinessApplication().equalsIgnoreCase(businessComponent)).collect(Collectors.toList());

        if (CollectionUtils.isNotEmpty(filteredForBAP)) {
            InfrastructureScan infrastructureScanLatest = filteredForBAP.stream().sorted(Comparator.comparing(InfrastructureScan::getTimestamp).reversed()).findFirst().get();
            setInfraAudit(infrastructureAuditResponse, infrastructureScanLatest, InfrastructureAuditStatus.INFRA_SCAN_BUSS_COMP_CRITICAL, InfrastructureAuditStatus.INFRA_SCAN_BUSS_COMP_HIGH, InfrastructureAuditStatus.INFRA_SCAN_BUSS_COMP_OK);
            infrastructureAuditResponse.setInfrastructureScans(Collections.singletonList(infrastructureScanLatest));
        } else {
            infrastructureAuditResponse.addAuditStatus(InfrastructureAuditStatus.INFRA_SEC_SCAN_BUSS_COMP_NOT_FOUND);
            List<InfrastructureScan> filteredForASV = StringUtils.isEmpty(businessService) ? Collections.EMPTY_LIST :
                    infrastructureScans.stream().filter(infrastructureScan -> infrastructureScan.getBusinessService().equalsIgnoreCase(businessService)).collect(Collectors.toList());
            if (CollectionUtils.isNotEmpty(filteredForASV)) {
                InfrastructureScan infrastructureScanLatest = filteredForASV.stream().sorted(Comparator.comparing(InfrastructureScan::getTimestamp).reversed()).findFirst().get();
                setInfraAudit(infrastructureAuditResponse, infrastructureScanLatest, InfrastructureAuditStatus.INFRA_SEC_SCAN_BUSS_APP_CRITICAL, InfrastructureAuditStatus.INFRA_SEC_SCAN_BUSS_APP_HIGH, InfrastructureAuditStatus.INFRA_SEC_SCAN_BUSS_APP_OK);
                infrastructureAuditResponse.setInfrastructureScans(Collections.singletonList(infrastructureScanLatest));
            } else {
                infrastructureAuditResponse.addAuditStatus(InfrastructureAuditStatus.INFRA_SEC_SCAN_BUSS_APP_NOT_FOUND);
                infrastructureAuditResponse.setInfrastructureScans(Collections.EMPTY_LIST);
            }
        }
        return infrastructureAuditResponse;
    }

    private void setInfraAudit(InfrastructureAuditResponse infrastructureAuditResponse, InfrastructureScan infrastructureScan, InfrastructureAuditStatus infraScanBussCritical, InfrastructureAuditStatus infraScanBussHigh, InfrastructureAuditStatus infraScanOK) {
        Vulnerability criticalVuln = CollectionUtils.isNotEmpty(infrastructureScan.getVulnerabilities()) ? infrastructureScan.getVulnerabilities().stream().filter(vulnerability -> vulnerability.getContextualizedRiskLabel().equalsIgnoreCase("CRITICAL")).findAny().orElse(null) : null;
        if (Objects.nonNull(criticalVuln)) {
            infrastructureAuditResponse.addAuditStatus(infraScanBussCritical);
        }
        Vulnerability highVuln = CollectionUtils.isNotEmpty(infrastructureScan.getVulnerabilities()) ? infrastructureScan.getVulnerabilities().stream().filter(vulnerability -> vulnerability.getContextualizedRiskLabel().equalsIgnoreCase("HIGH")).findAny().orElse(null) : null;
        if (Objects.nonNull(highVuln)) {
            infrastructureAuditResponse.addAuditStatus(infraScanBussHigh);
        }
        if(Objects.isNull(criticalVuln) && Objects.isNull(highVuln)){
            infrastructureAuditResponse.addAuditStatus(infraScanOK);
        }
    }

    private Map<?, ?> getBusinessItemsMap(Dashboard dashboard) {
        Map bMap = new HashMap();
        bMap.put(BUSINESS_SERVICE, dashboard.getConfigurationItemBusServName());
        bMap.put(BUSINESS_COMPONENT, dashboard.getConfigurationItemBusAppName());
        return bMap;
    }
}
