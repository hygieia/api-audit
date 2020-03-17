package com.capitalone.dashboard.evaluator;

import com.capitalone.dashboard.ApiSettings;
import com.capitalone.dashboard.model.Build;
import com.capitalone.dashboard.model.BuildStage;
import com.capitalone.dashboard.model.BuildStatus;
import com.capitalone.dashboard.model.CollectionError;
import com.capitalone.dashboard.model.CollectorItem;
import com.capitalone.dashboard.model.Collector;
import com.capitalone.dashboard.model.CollectorType;
import com.capitalone.dashboard.repository.BuildRepository;
import com.capitalone.dashboard.repository.CollectorItemRepository;
import com.capitalone.dashboard.repository.CollectorRepository;
import com.capitalone.dashboard.response.DeployAuditResponse;
import org.bson.types.ObjectId;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)

public class DeployEvaluatorTest {
    @InjectMocks
    private DeployEvaluator deployEvaluator;
    @Mock
    private BuildRepository buildRepository;
    @Mock
    private CollectorRepository collectorRepository;
    @Mock
    private CollectorItemRepository collectorItemRepository;
    @Mock
    private ApiSettings apiSettings;

    private DeployAuditResponse response;


    @Test
    public void test_MatchStage_Match() {
        List<String> patterns = new ArrayList<>();
        patterns.add("(?i:.*Deploy)");
        when(apiSettings.getBuildStageRegEx()).thenReturn(patterns);

        List<String> stages = new ArrayList<>();
        stages.add("Deploy");
        boolean res = deployEvaluator.matchStage(setBuildStages(stages, "success"), "success", apiSettings);

        List<String> stagesVariation = new ArrayList<>();
        stagesVariation.add("Deploy Somewhere");
        stagesVariation.add("Finished Deploy");
        stagesVariation.add("Finished Deploy Somewhere");
        boolean resVariation = deployEvaluator.matchStage(setBuildStages(stagesVariation, "success"), "success", apiSettings);

        List<String> stagesPrefix = new ArrayList<>();
        stagesPrefix.add("Pre-Deploy Validation");
        stagesPrefix.add("filler");
        boolean resPrefix = deployEvaluator.matchStage(setBuildStages(stagesPrefix, "success"), "success", apiSettings);

        List<String> stagesSuffix1 = new ArrayList<>();
        stagesSuffix1.add("Sample Deployment Environment");
        stagesSuffix1.add("filler");
        boolean resSuffix1 = deployEvaluator.matchStage(setBuildStages(stagesSuffix1, "success"), "success", apiSettings);

        List<String> stagesSuffix2 = new ArrayList<>();
        stagesSuffix2.add("Deploying Image to environment");
        stagesSuffix2.add("filler");
        boolean resSuffix2 = deployEvaluator.matchStage(setBuildStages(stagesSuffix2, "success"), "success", apiSettings);

        Assert.assertEquals(true, res);
        Assert.assertEquals(true, resVariation);
        Assert.assertEquals(true, resPrefix);
        Assert.assertEquals(true, resSuffix1);
        Assert.assertEquals(true, resSuffix2);
    }

    @Test
    public void test_MatchStage_NoMatch() {
        List<String> patterns = new ArrayList<>();
        patterns.add("(?i:.*Deploy)");
        when(apiSettings.getBuildStageRegEx()).thenReturn(patterns);

        List<String> stages = new ArrayList<>();
        stages.add("ploy");
        stages.add("dep");
        stages.add("deplo");
        stages.add("dev any");

        boolean res = deployEvaluator.matchStage(setBuildStages(stages, "success"), "success", apiSettings);
        Assert.assertEquals(false, res);
    }

    @Test
    public void testEvaluate_CollectorItemError_Deploy_NULL() {
        when(collectorRepository.findByName(anyString())).thenReturn(getBuildCollector());
        when(collectorItemRepository.findAllByOptionMapAndCollectorIdsIn(any(Map.class),any(List.class))).thenReturn(null);
        response = deployEvaluator.evaluate(getCollectorItem("testGenericItem", "/test", false), 125634536, 6235263, null);
        Assert.assertEquals(true, response.getAuditStatuses().toString().contains("NO_ACTIVITY"));
        Assert.assertEquals(true,response.getAuditEntity().toString().contains("jobUrl"));
    }

    @Test
    public void test_Evaluate_NoActivity() {
        when(collectorRepository.findByName(anyString())).thenReturn(getBuildCollector());
        when(collectorItemRepository.findAllByOptionMapAndCollectorIdsIn(any(Map.class),any(List.class))).thenReturn(Arrays.asList(getCollectorItem("testGenericItem", "/test", false)));
        when(buildRepository.findTop1ByCollectorItemIdAndTimestampIsBetweenOrderByTimestampDesc(any(ObjectId.class), any(Long.class), any(Long.class))).thenReturn(null);
        response = deployEvaluator.evaluate(getCollectorItem("testGenericItem", "/test", false), 125634536, 125634538, null);
        Assert.assertEquals(true, response.getAuditStatuses().toString().contains("NO_ACTIVITY"));
        verify(buildRepository, times(1)).findTop1ByCollectorItemIdAndTimestampIsBetweenOrderByTimestampDesc(any(ObjectId.class), any(Long.class), any(Long.class));
        Assert.assertEquals(true,response.getAuditEntity().toString().contains("jobUrl"));
    }

