package com.capitalone.dashboard.evaluator;

import com.capitalone.dashboard.ApiSettings;
import com.capitalone.dashboard.common.TestUtils;

import com.capitalone.dashboard.model.AuditException;
import com.capitalone.dashboard.model.CollectorItem;
import com.capitalone.dashboard.model.Dashboard;
import com.capitalone.dashboard.model.DashboardType;
import com.capitalone.dashboard.model.TestCapability;
import com.capitalone.dashboard.model.TestResult;
import com.capitalone.dashboard.model.TestSuite;
import com.capitalone.dashboard.model.TestSuiteType;
import com.capitalone.dashboard.repository.DashboardRepository;
import com.capitalone.dashboard.repository.FeatureRepository;
import com.capitalone.dashboard.repository.TestResultRepository;
import com.capitalone.dashboard.response.TestResultsAuditResponse;
import com.capitalone.dashboard.status.TestResultAuditStatus;
import org.bson.types.ObjectId;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.IOException;
import java.util.*;

@RunWith(MockitoJUnitRunner.class)
public class FeatureTestResultEvaluatorTest {

    public static final String FUNCTIONAL = "Functional";


    @InjectMocks
    private FeatureTestResultEvaluator featureTestResultEvaluator;

    @Mock
    private TestResultRepository testResultRepository;

    @Mock
    private DashboardRepository dashboardRepository;

    @Mock
    private FeatureRepository featureRepository;

    @Before
    public void setup() throws IOException {
        featureTestResultEvaluator.setSettings(getSettings());
    }

    @Test
    public void evaluate_testResultMissing() throws AuditException {
        FeatureTestResultEvaluator featureTestResultEvaluator = Mockito.spy(this.featureTestResultEvaluator);
        Dashboard dashboard = getDashboard();
        CollectorItem collectorItem = new CollectorItem();
        collectorItem.setId(ObjectId.get());

        Map<String, String> data = getDataMap();
        Map<String, Object> collItemOptions = new HashMap<>();
        collItemOptions.put("artifactName", "artifact-1");
        collItemOptions.put("artifactVersion", data.get("identifierVersion"));
        collectorItem.setOptions(collItemOptions);
        doReturn(collectorItem).when(featureTestResultEvaluator).getCollectorItemForIdentifierVersion(any(Dashboard.class), any(Map.class));
        List<TestResult> emptyTestResults = new ArrayList<>();
        when(testResultRepository.findByCollectorItemIdAndTimestampIsBetweenOrderByTimestampDesc(collectorItem.getId(),
                123456789, 123456989)).thenReturn(emptyTestResults);
        List<TestResultsAuditResponse> testResultsAuditResponseCollection = (List<TestResultsAuditResponse>) featureTestResultEvaluator.evaluate(getDashboard(), 123456789, 123456989, data, "altident", "artifact-1");
        Assert.assertTrue(testResultsAuditResponseCollection.get(0).getAuditStatuses().contains(TestResultAuditStatus.TEST_RESULT_MISSING));
        Assert.assertTrue(!testResultsAuditResponseCollection.get(0).getAuditStatuses().contains(TestResultAuditStatus.TEST_RESULT_AUDIT_OK));
        Assert.assertTrue(!testResultsAuditResponseCollection.get(0).getAuditStatuses().contains(TestResultAuditStatus.TEST_RESULT_AUDIT_FAIL));
        Assert.assertTrue(!testResultsAuditResponseCollection.get(0).getAuditStatuses().contains(TestResultAuditStatus.TEST_RESULT_SKIPPED));
    }

    @Test
    public void evaluate_testResultAuditOK() throws AuditException {
        FeatureTestResultEvaluator featureTestResultEvaluator = Mockito.spy(this.featureTestResultEvaluator);
        CollectorItem collectorItem = new CollectorItem();
        collectorItem.setId(ObjectId.get());
        Map<String, String> data = getDataMap();
        List<TestResult> testResults = Arrays.asList(getAuditOKTestResult());
        doReturn(collectorItem).when(featureTestResultEvaluator).getCollectorItemForIdentifierVersion(any(Dashboard.class), any(Map.class));

        when(testResultRepository.findByCollectorItemIdAndTimestampIsBetweenOrderByTimestampDesc(any(ObjectId.class),
                any(Long.class), any(Long.class))).thenReturn(testResults);
        List<TestResultsAuditResponse> testResultsAuditResponseCollection = (List<TestResultsAuditResponse>) featureTestResultEvaluator.evaluate(getDashboard(), 123456789, 123456989, data, "altident", "artifact-1");
        Assert.assertTrue(!testResultsAuditResponseCollection.get(0).getAuditStatuses().contains(TestResultAuditStatus.TEST_RESULT_MISSING));
        Assert.assertTrue(testResultsAuditResponseCollection.get(0).getAuditStatuses().contains(TestResultAuditStatus.TEST_RESULT_AUDIT_OK));
        Assert.assertTrue(!testResultsAuditResponseCollection.get(0).getAuditStatuses().contains(TestResultAuditStatus.TEST_RESULT_AUDIT_FAIL));
        Assert.assertTrue(!testResultsAuditResponseCollection.get(0).getAuditStatuses().contains(TestResultAuditStatus.TEST_RESULT_SKIPPED));
    }

