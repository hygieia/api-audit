package com.capitalone.dashboard.evaluator;

import com.capitalone.dashboard.model.CollectorItem;
import com.capitalone.dashboard.model.InfrastructureScan;
import com.capitalone.dashboard.model.Vulnerability;
import com.capitalone.dashboard.repository.InfrastructureScanRepository;
import com.capitalone.dashboard.response.InfrastructureAuditResponse;
import com.capitalone.dashboard.status.InfrastructureAuditStatus;
import org.bson.types.ObjectId;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.Collection;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)

public class InfrastructureEvaluatorTest {

    @InjectMocks
    private InfrastructureEvaluator infrastructureEvaluator;

    @Mock
    private InfrastructureScanRepository infrastructureScanRepository;

    @Test
    public void testEvaluate_infrastructureScanBusinessItemsEmpty() {
        CollectorItem collectorItem = new CollectorItem();
        collectorItem.setId(ObjectId.get());
        InfrastructureAuditResponse response = infrastructureEvaluator.evaluate(collectorItem, 125634536, 6235243, getBusinessItemsMap("", ""));
        Assert.assertEquals(true, response.getAuditStatuses().toString().contains(InfrastructureAuditStatus.INFRA_SEC_SCAN_BUSS_APP_NOT_FOUND.name()));
        Assert.assertEquals(true, response.getAuditStatuses().toString().contains(InfrastructureAuditStatus.INFRA_SEC_SCAN_BUSS_COMP_NOT_FOUND.name()));
    }

    @Test
    public void testEvaluate_infrastructureScanBusinessItemsNull() {
        CollectorItem collectorItem = new CollectorItem();
        collectorItem.setId(ObjectId.get());
        InfrastructureAuditResponse response = infrastructureEvaluator.evaluate(collectorItem, 125634536, 6235243, getBusinessItemsMap(null, null));
        Assert.assertEquals(true, response.getAuditStatuses().toString().contains(InfrastructureAuditStatus.INFRA_SEC_SCAN_BUSS_APP_NOT_FOUND.name()));
        Assert.assertEquals(true, response.getAuditStatuses().toString().contains(InfrastructureAuditStatus.INFRA_SEC_SCAN_BUSS_COMP_NOT_FOUND.name()));
    }

    @Test
    public void testEvaluate_infrastructureScanServiceCriticalInvalidComponent() {
        List<InfrastructureScan> infrastructureScanCriticalInvalidComponent = getInfraScanData("testService", null,"Critical");
        when(infrastructureScanRepository.findByCollectorItemIdAndTimestampIsBetweenOrderByTimestampDesc(any(ObjectId.class), any(Long.class), any(Long.class))).thenReturn(infrastructureScanCriticalInvalidComponent);
        CollectorItem collectorItem = new CollectorItem();
        collectorItem.setId(ObjectId.get());
        InfrastructureAuditResponse response = infrastructureEvaluator.evaluate(collectorItem, 125634536, 6235243, getBusinessItemsMap("testService", null));
        Assert.assertEquals(true, response.getAuditStatuses().toString().contains(InfrastructureAuditStatus.INFRA_SEC_SCAN_BUSS_APP_CRITICAL .name()));
    }

    @Test
    public void testEvaluate_infrastructureScanServiceInvalidComponentCritical() {
        List<InfrastructureScan> infrastructureScanCriticalInvalidComponent = getInfraScanData(null, "testComponent","Critical");
        when(infrastructureScanRepository.findByCollectorItemIdAndTimestampIsBetweenOrderByTimestampDesc(any(ObjectId.class), any(Long.class), any(Long.class))).thenReturn(infrastructureScanCriticalInvalidComponent);
        CollectorItem collectorItem = new CollectorItem();
        collectorItem.setId(ObjectId.get());
        InfrastructureAuditResponse response = infrastructureEvaluator.evaluate(collectorItem, 125634536, 6235243, getBusinessItemsMap(null, "testComponent"));
        Assert.assertEquals(true, response.getAuditStatuses().toString().contains(InfrastructureAuditStatus.INFRA_SCAN_BUSS_COMP_CRITICAL.name()));
    }

