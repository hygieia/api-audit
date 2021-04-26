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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class InfrastructureEvaluator extends Evaluator<InfrastructureAuditResponse> {

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
        return infrastructureScanItems.stream().map(item -> evaluate(item, beginDate, endDate, Collections.singletonMap(BUSINESS_COMPONENT, dashboard.getConfigurationItemBusAppName()))).collect(Collectors.toList());
    }

    @Override
    public Collection<InfrastructureAuditResponse> evaluateNextGen(ArtifactAuditRequest artifactAuditRequest, Dashboard dashboard, long beginDate, long endDate, Map<?, ?> data) throws AuditException {
        return null;
    }


    @Override
    public InfrastructureAuditResponse evaluate(CollectorItem collectorItem, long beginDate, long endDate, Map<?, ?> data) {
        return getInfrastructureScanResponse(collectorItem, beginDate, endDate, (String) data.get(BUSINESS_COMPONENT));
    }


    private InfrastructureAuditResponse getInfrastructureScanResponse(CollectorItem collectorItem, long beginDate, long endDate, String businessComponent) {
        InfrastructureAuditResponse infrastructureAuditResponse = new InfrastructureAuditResponse();
        infrastructureAuditResponse.setAuditEntity(collectorItem.getOptions());
        infrastructureAuditResponse.setLastUpdated(collectorItem.getLastUpdated());

        List<InfrastructureScan> infrastructureScans = infrastructureScanRepository.findByCollectorItemIdAndTimestampIsBetweenOrderByTimestampDesc(collectorItem.getId(), beginDate - 1, endDate + 1);
        // filter all scans for businesssComponent
        List<InfrastructureScan> filteredForBAP = infrastructureScans.stream().filter(infrastructureScan -> infrastructureScan.getBusinessApplication().equalsIgnoreCase(businessComponent)).collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(filteredForBAP)) {
            setInfraAudit(infrastructureAuditResponse, filteredForBAP, InfrastructureAuditStatus.INFRA_SCAN_BUSS_COMP_CRITICAL, InfrastructureAuditStatus.INFRA_SCAN_BUSS_COMP_HIGH);
        } else {
            infrastructureAuditResponse.addAuditStatus(InfrastructureAuditStatus.INFRA_SEC_SCAN_BUSS_COMP_NOT_FOUND);
        }
        //
        if (CollectionUtils.isNotEmpty(infrastructureScans)) {
            setInfraAudit(infrastructureAuditResponse, infrastructureScans, InfrastructureAuditStatus.INFRA_SEC_SCAN_BUSS_APP_CRITICAL, InfrastructureAuditStatus.INFRA_SEC_SCAN_BUSS_APP_HIGH);
        } else {
            infrastructureAuditResponse.addAuditStatus(InfrastructureAuditStatus.INFRA_SEC_SCAN_BUSS_APP_NOT_FOUND);
        }
        if (!infrastructureAuditResponse.getAuditStatuses().stream().filter(auditStatus -> auditStatus.equals(InfrastructureAuditStatus.INFRA_SCAN_BUSS_COMP_CRITICAL) ||
                auditStatus.equals(InfrastructureAuditStatus.INFRA_SCAN_BUSS_COMP_HIGH) ||
                auditStatus.equals(InfrastructureAuditStatus.INFRA_SEC_SCAN_BUSS_COMP_NOT_FOUND) ||
                auditStatus.equals(InfrastructureAuditStatus.INFRA_SEC_SCAN_BUSS_APP_CRITICAL) ||
                auditStatus.equals(InfrastructureAuditStatus.INFRA_SEC_SCAN_BUSS_APP_HIGH) ||
                auditStatus.equals(InfrastructureAuditStatus.INFRA_SEC_SCAN_BUSS_APP_NOT_FOUND)).findAny().isPresent()) {
            infrastructureAuditResponse.addAuditStatus(InfrastructureAuditStatus.INFRA_SCAN_BUSS_COMP_OK);
            infrastructureAuditResponse.addAuditStatus(InfrastructureAuditStatus.INFRA_SEC_SCAN_BUSS_APP__OK);
        }
        infrastructureAuditResponse.setInfrastructureScans(infrastructureScans);
        return infrastructureAuditResponse;
    }

    private void setInfraAudit(InfrastructureAuditResponse infrastructureAuditResponse, List<InfrastructureScan> filteredForBAP, InfrastructureAuditStatus infraScanBussCritical, InfrastructureAuditStatus infraScanBussHigh) {
        filteredForBAP.stream().forEach(infrastructureScan -> {
            Vulnerability criticalVuln = CollectionUtils.isNotEmpty(infrastructureScan.getVulnerabilities()) ? infrastructureScan.getVulnerabilities().stream().filter(vulnerability -> vulnerability.getContextualizedRiskLabel().equalsIgnoreCase("CRITICAL")).findAny().orElse(null) : null;
            if (Objects.nonNull(criticalVuln)) {
                infrastructureAuditResponse.addAuditStatus(infraScanBussCritical);
            }
            Vulnerability highVuln = CollectionUtils.isNotEmpty(infrastructureScan.getVulnerabilities()) ? infrastructureScan.getVulnerabilities().stream().filter(vulnerability -> vulnerability.getContextualizedRiskLabel().equalsIgnoreCase("HIGH")).findAny().orElse(null) : null;
            if (Objects.nonNull(highVuln)) {
                infrastructureAuditResponse.addAuditStatus(infraScanBussHigh);
            }
        });
    }
}
