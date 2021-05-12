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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)

public class InfrastructureEvaluatorTest {

    @InjectMocks
    private InfrastructureEvaluator infrastructureEvaluator;

    @Mock
    private InfrastructureScanRepository infrastructureScanRepository;

    @Test
    public void testEvaluate_infrastructureScanMissing() {
        CollectorItem collectorItem = new CollectorItem();
        collectorItem.setId(ObjectId.get());
        InfrastructureAuditResponse response = infrastructureEvaluator.evaluate(collectorItem, 125634536, 6235243, Collections.singletonMap("businessComponent", "testBap"));
        Assert.assertEquals(true, response.getAuditStatuses().toString().contains(InfrastructureAuditStatus.INFRA_SEC_SCAN_BUSS_APP_NOT_FOUND.name()));
    }

    @Test
    public void testEvaluate_infrastructureCritical() {

        List<InfrastructureScan> infrastructureScanCritical = getInfraScanData("Critical");
        when(infrastructureScanRepository.findByCollectorItemIdAndTimestampIsBetweenOrderByTimestampDesc(any(ObjectId.class), any(Long.class), any(Long.class))).thenReturn(infrastructureScanCritical);
        CollectorItem collectorItem = new CollectorItem();
        collectorItem.getOptions().put("businessApplication", "testBap");
        collectorItem.getOptions().put("businessComponent", "testComponent");
        InfrastructureAuditResponse response = infrastructureEvaluator.evaluate(collectorItem, 125634436, 125634636, Collections.singletonMap("businessComponent", "testBap"));
        Assert.assertEquals(true, response.getAuditStatuses().toString().contains(InfrastructureAuditStatus.INFRA_SEC_SCAN_BUSS_APP_CRITICAL.name()));
        Assert.assertEquals(true, response.getAuditStatuses().toString().contains(InfrastructureAuditStatus.INFRA_SCAN_BUSS_COMP_CRITICAL.name()));
    }

    @Test
    public void testEvaluate_infrastructureHigh() {
        List<InfrastructureScan> infrastructureScanHigh = getInfraScanData("High");
        when(infrastructureScanRepository.findByCollectorItemIdAndTimestampIsBetweenOrderByTimestampDesc(any(ObjectId.class), any(Long.class), any(Long.class))).thenReturn(infrastructureScanHigh);
        CollectorItem collectorItem = new CollectorItem();
        collectorItem.getOptions().put("businessApplication", "testBap");
        collectorItem.getOptions().put("businessComponent", "testComponent");
        InfrastructureAuditResponse response = infrastructureEvaluator.evaluate(collectorItem, 125634436, 125634636, Collections.singletonMap("businessComponent", "testBap"));
        Assert.assertEquals(true, response.getAuditStatuses().toString().contains(InfrastructureAuditStatus.INFRA_SEC_SCAN_BUSS_APP_HIGH.name()));
        Assert.assertEquals(true, response.getAuditStatuses().toString().contains(InfrastructureAuditStatus.INFRA_SCAN_BUSS_COMP_HIGH.name()));
    }

    @Test
    public void testEvaluate_infrastructureMedium() {
        List<InfrastructureScan> infrastructureScanMedium = getInfraScanData("Medium");
        when(infrastructureScanRepository.findByCollectorItemIdAndTimestampIsBetweenOrderByTimestampDesc(any(ObjectId.class), any(Long.class), any(Long.class))).thenReturn(infrastructureScanMedium);
        CollectorItem collectorItem = new CollectorItem();
        collectorItem.getOptions().put("businessApplication", "testBap");
        collectorItem.getOptions().put("businessComponent", "testComponent");
        InfrastructureAuditResponse response = infrastructureEvaluator.evaluate(collectorItem, 125634436, 125634636, Collections.singletonMap("businessComponent", "testBap"));
        Assert.assertEquals(true, response.getAuditStatuses().toString().contains(InfrastructureAuditStatus.INFRA_SEC_SCAN_BUSS_APP_OK.name()));
        Assert.assertEquals(true, response.getAuditStatuses().toString().contains(InfrastructureAuditStatus.INFRA_SCAN_BUSS_COMP_OK.name()));
    }

    @Test
    public void testEvaluate_infrastructureLow() {
        List<InfrastructureScan> infrastructureScanLow = getInfraScanData("Low");
        when(infrastructureScanRepository.findByCollectorItemIdAndTimestampIsBetweenOrderByTimestampDesc(any(ObjectId.class), any(Long.class), any(Long.class))).thenReturn(infrastructureScanLow);
        CollectorItem collectorItem = new CollectorItem();
        collectorItem.getOptions().put("businessApplication", "testBap");
        collectorItem.getOptions().put("businessComponent", "testComponent");
        InfrastructureAuditResponse response = infrastructureEvaluator.evaluate(collectorItem, 125634436, 125634636, Collections.singletonMap("businessComponent", "testBap"));
        Assert.assertEquals(true, response.getAuditStatuses().toString().contains(InfrastructureAuditStatus.INFRA_SEC_SCAN_BUSS_APP_OK.name()));
        Assert.assertEquals(true, response.getAuditStatuses().toString().contains(InfrastructureAuditStatus.INFRA_SCAN_BUSS_COMP_OK.name()));
    }


    @Test
    public void testEvaluate_infrastructureNotFound() {
        List<InfrastructureScan> infrastructureScanNotFound = new ArrayList<>();
        when(infrastructureScanRepository.findByCollectorItemIdAndTimestampIsBetweenOrderByTimestampDesc(any(ObjectId.class), any(Long.class), any(Long.class))).thenReturn(infrastructureScanNotFound);
        CollectorItem collectorItem = new CollectorItem();
        collectorItem.getOptions().put("businessApplication", "testBap");
        collectorItem.getOptions().put("businessComponent", "testComponent");
        InfrastructureAuditResponse response = infrastructureEvaluator.evaluate(collectorItem, 125634436, 125634636, Collections.singletonMap("businessComponent", "testBap"));
        Assert.assertEquals(true, response.getAuditStatuses().toString().contains(InfrastructureAuditStatus.INFRA_SEC_SCAN_BUSS_APP_NOT_FOUND.name()));
        Assert.assertEquals(true, response.getAuditStatuses().toString().contains(InfrastructureAuditStatus.INFRA_SEC_SCAN_BUSS_COMP_NOT_FOUND.name()));
    }

    private List<InfrastructureScan> getInfraScanData(String riskLevel) {
        InfrastructureScan infrastructureScan = new InfrastructureScan();
        Vulnerability vulnerability = new Vulnerability();
        vulnerability.setContextualizedRiskLabel(riskLevel);
        infrastructureScan.setVulnerabilities(Arrays.asList(vulnerability));
        infrastructureScan.setBusinessApplication("testBap");
        return Arrays.asList(infrastructureScan);
    }
}
