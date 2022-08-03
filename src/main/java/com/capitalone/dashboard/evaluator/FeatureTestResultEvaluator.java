package com.capitalone.dashboard.evaluator;

import com.capitalone.dashboard.ApiSettings;
import com.capitalone.dashboard.misc.HygieiaException;
import com.capitalone.dashboard.model.AuditException;
import com.capitalone.dashboard.model.CollectorItem;
import com.capitalone.dashboard.model.CollectorType;
import com.capitalone.dashboard.model.Dashboard;
import com.capitalone.dashboard.model.TestCapability;
import com.capitalone.dashboard.model.TestResult;
import com.capitalone.dashboard.model.TestSuite;
import com.capitalone.dashboard.repository.CollectorItemRepository;
import com.capitalone.dashboard.repository.TestResultRepository;
import com.capitalone.dashboard.request.ArtifactAuditRequest;
import com.capitalone.dashboard.response.TestResultsAuditResponse;
import com.capitalone.dashboard.status.TestResultAuditStatus;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class FeatureTestResultEvaluator extends Evaluator<TestResultsAuditResponse>{

    private final TestResultRepository testResultRepository;
    private static final Logger LOGGER = LoggerFactory.getLogger(FeatureTestResultEvaluator.class);

    public static final String FUNCTIONAL = "Functional";
    public static final String ARTIFACT_NAME = "artifactName";
    public static final String ARTIFACT_VERSION = "artifactVersion";
    public static final String IDENTIFIER_VERSION = "identifierVersion";
    private static final String SUCCESS_COUNT = "successCount";
    private static final String FAILURE_COUNT = "failureCount";
    private static final String SKIP_COUNT = "skippedCount";
    private static final String TOTAL_COUNT = "totalCount";
    private static final String TEST_CASE_SUCCESS_COUNT = "successTestCaseCount";
    private static final String TEST_CASE_FAILURE_COUNT = "failureTestCaseCount";
    private static final String TEST_CASE_SKIPPED_COUNT = "skippedTestCaseCount";
    private static final String TEST_CASE_UNKNOWN_COUNT = "unknownStatusTestCaseCount";
    private static final String TEST_CASE_TOTAL_COUNT = "totalTestCaseCount";

    private static final String PRIORITY_HIGH = "High";



    @Autowired
    public FeatureTestResultEvaluator(TestResultRepository testResultRepository, CollectorItemRepository collectorItemRepository) {
        this.testResultRepository = testResultRepository;
        this.collectorItemRepository = collectorItemRepository;
    }


    @Override
    public Collection<TestResultsAuditResponse> evaluate(Dashboard dashboard, long beginDate, long endDate, Map<?, ?> data, String altIdentifier, String identifierName) throws AuditException {
        Map<String, Object> collItemOptions = new HashMap<>();

        if (StringUtils.isEmpty((String)data.get(IDENTIFIER_VERSION)) || StringUtils.isEmpty(identifierName)){
            throw new AuditException("identifierVersion or identifierName missing.", AuditException.MISSING_DETAILS);
        }

        collItemOptions.put(ARTIFACT_NAME, identifierName);
        collItemOptions.put(ARTIFACT_VERSION, data.get(IDENTIFIER_VERSION));

        CollectorItem testItem = getCollectorItemForIdentifierVersion(dashboard, collItemOptions);
        if (testItem == null) {
            throw new AuditException("No tests configured", AuditException.NO_COLLECTOR_ITEM_CONFIGURED);
        }


        Double featureTestThreshold;
        try{
            try{
                featureTestThreshold = Double.parseDouble((String)data.get("featureTestThreshold"));
            }catch(NumberFormatException | NullPointerException e){
                LOGGER.error("Could not parse double from featureTestThreshold. Setting to default value.");
                LOGGER.error(e.toString());
                featureTestThreshold = Double.parseDouble(settings.getFeatureTestResultThreshold());
            }
        }catch(Exception e){
            throw new AuditException("testingThreshold unavailable. Cannot perform audit.", AuditException.BAD_INPUT_DATA);
        }


        // Value needs to be final to be used in lambda
        Double threshold = featureTestThreshold;

        Collection<TestResultsAuditResponse> testResultsAuditResponseCollection = new ArrayList<>();

        List<TestResult> testResultList = testResultRepository.findByCollectorItemIdAndTimestampIsBetweenOrderByTimestampDesc(testItem.getId(), beginDate, endDate);
        TestResultsAuditResponse testResultsAuditResponse = new TestResultsAuditResponse();
        Map<String, Object> auditEntity = new HashMap<>();
        auditEntity.putAll(testItem.getOptions());
        auditEntity.put("testingThreshold", threshold);
        testResultsAuditResponse.setAuditEntity(auditEntity);
        testResultsAuditResponse.setLastUpdated(testItem.getLastUpdated());
        testResultsAuditResponse.setCollectorItemId(testItem.getId());

        // If no test results, set status to TEST_RESULT_MISSING and return
        if (CollectionUtils.isEmpty(testResultList)){
            testResultsAuditResponse.addAuditStatus(TestResultAuditStatus.TEST_RESULT_MISSING);
            testResultsAuditResponseCollection.add(testResultsAuditResponse);
            return testResultsAuditResponseCollection;
        }


        testResultList.forEach(testResult -> testResultsAuditResponseCollection.add(getFeatureTestResultAudit(testResultsAuditResponse ,testResult, threshold)));


        return testResultsAuditResponseCollection;
    }

    protected TestResultsAuditResponse getFeatureTestResultAudit(TestResultsAuditResponse baseTestResultsAuditResponse, TestResult testResult, Double threshold){
        TestResultsAuditResponse testResultsAuditResponse = new TestResultsAuditResponse();
        testResultsAuditResponse.setAuditEntity(baseTestResultsAuditResponse.getAuditEntity());
        testResultsAuditResponse.setLastUpdated(baseTestResultsAuditResponse.getLastUpdated());
        testResultsAuditResponse.setCollectorItemId(baseTestResultsAuditResponse.getCollectorItemId());
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
     * Updates test result audit statuses based on percentage of test cases (not suites) that exceed the threshold.
     * @param testCapabilities List of testCapability attached to the testResult being processed
     * @param testResultsAuditResponse the audit response that the audit statuses will be added to
     * @param threshold testingThreshold from Bladerunner
     * @return TestResultsAuditResponse updated with statuses
     */
    private TestResultsAuditResponse updateTestResultAuditStatuses(List<TestCapability> testCapabilities, TestResultsAuditResponse testResultsAuditResponse, double threshold) {
        if(isAllTestCasesSkipped(testCapabilities)){
            testResultsAuditResponse.addAuditStatus(TestResultAuditStatus.TEST_RESULT_SKIPPED);
            return testResultsAuditResponse;
        }

        double testCasePassPercent = this.getTestCasePassPercent(testCapabilities);

        if (testCasePassPercent < threshold) {
            testResultsAuditResponse.addAuditStatus(TestResultAuditStatus.TEST_RESULT_AUDIT_FAIL);
        } else {
            testResultsAuditResponse.addAuditStatus(TestResultAuditStatus.TEST_RESULT_AUDIT_OK);
        }

        return testResultsAuditResponse;
    }



    /**
     * Get test case pass percent for a given set of test capabilities.
     * @param testCapabilities List of TestCapability attached to testResult being processed
     * @return testCasePassPercent Double representing percentage of test cases that passed for given List of test capabilities
     */
    private double getTestCasePassPercent(List<TestCapability> testCapabilities) {
        try{
            double testCaseSuccessCount = testCapabilities.stream().mapToDouble(testCapability ->
                    testCapability.getTestSuites().parallelStream().mapToDouble(TestSuite::getSuccessTestCaseCount).sum()
            ).sum();
            double totalTestCaseCount = testCapabilities.stream().mapToDouble(testCapability ->
                    testCapability.getTestSuites().parallelStream().mapToDouble(TestSuite::getTotalTestCaseCount).sum()
            ).sum();
            double testCaseSkipCount = testCapabilities.stream().mapToDouble(testCapability ->
                    testCapability.getTestSuites().parallelStream().mapToDouble(TestSuite::getSkippedTestCaseCount).sum()
            ).sum();
            double testCaseUnkownCount = testCapabilities.stream().mapToDouble(testCapability ->
                    testCapability.getTestSuites().parallelStream().mapToDouble(TestSuite::getUnknownStatusCount).sum()
            ).sum();

            if(totalTestCaseCount == 0 || (testCaseSkipCount + testCaseUnkownCount) == totalTestCaseCount){
                return 100.0;
            }

            return (testCaseSuccessCount/(totalTestCaseCount - testCaseSkipCount - testCaseUnkownCount)) * 100;

        }catch(Exception e){
            LOGGER.error("Could not get 'testCasePassPercent', setting to 0.0%");
            return 0.0;
        }
    }

    /**
     * Builds feature test result data map
     */
    protected HashMap getFeatureTestResult(TestResult testResult) {
        HashMap<String,Integer> featureTestResultMap = new HashMap<>();

        //If an exception occurs an empty map will be returned.
        try{
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
            int testCaseUnknownCount = testCapabilities.stream().mapToInt(testCapability ->
                    testCapability.getTestSuites().parallelStream().mapToInt(TestSuite::getUnknownStatusCount).sum()).sum();

            featureTestResultMap.put(TEST_CASE_TOTAL_COUNT, totalTestCaseCount);
            featureTestResultMap.put(TEST_CASE_SUCCESS_COUNT, testCaseSuccessCount);
            featureTestResultMap.put(TEST_CASE_FAILURE_COUNT, testCaseFailureCount);
            featureTestResultMap.put(TEST_CASE_SKIPPED_COUNT, testCaseSkippedCount);
            featureTestResultMap.put(TEST_CASE_UNKNOWN_COUNT, testCaseUnknownCount);

        }catch(Exception e){
            LOGGER.error("Exception occurred while processing testResult " + testResult.getDescription(), e);
        }


        return featureTestResultMap;
    }

    /**
     * Gets the collector item of type TEST associated with the given artifact name/version
     * @return testCollectorItem
     */
    protected CollectorItem getCollectorItemForIdentifierVersion(Dashboard dashboard, Map<String, Object> collItemOptions) {
        List<CollectorItem> testItems = getCollectorItems(dashboard, CollectorType.Test, FUNCTIONAL);
        // Sort so that the most recent results are audited
        testItems.sort(Comparator.comparing(CollectorItem::getUpsertTime).reversed());
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


    public void setSettings(ApiSettings settings) {
        this.settings = settings;
    }

    /**
     * Check if all the test cases are skipped
     * @param testCapabilities set of testCapabilities attached to the testResult being reviewed
     * @return boolean: true if all tests were skipped, false otherwise
     */
    public boolean isAllTestCasesSkipped(List<TestCapability> testCapabilities) {
        try{
            int totalTestCaseCount = testCapabilities.stream().mapToInt(testCapability ->
                    testCapability.getTestSuites().parallelStream().mapToInt(TestSuite::getTotalTestCaseCount).sum()
            ).sum();
            int testCaseSkippedCount = testCapabilities.stream().mapToInt(testCapability ->
                    testCapability.getTestSuites().parallelStream().mapToInt(TestSuite::getSkippedTestCaseCount).sum()
            ).sum();

            boolean isSkippedHighPriority = settings.getTestResultSkippedPriority().equalsIgnoreCase(PRIORITY_HIGH);

            if ((testCaseSkippedCount >= totalTestCaseCount) && isSkippedHighPriority){
                return true;
            }
            return false;
        }catch(Exception e){
            LOGGER.warn("Error during isAllTestCasesSkipped call", e);
            return false;

        }
    }

}