    @Test
    public void evaluate_testResultAuditFAIL() throws AuditException {
        FeatureTestResultEvaluator featureTestResultEvaluator = Mockito.spy(this.featureTestResultEvaluator);
        CollectorItem collectorItem = new CollectorItem();
        collectorItem.setId(ObjectId.get());
        Map<String, String> data = getDataMap();
        List<TestResult> testResults = Arrays.asList(getAuditFAILTestResult());
        doReturn(collectorItem).when(featureTestResultEvaluator).getCollectorItemForIdentifierVersion(any(Dashboard.class), any(Map.class));

        when(testResultRepository.findByCollectorItemIdAndTimestampIsBetweenOrderByTimestampDesc(any(ObjectId.class),
                any(Long.class), any(Long.class))).thenReturn(testResults);
        List<TestResultsAuditResponse> testResultsAuditResponseCollection = (List<TestResultsAuditResponse>) featureTestResultEvaluator.evaluate(getDashboard(), 123456789, 123456989, data, "altident", "artifact-1");
        Assert.assertTrue(!testResultsAuditResponseCollection.get(0).getAuditStatuses().contains(TestResultAuditStatus.TEST_RESULT_MISSING));
        Assert.assertTrue(!testResultsAuditResponseCollection.get(0).getAuditStatuses().contains(TestResultAuditStatus.TEST_RESULT_AUDIT_OK));
        Assert.assertTrue(testResultsAuditResponseCollection.get(0).getAuditStatuses().contains(TestResultAuditStatus.TEST_RESULT_AUDIT_FAIL));
        Assert.assertTrue(!testResultsAuditResponseCollection.get(0).getAuditStatuses().contains(TestResultAuditStatus.TEST_RESULT_SKIPPED));
    }

    @Test
    public void evaluate_testResultAuditSKIP() throws AuditException {
        FeatureTestResultEvaluator featureTestResultEvaluator = Mockito.spy(this.featureTestResultEvaluator);
        CollectorItem collectorItem = new CollectorItem();
        collectorItem.setId(ObjectId.get());
        Map<String, String> data = getDataMap();
        List<TestResult> testResults = Arrays.asList(getAuditSKIPTestResult());
        doReturn(collectorItem).when(featureTestResultEvaluator).getCollectorItemForIdentifierVersion(any(Dashboard.class), any(Map.class));

        when(testResultRepository.findByCollectorItemIdAndTimestampIsBetweenOrderByTimestampDesc(any(ObjectId.class),
                any(Long.class), any(Long.class))).thenReturn(testResults);
        List<TestResultsAuditResponse> testResultsAuditResponseCollection = (List<TestResultsAuditResponse>) featureTestResultEvaluator.evaluate(getDashboard(), 123456789, 123456989, data, "altident", "artifact-1");
        Assert.assertTrue(!testResultsAuditResponseCollection.get(0).getAuditStatuses().contains(TestResultAuditStatus.TEST_RESULT_MISSING));
        Assert.assertTrue(!testResultsAuditResponseCollection.get(0).getAuditStatuses().contains(TestResultAuditStatus.TEST_RESULT_AUDIT_OK));
        Assert.assertTrue(!testResultsAuditResponseCollection.get(0).getAuditStatuses().contains(TestResultAuditStatus.TEST_RESULT_AUDIT_FAIL));
        Assert.assertTrue(testResultsAuditResponseCollection.get(0).getAuditStatuses().contains(TestResultAuditStatus.TEST_RESULT_SKIPPED));
    }

    @Test
    public void evaluate_multipleTestResults() throws AuditException {
        FeatureTestResultEvaluator featureTestResultEvaluator = Mockito.spy(this.featureTestResultEvaluator);
        CollectorItem collectorItem = new CollectorItem();
        collectorItem.setId(ObjectId.get());
        Map<String, String> data = getDataMap();
        List<TestResult> testResults = new ArrayList<>();
        testResults.add(getAuditFAILTestResult());
        testResults.add(getAuditOKTestResult());
        doReturn(collectorItem).when(featureTestResultEvaluator).getCollectorItemForIdentifierVersion(any(Dashboard.class), any(Map.class));

        when(testResultRepository.findByCollectorItemIdAndTimestampIsBetweenOrderByTimestampDesc(any(ObjectId.class),
                any(Long.class), any(Long.class))).thenReturn(testResults);
        List<TestResultsAuditResponse> testResultsAuditResponseCollection = (List<TestResultsAuditResponse>) featureTestResultEvaluator.evaluate(getDashboard(), 123456789, 123456989, data, "altident", "artifact-1");
        Assert.assertTrue(!testResultsAuditResponseCollection.get(0).getAuditStatuses().contains(TestResultAuditStatus.TEST_RESULT_MISSING));
        Assert.assertTrue(!testResultsAuditResponseCollection.get(0).getAuditStatuses().contains(TestResultAuditStatus.TEST_RESULT_AUDIT_OK));
        Assert.assertTrue(testResultsAuditResponseCollection.get(0).getAuditStatuses().contains(TestResultAuditStatus.TEST_RESULT_AUDIT_FAIL));
        Assert.assertTrue(!testResultsAuditResponseCollection.get(0).getAuditStatuses().contains(TestResultAuditStatus.TEST_RESULT_SKIPPED));

        Assert.assertTrue(!testResultsAuditResponseCollection.get(1).getAuditStatuses().contains(TestResultAuditStatus.TEST_RESULT_MISSING));
        Assert.assertTrue(testResultsAuditResponseCollection.get(1).getAuditStatuses().contains(TestResultAuditStatus.TEST_RESULT_AUDIT_OK));
        Assert.assertTrue(!testResultsAuditResponseCollection.get(1).getAuditStatuses().contains(TestResultAuditStatus.TEST_RESULT_AUDIT_FAIL));
        Assert.assertTrue(!testResultsAuditResponseCollection.get(1).getAuditStatuses().contains(TestResultAuditStatus.TEST_RESULT_SKIPPED));
    }