    @Test
    public void testEvaluate_infrastructureScanOfComponentAndServiceMissing() {
        when(infrastructureScanRepository.findByCollectorItemIdAndTimestampIsBetweenOrderByTimestampDesc(any(ObjectId.class), any(Long.class), any(Long.class))).thenReturn(Collections.emptyList());
        CollectorItem collectorItem = new CollectorItem();
        collectorItem.setId(ObjectId.get());
        InfrastructureAuditResponse response = infrastructureEvaluator.evaluate(collectorItem, 125634536, 6235243, getBusinessItemsMap("testService", "testComponent"));
        Assert.assertEquals(true, response.getAuditStatuses().toString().contains(InfrastructureAuditStatus.INFRA_SEC_SCAN_BUSS_COMP_NOT_FOUND.name()));
        Assert.assertEquals(true, response.getAuditStatuses().toString().contains(InfrastructureAuditStatus.INFRA_SEC_SCAN_BUSS_APP_NOT_FOUND.name()));
    }

    @Test
    public void testEvaluate_infrastructureComponentCritical() {
        List<InfrastructureScan> infrastructureScanCritical = getInfraScanData("testService", "testComponent","Critical");
        when(infrastructureScanRepository.findByCollectorItemIdAndTimestampIsBetweenOrderByTimestampDesc(any(ObjectId.class), any(Long.class), any(Long.class))).thenReturn(infrastructureScanCritical);
        InfrastructureAuditResponse response = infrastructureEvaluator.evaluate(getCollectorItem(), 125634436, 125634636, getBusinessItemsMap("testService", "testComponent"));
        Assert.assertEquals(true, response.getAuditStatuses().toString().contains(InfrastructureAuditStatus.INFRA_SCAN_BUSS_COMP_CRITICAL.name()));
    }

    @Test
    public void testEvaluate_infrastructureNoComponentServiceOnlyCritical() {
        List<InfrastructureScan> infrastructureScanCritical = getInfraScanData("testService", null,"Critical");
        when(infrastructureScanRepository.findByCollectorItemIdAndTimestampIsBetweenOrderByTimestampDesc(any(ObjectId.class), any(Long.class), any(Long.class))).thenReturn(infrastructureScanCritical);
        CollectorItem collectorItem = new CollectorItem();
        collectorItem.getOptions().put("businessApplication", "testService");
        InfrastructureAuditResponse response = infrastructureEvaluator.evaluate(collectorItem, 125634436, 125634636, getBusinessItemsMap("testService", null));
        Assert.assertEquals(true, response.getAuditStatuses().toString().contains(InfrastructureAuditStatus.INFRA_SEC_SCAN_BUSS_APP_CRITICAL.name()));
        Assert.assertEquals(true, response.getAuditStatuses().toString().contains(InfrastructureAuditStatus.INFRA_SEC_SCAN_BUSS_COMP_NOT_FOUND.name()));
    }

    @Test
    public void testEvaluate_infrastructureComponentHigh() {
        List<InfrastructureScan> infrastructureScanHigh = getInfraScanData("testService", "testComponent", "High");
        when(infrastructureScanRepository.findByCollectorItemIdAndTimestampIsBetweenOrderByTimestampDesc(any(ObjectId.class), any(Long.class), any(Long.class))).thenReturn(infrastructureScanHigh);
        InfrastructureAuditResponse response = infrastructureEvaluator.evaluate(getCollectorItem(), 125634436, 125634636, getBusinessItemsMap("testService", "testComponent"));
        Assert.assertEquals(true, response.getAuditStatuses().toString().contains(InfrastructureAuditStatus.INFRA_SCAN_BUSS_COMP_HIGH.name()));
    }