    @Test
    public void test_Evaluate_CollectorItem_Latest() {
        CollectorItem c1 = getCollectorItem("testGenericItemPrev", "/test/prev", false);
        CollectorItem c2 = getCollectorItem("testGenericItemLatest", "/test/latest", false);
        c2.setLastUpdated(125634538);
        when(collectorRepository.findByName(anyString())).thenReturn(getBuildCollector());
        when(collectorItemRepository.findAllByOptionMapAndCollectorIdsIn(any(Map.class),any(List.class))).thenReturn(Arrays.asList(c1, c2));
        when(buildRepository.findTop1ByCollectorItemIdAndTimestampIsBetweenOrderByTimestampDesc(any(ObjectId.class), any(Long.class), any(Long.class))).thenReturn(null);
        response = deployEvaluator.evaluate(getCollectorItem("testGenericItem", "/test", false), 125634536, 125634538, null);
        Assert.assertEquals(true, response.getAuditEntity().get("jobName").equals(c2.getOptions().get("jobName")));
        Assert.assertEquals(true, response.getAuditEntity().get("jobUrl").equals(c2.getOptions().get("jobUrl")));
    }

    @Test
    public void test_Evaluate_Deploy_Scripts_Found_Tested() {
        when(collectorRepository.findByName(anyString())).thenReturn(getBuildCollector());
        when(collectorItemRepository.findAllByOptionMapAndCollectorIdsIn(any(Map.class),any(List.class))).thenReturn(Arrays.asList(getCollectorItem("testGenericItem", "/test", false)));
        when(buildRepository.findTop1ByCollectorItemIdAndTimestampIsBetweenOrderByTimestampDesc(any(ObjectId.class), any(Long.class), any(Long.class))).thenReturn(getBuild(BuildStatus.Success, "success"));

        List<String> patterns = new ArrayList<>();
        patterns.add("(?i:.*any)");
        when(apiSettings.getBuildStageRegEx()).thenReturn(patterns);

        response = deployEvaluator.evaluate(getCollectorItem("testGenericItem", "/test", false), 125634536, 125634538, null);
        Assert.assertEquals(true, response.getAuditStatuses().toString().contains("DEPLOY_SCRIPTS_FOUND_TESTED"));
        verify(buildRepository, times(1)).findTop1ByCollectorItemIdAndTimestampIsBetweenOrderByTimestampDesc(any(ObjectId.class), any(Long.class), any(Long.class));
        Assert.assertEquals(true,response.getAuditEntity().toString().contains("jobUrl"));
    }

    @Test
    public void test_Evaluate_Deploy_Scripts_Found_Tested_Extend() {
        when(collectorRepository.findByName(anyString())).thenReturn(getBuildCollector());
        when(collectorItemRepository.findAllByOptionMapAndCollectorIdsIn(any(Map.class),any(List.class))).thenReturn(Arrays.asList(getCollectorItem("testGenericItem", "/test", false)));
        when(buildRepository.findTop1ByCollectorItemIdAndTimestampIsBetweenOrderByTimestampDesc(any(ObjectId.class), any(Long.class), any(Long.class))).thenReturn(getBuild(BuildStatus.Success, "success"));

        List<String> patterns = new ArrayList<>();
        patterns.add("(?i:.*Deploy)");
        patterns.add(("(?i:.*filler)"));
        when(apiSettings.getBuildStageRegEx()).thenReturn(patterns);

        response = deployEvaluator.evaluate(getCollectorItem("testGenericItem", "/test", false), 125634536, 125634538, null);
        Assert.assertEquals(true, response.getAuditStatuses().toString().contains("DEPLOY_SCRIPTS_FOUND_TESTED"));
        verify(buildRepository, times(1)).findTop1ByCollectorItemIdAndTimestampIsBetweenOrderByTimestampDesc(any(ObjectId.class), any(Long.class), any(Long.class));
        Assert.assertEquals(true,response.getAuditEntity().toString().contains("jobUrl"));

    }

    @Test
    public void test_Evaluate_Deploy_Scripts_Found_Non_Tested() {
        when(collectorRepository.findByName(anyString())).thenReturn(getBuildCollector());
        when(collectorItemRepository.findAllByOptionMapAndCollectorIdsIn(any(Map.class),any(List.class))).thenReturn(Arrays.asList(getCollectorItem("testGenericItem", "/test", false)));
        when(buildRepository.findTop1ByCollectorItemIdAndTimestampIsBetweenOrderByTimestampDesc(any(ObjectId.class), any(Long.class), any(Long.class))).thenReturn(getBuild(BuildStatus.Failure, "failed"));

        List<String> patterns = new ArrayList<>();
        patterns.add("(?i:.*any)");
        when(apiSettings.getBuildStageRegEx()).thenReturn(patterns);

        response = deployEvaluator.evaluate(getCollectorItem("testGenericItem", "/test", false), 125634536, 125634538, null);
        Assert.assertEquals(true, response.getAuditStatuses().toString().contains("DEPLOY_SCRIPTS_FOUND_NOT_TESTED"));
        verify(buildRepository, times(1)).findTop1ByCollectorItemIdAndTimestampIsBetweenOrderByTimestampDesc(any(ObjectId.class), any(Long.class), any(Long.class));
        Assert.assertEquals(true,response.getAuditEntity().toString().contains("jobUrl"));

    }