    @Test
    public void evaluate_featureTestResult() {
        TestResult testResult = getTestResult();
        HashMap featureTestMap = featureTestResultEvaluator.getFeatureTestResult(testResult);
        Assert.assertEquals(testResult.getSuccessCount(), Integer.parseInt(featureTestMap.get("successCount").toString()));
        Assert.assertEquals(testResult.getFailureCount(), Integer.parseInt(featureTestMap.get("failureCount").toString()));
        Assert.assertEquals(testResult.getSkippedCount(), Integer.parseInt(featureTestMap.get("skippedCount").toString()));
        Assert.assertEquals(testResult.getTotalCount(), Integer.parseInt(featureTestMap.get("totalCount").toString()));
    }

    private TestResult getTestResult() {
        TestResult testResult = new TestResult();
        testResult.setType(TestSuiteType.Functional);
        testResult.setSuccessCount(10);
        testResult.setFailureCount(5);
        testResult.setSkippedCount(1);
        testResult.setTotalCount(16);
        return testResult;
    }
    private TestResult getAuditOKTestResult() {
        TestResult testResult = new TestResult();
        testResult.setType(TestSuiteType.Functional);
        TestCapability testCapability = new TestCapability();

        TestSuite testSuite1 = new TestSuite();
        testSuite1.setSuccessTestCaseCount(20);
        testSuite1.setFailedTestCaseCount(0);
        testSuite1.setSkippedTestCaseCount(0);
        testSuite1.setTotalTestCaseCount(20);

        TestSuite testSuite2 = new TestSuite();
        testSuite2.setSuccessTestCaseCount(20);
        testSuite2.setFailedTestCaseCount(0);
        testSuite2.setSkippedTestCaseCount(0);
        testSuite2.setTotalTestCaseCount(20);

        testCapability.getTestSuites().add(testSuite1);
        testCapability.getTestSuites().add(testSuite2);
        testResult.getTestCapabilities().add(testCapability);
        return testResult;
    }

    private TestResult getAuditFAILTestResult() {
        TestResult testResult = new TestResult();
        testResult.setType(TestSuiteType.Functional);
        TestCapability testCapability = new TestCapability();


        TestSuite testSuite = new TestSuite();
        testSuite.setSuccessTestCaseCount(37);
        testSuite.setFailedTestCaseCount(2);
        testSuite.setSkippedTestCaseCount(1);
        testSuite.setTotalTestCaseCount(40);
        testCapability.getTestSuites().add(testSuite);
        testResult.getTestCapabilities().add(testCapability);
        return testResult;
    }

    public TestResult getAuditSKIPTestResult() {
        TestResult testResult = new TestResult();
        testResult.setType(TestSuiteType.Functional);
        TestCapability testCapability = new TestCapability();
        TestSuite testSuite = new TestSuite();
        testSuite.setSuccessTestCaseCount(0);
        testSuite.setFailedTestCaseCount(0);
        testSuite.setSkippedTestCaseCount(40);
        testSuite.setTotalTestCaseCount(40);
        testCapability.getTestSuites().add(testSuite);
        testResult.getTestCapabilities().add(testCapability);
        return testResult;
    }

    public Dashboard getDashboard() {
        Dashboard dashboard = new Dashboard("Template1", "Title1", null, null, DashboardType.Team,
                "ASV1", "BAP1", null, false, null);
        return dashboard;
    }

    private ApiSettings getSettings(){
        ApiSettings settings = new ApiSettings();
        settings.setTestResultSuccessPriority("Low");
        settings.setTestResultFailurePriority("High");
        settings.setTestResultSkippedPriority("High");
        settings.setTestResultThreshold(95.0);
        settings.setFeatureTestResultThreshold("100.0");
        return settings;
    }

    public CollectorItem getDummyCollectorItem(){
        CollectorItem collectorItem = new CollectorItem();
        return collectorItem;
    }

    public Map<String, String> getDataMap(){
        Map<String, String> data = new HashMap<>();
        data.put("identifierVersion", "1.0");
        return data;
    }
}