    @Test
    public void testEvaluate_infrastructureMedium() {
        List<InfrastructureScan> infrastructureScanMedium = getInfraScanData("testService", "testComponent", "Medium");
        when(infrastructureScanRepository.findByCollectorItemIdAndTimestampIsBetweenOrderByTimestampDesc(any(ObjectId.class), any(Long.class), any(Long.class))).thenReturn(infrastructureScanMedium);
        InfrastructureAuditResponse response = infrastructureEvaluator.evaluate(getCollectorItem(), 125634436, 125634636, getBusinessItemsMap("testService", "testComponent"));
        Assert.assertEquals(true, response.getAuditStatuses().toString().contains(InfrastructureAuditStatus.INFRA_SCAN_BUSS_COMP_OK.name()));
    }

    @Test
    public void testEvaluate_infrastructureLow() {
        List<InfrastructureScan> infrastructureScanLow = getInfraScanData("testService", "testComponent", "Low");
        when(infrastructureScanRepository.findByCollectorItemIdAndTimestampIsBetweenOrderByTimestampDesc(any(ObjectId.class), any(Long.class), any(Long.class))).thenReturn(infrastructureScanLow);
        InfrastructureAuditResponse response = infrastructureEvaluator.evaluate(getCollectorItem(), 125634436, 125634636, getBusinessItemsMap("testService", "testComponent"));
        Assert.assertEquals(true, response.getAuditStatuses().toString().contains(InfrastructureAuditStatus.INFRA_SCAN_BUSS_COMP_OK.name()));
    }


    @Test
    public void testEvaluate_infrastructureNotFound() {
        List<InfrastructureScan> infrastructureScanNotFound = new ArrayList<>();
        when(infrastructureScanRepository.findByCollectorItemIdAndTimestampIsBetweenOrderByTimestampDesc(any(ObjectId.class), any(Long.class), any(Long.class))).thenReturn(infrastructureScanNotFound);
        InfrastructureAuditResponse response = infrastructureEvaluator.evaluate(getCollectorItem(), 125634436, 125634636, getBusinessItemsMap("testService", "testComponent"));
        Assert.assertEquals(true, response.getAuditStatuses().toString().contains(InfrastructureAuditStatus.INFRA_SEC_SCAN_BUSS_APP_NOT_FOUND.name()));
        Assert.assertEquals(true, response.getAuditStatuses().toString().contains(InfrastructureAuditStatus.INFRA_SEC_SCAN_BUSS_COMP_NOT_FOUND.name()));
    }

    @Test
    public void testEvaluate_infrastructureComponentDistinctByInstanceId() {
        when(infrastructureScanRepository.findByCollectorItemIdAndTimestampIsBetweenOrderByTimestampDesc(any(ObjectId.class), any(Long.class), any(Long.class))).thenReturn(getInfraScansWithInstances());
        InfrastructureAuditResponse response = infrastructureEvaluator.evaluate(getCollectorItem(), 123450000, 123457777, getBusinessItemsMap("testService", "testComponent"));
        Assert.assertEquals(true, response.getAuditStatuses().toString().contains(InfrastructureAuditStatus.INFRA_SCAN_BUSS_COMP_HIGH.name()));
        Assert.assertEquals(true, response.getAuditStatuses().toString().contains(InfrastructureAuditStatus.INFRA_SCAN_BUSS_COMP_CRITICAL.name()));
        Assert.assertEquals(2, response.getAuditStatuses().size());
        Assert.assertEquals(3, response.getInfrastructureScans().size());
    }

    @Test
    public void testEvaluate_infrastructureAppDistinctByInstanceId() {
        List<InfrastructureScan> infrastructureScans = getInfraScansWithInstances();
        infrastructureScans.forEach(infrastructureScan -> infrastructureScan.setBusinessApplication(""));
        CollectorItem collectorItem = new CollectorItem();
        collectorItem.getOptions().put("businessApplication", "testService");
        when(infrastructureScanRepository.findByCollectorItemIdAndTimestampIsBetweenOrderByTimestampDesc(any(ObjectId.class), any(Long.class), any(Long.class))).thenReturn(infrastructureScans);
        InfrastructureAuditResponse response = infrastructureEvaluator.evaluate(collectorItem, 123450000, 123457777, getBusinessItemsMap("testService", "testComponent"));
        Assert.assertEquals(true, response.getAuditStatuses().toString().contains(InfrastructureAuditStatus.INFRA_SEC_SCAN_BUSS_APP_HIGH.name()));
        Assert.assertEquals(true, response.getAuditStatuses().toString().contains(InfrastructureAuditStatus.INFRA_SEC_SCAN_BUSS_APP_CRITICAL.name()));
        Assert.assertEquals(true, response.getAuditStatuses().toString().contains(InfrastructureAuditStatus.INFRA_SEC_SCAN_BUSS_COMP_NOT_FOUND.name()));
        Assert.assertEquals(3, response.getAuditStatuses().size());
        Assert.assertEquals(3, response.getInfrastructureScans().size());
    }

