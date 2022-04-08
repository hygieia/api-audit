package com.capitalone.dashboard.evaluator;

import com.capitalone.dashboard.ApiSettings;
import com.capitalone.dashboard.misc.HygieiaException;
import com.capitalone.dashboard.model.*;
import com.capitalone.dashboard.repository.CollectorItemRepository;
import com.capitalone.dashboard.repository.TestResultRepository;
import com.capitalone.dashboard.request.ArtifactAuditRequest;
import com.capitalone.dashboard.response.TestResultsAuditResponse;
import com.capitalone.dashboard.status.TestResultAuditStatus;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class FeatureTestResultEvaluator extends Evaluator<TestResultsAuditResponse>{

    private final TestResultRepository testResultRepository;
    private static final Logger LOGGER = LoggerFactory.getLogger(FeatureTestResultEvaluator.class);

    private Dashboard dashboard;
    public static final String FUNCTIONAL = "Functional";
    public static final String ARTIFACT_NAME = "artifactName";
    public static final String ARTIFACT_VERSION = "artifactVersion";
    private static final String SUCCESS_COUNT = "successCount";
    private static final String FAILURE_COUNT = "failureCount";
    private static final String SKIP_COUNT = "skippedCount";
    private static final String TOTAL_COUNT = "totalCount";
    private static final String TEST_CASE_SUCCESS_COUNT = "successTestCaseCount";
    private static final String TEST_CASE_FAILURE_COUNT = "failureTestCaseCount";
    private static final String TEST_CASE_SKIPPED_COUNT = "skippedTestCaseCount";
    private static final String TEST_CASE_TOTAL_COUNT = "totalTestCaseCount";



    @Autowired
    public FeatureTestResultEvaluator(TestResultRepository testResultRepository, CollectorItemRepository collectorItemRepository) {
        this.testResultRepository = testResultRepository;
        this.collectorItemRepository = collectorItemRepository;
    }


    @Override
    public Collection<TestResultsAuditResponse> evaluate(Dashboard dashboard, long beginDate, long endDate, Map<?, ?> data, String altIdentifier, String identifierName) throws AuditException {
        this.dashboard = getDashboard(dashboard.getTitle(), DashboardType.Team);
        Map<String, Object> collItemOptions = new HashMap<>();
        collItemOptions.put("artifactName", identifierName);
        collItemOptions.put("artifactVersion", data.get("identifierVersion"));
        CollectorItem testItem = getCollectorItemForIdentifierVersion(this.dashboard, collItemOptions);
        if (testItem == null) {
            throw new AuditException("No tests configured", AuditException.NO_COLLECTOR_ITEM_CONFIGURED);
        }

        Collection<TestResultsAuditResponse> testResultsAuditResponseCollection = new ArrayList<>();

        List<TestResult> testResultList = testResultRepository.findByCollectorItemIdAndTimestampIsBetweenOrderByTimestampDesc(testItem.getId(), 0, System.currentTimeMillis());
        TestResultsAuditResponse testResultsAuditResponse = new TestResultsAuditResponse();
        testResultsAuditResponse.setAuditEntity(testItem.getOptions());
        testResultsAuditResponse.setLastUpdated(testItem.getLastUpdated());

        // If no test results, set status to TEST_RESULT_MISSING and return
        if (CollectionUtils.isEmpty(testResultList)){
            testResultsAuditResponse.addAuditStatus(TestResultAuditStatus.TEST_RESULT_MISSING);
            testResultsAuditResponseCollection.add(testResultsAuditResponse);
            return testResultsAuditResponseCollection;
        }

        Double featureTestThreshold;
        try{
            featureTestThreshold = Double.parseDouble((String)data.get("featureTestThreshold"));
        }catch(NumberFormatException e){
            LOGGER.warn("Could not parse double from featureTestThreshold. Setting to default value of 90%");
            featureTestThreshold = 100.0;
        }

        // Value needs to be final to be used in lambda
        Double threshold = featureTestThreshold;

        testResultList.forEach(testResult -> testResultsAuditResponseCollection.add(getFeatureTestResultAudit(testResultsAuditResponse ,testResult, threshold)));


        return testResultsAuditResponseCollection;
    }

    protected TestResultsAuditResponse getFeatureTestResultAudit(TestResultsAuditResponse baseTestResultsAuditResponse, TestResult testResult, Double threshold){
        TestResultsAuditResponse testResultsAuditResponse = new TestResultsAuditResponse();
        testResultsAuditResponse.setAuditEntity(baseTestResultsAuditResponse.getAuditEntity());
        testResultsAuditResponse.setLastUpdated(baseTestResultsAuditResponse.getLastUpdated());
        testResultsAuditResponse.setBuildArtifact(testResult.getBuildArtifact());
        testResultsAuditResponse.setLastExecutionTime(testResult.getStartTime());
        testResultsAuditResponse.setType(testResult.getType().toString());
        testResultsAuditResponse.setFeatureTestResult(getFeatureTestResult(testResult));

        List<TestCapability> testCapabilities = testResult.getTestCapabilities().stream().collect(Collectors.toList());
        testResultsAuditResponse = updateTestResultAuditStatuses(testCapabilities, testResultsAuditResponse, threshold);

        // Clearing for readability in response
        for(TestCapability test: testCapabilities){
            test.setTestSuites(null);
        }
        testResultsAuditResponse.setTestCapabilities(testCapabilities);
        return testResultsAuditResponse;
    }

    /**
     * update test result audit statuses
     * @param testCapabilities
     * @param testResultsAuditResponse
     * @return
     */
    private TestResultsAuditResponse updateTestResultAuditStatuses(List<TestCapability> testCapabilities, TestResultsAuditResponse testResultsAuditResponse, double threshold) {



//        if(isAllTestCasesSkipped(testCapabilities)){
//            testResultsAuditResponse.addAuditStatus(TestResultAuditStatus.TEST_RESULT_SKIPPED);
//            return testResultsAuditResponse;
//        }
        double testCasePassPercent = this.getTestCasePassPercent(testCapabilities);
        if (testCasePassPercent < threshold) {
            testResultsAuditResponse.addAuditStatus(TestResultAuditStatus.TEST_RESULT_AUDIT_FAIL);
        } else {
            testResultsAuditResponse.addAuditStatus(TestResultAuditStatus.TEST_RESULT_AUDIT_OK);
        }

        return testResultsAuditResponse;
    }



    /**
     * Get test result pass percent
     * @param testCapabilities
     * @return
     */
    private double getTestCasePassPercent(List<TestCapability> testCapabilities) {
        double testCaseSuccessCount = testCapabilities.stream().mapToDouble(testCapability ->
                testCapability.getTestSuites().parallelStream().mapToDouble(TestSuite::getSuccessTestCaseCount).sum()
        ).sum();
        double totalTestCaseCount = testCapabilities.stream().mapToDouble(testCapability ->
                testCapability.getTestSuites().parallelStream().mapToDouble(TestSuite::getTotalTestCaseCount).sum()
        ).sum();

        return (testCaseSuccessCount/totalTestCaseCount) * 100;
    }

    /**
     * Builds feature test result data map
     * @param testResult
     * @return featureTestResultMap
     */
    protected HashMap getFeatureTestResult(TestResult testResult) {
        HashMap<String,Integer> featureTestResultMap = new HashMap<>();
        featureTestResultMap.put(SUCCESS_COUNT, testResult.getSuccessCount());
        featureTestResultMap.put(FAILURE_COUNT, testResult.getFailureCount());
        featureTestResultMap.put(SKIP_COUNT, testResult.getSkippedCount());
        featureTestResultMap.put(TOTAL_COUNT,testResult.getTotalCount());

        Collection<TestCapability> testCapabilities = testResult.getTestCapabilities();
        int totalTestCaseCount = testCapabilities.stream().mapToInt(testCapability ->
                testCapability.getTestSuites().parallelStream().mapToInt(TestSuite::getTotalTestCaseCount).sum()).sum();
        int testCaseSuccessCount = testCapabilities.stream().mapToInt(testCapability ->
                testCapability.getTestSuites().parallelStream().mapToInt(TestSuite::getSuccessTestCaseCount).sum()).sum();
        int testCaseFailureCount = testCapabilities.stream().mapToInt(testCapability ->
                testCapability.getTestSuites().parallelStream().mapToInt(TestSuite::getFailedTestCaseCount).sum()).sum();
        int testCaseSkippedCount = testCapabilities.stream().mapToInt(testCapability ->
                testCapability.getTestSuites().parallelStream().mapToInt(TestSuite::getSkippedTestCaseCount).sum()).sum();

        featureTestResultMap.put(TEST_CASE_TOTAL_COUNT, totalTestCaseCount);
        featureTestResultMap.put(TEST_CASE_SUCCESS_COUNT, testCaseSuccessCount);
        featureTestResultMap.put(TEST_CASE_FAILURE_COUNT, testCaseFailureCount);
        featureTestResultMap.put(TEST_CASE_SKIPPED_COUNT, testCaseSkippedCount);

        return featureTestResultMap;
    }

    protected CollectorItem getCollectorItemForIdentifierVersion(Dashboard dashboard, Map<String, Object> collItemOptions) {
        List<CollectorItem> testItems = getCollectorItems(dashboard, CollectorType.Test, FUNCTIONAL);
        for(CollectorItem testItem : testItems){
            if(isEqualsIdentifierName(testItem, (String) collItemOptions.get(ARTIFACT_NAME)) && isEqualsIdentifierVersion(testItem, (String) collItemOptions.get(ARTIFACT_VERSION))){
               CollectorItem testCollItem = testItem;
               return testCollItem;
            }
        }
        LOGGER.warn("No feature test collector items found for this artifact name/version combination.");
        return null;
    }


    private boolean isEqualsIdentifierName(CollectorItem testItem, String identifierName) {
        return (Objects.nonNull(identifierName) && Objects.nonNull(testItem.getOptions())) ? identifierName.equalsIgnoreCase((String)testItem.getOptions().get(ARTIFACT_NAME)) : false;
    }

    private boolean isEqualsIdentifierVersion(CollectorItem testItem, String identifierVersion){
        return (Objects.nonNull(identifierVersion) && Objects.nonNull(testItem.getOptions())) ? identifierVersion.equalsIgnoreCase((String)testItem.getOptions().get(ARTIFACT_VERSION)) : false;
    }


    @Override
    public Collection<TestResultsAuditResponse> evaluateNextGen(ArtifactAuditRequest artifactAuditRequest, Dashboard dashboard, long beginDate, long endDate, Map<?, ?> data) throws AuditException {
        return null;
    }

    @Override
    public TestResultsAuditResponse evaluate(CollectorItem collectorItem, long beginDate, long endDate, Map<?, ?> data) throws AuditException, HygieiaException {
        return null;
    }

    /**
     * Get dashboard by title and type
     * @param title
     * @param dashboardType
     * @return
     */
    protected Dashboard getDashboard(String title, DashboardType dashboardType) {
        return dashboardRepository.findByTitleAndType(title, dashboardType);
    }

    public void setSettings(ApiSettings settings) {
        this.settings = settings;
    }

}