    @Test
    public void test_Evaluate_Deploy_Scripts_Found_Non_Tested_Extend() {
        when(collectorRepository.findByName(anyString())).thenReturn(getBuildCollector());
        when(collectorItemRepository.findAllByOptionMapAndCollectorIdsIn(any(Map.class),any(List.class))).thenReturn(Arrays.asList(getCollectorItem("testGenericItem", "/test", false)));
        when(buildRepository.findTop1ByCollectorItemIdAndTimestampIsBetweenOrderByTimestampDesc(any(ObjectId.class), any(Long.class), any(Long.class))).thenReturn(getBuild(BuildStatus.Failure, "failed"));

        List<String> patterns = new ArrayList<>();
        patterns.add("(?i:.*Deploy)");
        patterns.add("(?i:.*filler)");
        when(apiSettings.getBuildStageRegEx()).thenReturn(patterns);

        response = deployEvaluator.evaluate(getCollectorItem("testGenericItem", "/test", false), 125634536, 125634538, null);
        Assert.assertEquals(true, response.getAuditStatuses().toString().contains("DEPLOY_SCRIPTS_FOUND_NOT_TESTED"));
        verify(buildRepository, times(1)).findTop1ByCollectorItemIdAndTimestampIsBetweenOrderByTimestampDesc(any(ObjectId.class), any(Long.class), any(Long.class));
        Assert.assertEquals(true,response.getAuditEntity().toString().contains("jobUrl"));

    }

    @Test
    public void test_Evaluate_Deploy_Scripts_Tests_Not_Found() {
        when(collectorRepository.findByName(anyString())).thenReturn(getBuildCollector());
        when(collectorItemRepository.findAllByOptionMapAndCollectorIdsIn(any(Map.class),any(List.class))).thenReturn(Arrays.asList(getCollectorItem("testGenericItem", "/test", false)));
        when(buildRepository.findTop1ByCollectorItemIdAndTimestampIsBetweenOrderByTimestampDesc(any(ObjectId.class), any(Long.class), any(Long.class))).thenReturn(getBuild(BuildStatus.Failure, "failed"));

        List<String> patterns = new ArrayList<>();
        patterns.add("(?i:.*error)");
        when(apiSettings.getBuildStageRegEx()).thenReturn(patterns);

        response = deployEvaluator.evaluate(getCollectorItem("testGenericItem", "/test", false), 125634536, 125634538, null);
        Assert.assertEquals(true, response.getAuditStatuses().toString().contains("DEPLOYMENT_SCRIPTS_TEST_NOT_FOUND"));
        verify(buildRepository, times(1)).findTop1ByCollectorItemIdAndTimestampIsBetweenOrderByTimestampDesc(any(ObjectId.class), any(Long.class), any(Long.class));
        Assert.assertEquals(true,response.getAuditEntity().toString().contains("jobUrl"));

    }

    private CollectorItem getCollectorItem(String jobName, String jobUrl, boolean isError) {
        CollectorItem ci = new CollectorItem();
        ci.setLastUpdated(125634537);
        ci.getOptions().put("jobName", jobName);
        ci.getOptions().put("jobUrl", jobUrl);
        ci.getOptions().put("instanceUrl", "http://jenkins.com/");
        if (isError) {
            ci.getErrors().add(new CollectionError("404", "Service Unavailable"));
        }
        return ci;
    }

    private Build getBuild(BuildStatus status, String stageStatus) {
        Build build = new Build();
        build.setBuildStatus(status);
        build.setStages(getBuildStages(stageStatus));
        return build;
    }

    private List<BuildStage> getBuildStages(String status) {
        return Arrays.asList("DEV ANY", "TEST", "PROD",
                "Deploy",
                "Deploy Somewhere",
                "Finished Deploy Somewhere",
                "Pre-Deploy Validation",
                "Sample Deployment Environment",
                "Deploying Image to environment",
                "Call This to deploy",
                "Get Data",
                "Test Execution",
                "some test",
                "dep",
                "ploy"
        ).stream().map(env -> getBuildStage(env, status)).collect(Collectors.toList());
    }

    // for testing different variations of stage groups
    private List<BuildStage> setBuildStages(List<String> stages, String status) {
        return stages.stream().map(env -> getBuildStage(env, status)).collect(Collectors.toList());
    }

    private BuildStage getBuildStage(String name, String status) {
        BuildStage stage = new BuildStage();
        stage.setName(name);
        stage.setStatus(status);
        return stage;
    }

    private Collector getBuildCollector() {
        return new Collector("Hudson", CollectorType.Build);
    }
}