    @Test
    public void testEvaluate_infrastructureDistinctByInstanceIdCompOk() {
        List<InfrastructureScan> infrastructureScans = getInfraScansWithInstances();
        infrastructureScans.stream().map(InfrastructureScan::getVulnerabilities).flatMap(Collection::stream).forEach(v -> v.setContextualizedRiskLabel("MEDIUM"));
        when(infrastructureScanRepository.findByCollectorItemIdAndTimestampIsBetweenOrderByTimestampDesc(any(ObjectId.class), any(Long.class), any(Long.class))).thenReturn(infrastructureScans);
        InfrastructureAuditResponse response = infrastructureEvaluator.evaluate(getCollectorItem(), 123450000, 123457777, getBusinessItemsMap("testService", "testComponent"));
        Assert.assertEquals(true, response.getAuditStatuses().toString().contains(InfrastructureAuditStatus.INFRA_SCAN_BUSS_COMP_OK.name()));
        Assert.assertEquals(1, response.getAuditStatuses().size());
        Assert.assertEquals(3, response.getInfrastructureScans().size());
    }

    private CollectorItem getCollectorItem() {
        CollectorItem collectorItem = new CollectorItem();
        collectorItem.getOptions().put("businessApplication", "testService");
        collectorItem.getOptions().put("businessComponent", "testComponent");
        return collectorItem;
    }

    private List<InfrastructureScan> getInfraScanData(String service, String component, String riskLevel) {
        InfrastructureScan infrastructureScan = new InfrastructureScan();
        Vulnerability vulnerability = new Vulnerability();
        vulnerability.setContextualizedRiskLabel(riskLevel);
        infrastructureScan.setVulnerabilities(Arrays.asList(vulnerability));
        infrastructureScan.setBusinessService(service);
        infrastructureScan.setBusinessApplication(component);
        return Arrays.asList(infrastructureScan);
    }

    private List<InfrastructureScan> getInfraScansWithInstances() {
        List<InfrastructureScan> infrastructureScans = new ArrayList<>();
        infrastructureScans.add(getInfrastructureScan("CRITICAL", "instance1", 123451111));
        infrastructureScans.add(getInfrastructureScan("HIGH", "instance2", 123455555));
        infrastructureScans.add(getInfrastructureScan("MEDIUM", "instance3", 123451111));
        infrastructureScans.add(getInfrastructureScan("CRITICAL", "instance2", 123456666));
        infrastructureScans.add(getInfrastructureScan("HIGH", "instance1", 123452222));
        return infrastructureScans;
    }

    private InfrastructureScan getInfrastructureScan(String riskLevel, String instanceId, long timestamp) {
        InfrastructureScan infrastructureScan = new InfrastructureScan();
        infrastructureScan.setInstanceId(instanceId);
        infrastructureScan.setTimestamp(timestamp);
        Vulnerability vulnerability = new Vulnerability();
        vulnerability.setContextualizedRiskLabel(riskLevel);
        infrastructureScan.setVulnerabilities(Arrays.asList(vulnerability));
        infrastructureScan.setBusinessService("testService");
        infrastructureScan.setBusinessApplication("testComponent");
        return infrastructureScan;
    }

    private Map<?,?> getBusinessItemsMap(String service, String component) {
        Map bMap = new HashMap();
        bMap.put("businessService", service);
        bMap.put("businessComponent", component);
        return bMap;
    }
}
