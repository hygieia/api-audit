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
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Comparator;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.function.Predicate;

@Component
public class InfrastructureEvaluator extends Evaluator<InfrastructureAuditResponse> {

    private static final String BUSINESS_SERVICE = "businessService";
    public static final String BUSINESS_COMPONENT = "businessComponent";
    public static final String CRITICAL = "CRITICAL";
    public static final String HIGH = "HIGH";
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
            List<InfrastructureScan> sortedDistinctForBAP = getSortedAndDistinctByInstanceId(filteredForBAP);
            setInfraAudit(infrastructureAuditResponse, sortedDistinctForBAP, InfrastructureAuditStatus.INFRA_SCAN_BUSS_COMP_CRITICAL, InfrastructureAuditStatus.INFRA_SCAN_BUSS_COMP_HIGH, InfrastructureAuditStatus.INFRA_SCAN_BUSS_COMP_OK);
            infrastructureAuditResponse.setInfrastructureScans(sortedDistinctForBAP);
        } else {
            infrastructureAuditResponse.addAuditStatus(InfrastructureAuditStatus.INFRA_SEC_SCAN_BUSS_COMP_NOT_FOUND);
            List<InfrastructureScan> filteredForASV = StringUtils.isEmpty(businessService) ? Collections.EMPTY_LIST :
                    infrastructureScans.stream().filter(infrastructureScan -> infrastructureScan.getBusinessService().equalsIgnoreCase(businessService)).collect(Collectors.toList());
            if (CollectionUtils.isNotEmpty(filteredForASV)) {
                List<InfrastructureScan> sortedDistinctForASV = getSortedAndDistinctByInstanceId(filteredForASV);
                setInfraAudit(infrastructureAuditResponse, sortedDistinctForASV, InfrastructureAuditStatus.INFRA_SEC_SCAN_BUSS_APP_CRITICAL, InfrastructureAuditStatus.INFRA_SEC_SCAN_BUSS_APP_HIGH, InfrastructureAuditStatus.INFRA_SEC_SCAN_BUSS_APP_OK);
                infrastructureAuditResponse.setInfrastructureScans(sortedDistinctForASV);
            } else {
                infrastructureAuditResponse.addAuditStatus(InfrastructureAuditStatus.INFRA_SEC_SCAN_BUSS_APP_NOT_FOUND);
                infrastructureAuditResponse.setInfrastructureScans(Collections.EMPTY_LIST);
            }
        }
        return infrastructureAuditResponse;
    }

    private void setInfraAudit(InfrastructureAuditResponse infrastructureAuditResponse, List<InfrastructureScan> infrastructureScans, InfrastructureAuditStatus critical, InfrastructureAuditStatus high, InfrastructureAuditStatus ok) {
        Set<String> risks = infrastructureScans.stream().map(InfrastructureScan::getVulnerabilities).flatMap(Collection::stream).map(Vulnerability::getContextualizedRiskLabel).collect(Collectors.toSet());
        boolean hasCritical = risks.stream().anyMatch(r -> r.equalsIgnoreCase(CRITICAL));
        boolean hasHigh = risks.stream().anyMatch(r -> r.equalsIgnoreCase(HIGH));

        if (hasCritical) {
            infrastructureAuditResponse.addAuditStatus(critical);
        }
        if (hasHigh) {
            infrastructureAuditResponse.addAuditStatus(high);
        }
        if (!hasCritical && !hasHigh) {
            infrastructureAuditResponse.addAuditStatus(ok);
        }
    }

    private List<InfrastructureScan> getSortedAndDistinctByInstanceId(List<InfrastructureScan> filteredForBAP) {
        return CollectionUtils.isNotEmpty(filteredForBAP) ? filteredForBAP.stream().sorted(Comparator.comparing(InfrastructureScan::getTimestamp).reversed())
                .filter(distinctByKey(InfrastructureScan::getInstanceId)).collect(Collectors.toList()) : Collections.EMPTY_LIST;
    }

    private Map<?, ?> getBusinessItemsMap(Dashboard dashboard) {
        Map bMap = new HashMap();
        bMap.put(BUSINESS_SERVICE, dashboard.getConfigurationItemBusServName());
        bMap.put(BUSINESS_COMPONENT, dashboard.getConfigurationItemBusAppName());
        return bMap;
    }

    public static <T> Predicate<T> distinctByKey(Function<T, Object> function) {
        Set<Object> seen = new HashSet<>();
        return t -> seen.add(function.apply(t));
    }
}
