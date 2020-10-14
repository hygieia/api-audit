package com.capitalone.dashboard.evaluator;

import com.capitalone.dashboard.ApiSettings;
import com.capitalone.dashboard.model.AuditException;
import com.capitalone.dashboard.model.BinaryArtifact;
import com.capitalone.dashboard.model.CollectorItem;
import com.capitalone.dashboard.model.CollectorType;
import com.capitalone.dashboard.model.Dashboard;
import com.capitalone.dashboard.repository.BinaryArtifactRepository;
import com.capitalone.dashboard.request.ArtifactAuditRequest;
import com.capitalone.dashboard.response.ArtifactAuditResponse;
import com.capitalone.dashboard.status.ArtifactAuditStatus;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class ArtifactEvaluator extends Evaluator<ArtifactAuditResponse> {

    private final BinaryArtifactRepository binaryArtifactRepository;
    private final ApiSettings apiSettings;
    private final static String DOCKER = "docker";
    private final static String ARTIFACT_NAME = "artifactName";
    private final static String PATH = "path";
    private final static String REPO_NAME = "repoName";

    @Autowired
    public ArtifactEvaluator(BinaryArtifactRepository binaryArtifactRepository, ApiSettings apiSettings) {
        this.binaryArtifactRepository = binaryArtifactRepository;
        this.apiSettings = apiSettings;
    }

    @Override
    public Collection<ArtifactAuditResponse> evaluate(Dashboard dashboard, long beginDate, long endDate, Map<?, ?> data) throws AuditException {

        List<CollectorItem> artifactCollectorItems = getCollectorItems(dashboard, CollectorType.Artifact);
        if (CollectionUtils.isEmpty(artifactCollectorItems)) {
            throw new AuditException("No artifacts are available", AuditException.NO_COLLECTOR_ITEM_CONFIGURED);
        }
        return artifactCollectorItems.stream().map(item -> evaluate(item, beginDate, endDate, null)).collect(Collectors.toList());
    }

    @Override
    public Collection<ArtifactAuditResponse> evaluateNextGen(ArtifactAuditRequest artifactAuditRequest, Dashboard dashboard, long beginDate, long endDate, Map<?, ?> data) throws AuditException {
        List<CollectorItem> artifactCollectorItems = getCollectorItemsNextGen(dashboard, CollectorType.Artifact);
        if (CollectionUtils.isEmpty(artifactCollectorItems)) {
            throw new AuditException("No artifacts are available", AuditException.NO_COLLECTOR_ITEM_CONFIGURED);
        }
        CollectorItem artifactItem = getArtifactItemConfiguredToDashboard(artifactAuditRequest, artifactCollectorItems);
        if(Objects.isNull(artifactItem)){
            throw new AuditException("Artifact is not configured to dashboard="+dashboard.getTitle(), AuditException.NO_COLLECTOR_ITEM_CONFIGURED);
        }
        return Stream.of(getArtifactAuditResponseForVersion(artifactAuditRequest, artifactItem, beginDate, endDate)).collect(Collectors.toList());
    }



    @Override
    public ArtifactAuditResponse evaluate(CollectorItem collectorItem, long beginDate, long endDate, Map<?, ?> data) {
        return getArtifactAuditResponse(collectorItem, beginDate, endDate);
    }

    private ArtifactAuditResponse getArtifactAuditResponse(CollectorItem collectorItem, long beginDate, long endDate) {
        ArtifactAuditResponse artifactAuditResponse = new ArtifactAuditResponse();
        if(collectorItem ==null) return artifactNotConfigured();

        String artifactName = getValue(collectorItem, ARTIFACT_NAME);
        String path = getValue(collectorItem, PATH);
        String repoName = getValue(collectorItem, REPO_NAME);
        artifactAuditResponse.setAuditEntity(collectorItem.getOptions());
        if (StringUtils.isEmpty(artifactName) || StringUtils.isEmpty(repoName) || StringUtils.isEmpty(path)) {
            return getErrorResponse(collectorItem, artifactAuditResponse,ArtifactAuditStatus.COLLECTOR_ITEM_ERROR);
        }
        if (!CollectionUtils.isEmpty(collectorItem.getErrors())) {
            return getErrorResponse(collectorItem,artifactAuditResponse, ArtifactAuditStatus.UNAVAILABLE);
        }
        if(isThirdParty(repoName)){
            artifactAuditResponse.addAuditStatus(ArtifactAuditStatus.ART_SYS_ACCT_BUILD_THIRD_PARTY);
        }
        List<BinaryArtifact> binaryArtifacts = binaryArtifactRepository.findByCollectorItemIdAndTimestampIsBetweenOrderByTimestampDesc(collectorItem.getId(), beginDate - 1, endDate + 1);
        if (CollectionUtils.isEmpty(binaryArtifacts)) {
            return getErrorResponse(collectorItem, artifactAuditResponse, ArtifactAuditStatus.NO_ACTIVITY);
        }
        artifactAuditResponse.setBinaryArtifacts(binaryArtifacts);
        binaryArtifacts.sort(Comparator.comparing(BinaryArtifact::getCreatedTimeStamp));
        artifactAuditResponse.setLastUpdated(getLastUpdated(binaryArtifacts));
        boolean isBuild = binaryArtifacts.stream().anyMatch(ba-> CollectionUtils.isNotEmpty(ba.getBuildInfos()));
        boolean isDocker = binaryArtifacts.stream().anyMatch(ba-> Optional.ofNullable(ba.getVirtualRepos()).orElse(Collections.emptyList()).stream().anyMatch(repo -> repo.contains(DOCKER)));
        evaluateArtifactForServiceAccountAndBuild(artifactAuditResponse, isBuild);


        if (isDocker) {
            artifactAuditResponse.addAuditStatus(ArtifactAuditStatus.ART_DOCK_IMG_FOUND);
        }
        return artifactAuditResponse;
    }

    private ArtifactAuditResponse getArtifactAuditResponseForVersion(ArtifactAuditRequest artifactAuditRequest , CollectorItem collectorItem, long beginDate, long endDate) {
        ArtifactAuditResponse artifactAuditResponse = new ArtifactAuditResponse();
        if(collectorItem ==null) return artifactNotConfigured();
        String version = artifactAuditRequest.getArtifactVersion();
        String artifactName = getValue(collectorItem, ARTIFACT_NAME);
        String path = getValue(collectorItem, PATH);
        String repoName = getValue(collectorItem, REPO_NAME);
        artifactAuditResponse.setAuditEntity(collectorItem.getOptions());
        if (StringUtils.isEmpty(artifactName) || StringUtils.isEmpty(repoName) || StringUtils.isEmpty(path)) {
            return getErrorResponse(collectorItem, artifactAuditResponse,ArtifactAuditStatus.COLLECTOR_ITEM_ERROR);
        }
        if (!CollectionUtils.isEmpty(collectorItem.getErrors())) {
            return getErrorResponse(collectorItem,artifactAuditResponse, ArtifactAuditStatus.UNAVAILABLE);
        }
        if(isThirdParty(repoName)){
            artifactAuditResponse.addAuditStatus(ArtifactAuditStatus.ART_SYS_ACCT_BUILD_THIRD_PARTY);
        }
        List<BinaryArtifact> binaryArtifacts = binaryArtifactRepository.findByCollectorItemIdAndTimestampIsBetweenOrderByTimestampDesc(collectorItem.getId(), beginDate - 1, endDate + 1);
        // filter binary artifacts basing on version
        List<BinaryArtifact> filtered = binaryArtifacts.stream().filter(binaryArtifact -> binaryArtifact.getArtifactVersion().contains(version)).collect(Collectors.toList());
        if (CollectionUtils.isEmpty(filtered)) {
            return getErrorResponse(collectorItem, artifactAuditResponse, ArtifactAuditStatus.NO_ACTIVITY);
        }
        artifactAuditResponse.setBinaryArtifacts(filtered);
        filtered.sort(Comparator.comparing(BinaryArtifact::getCreatedTimeStamp));
        artifactAuditResponse.setLastUpdated(getLastUpdated(filtered));
        boolean isBuild = filtered.stream().anyMatch(ba-> CollectionUtils.isNotEmpty(ba.getBuildInfos()));
        boolean isDocker = filtered.stream().anyMatch(ba-> Optional.ofNullable(ba.getVirtualRepos()).orElse(Collections.emptyList()).stream().anyMatch(repo -> repo.contains(DOCKER)));
        evaluateArtifactForServiceAccountAndBuild(artifactAuditResponse, isBuild);


        if (isDocker) {
            artifactAuditResponse.addAuditStatus(ArtifactAuditStatus.ART_DOCK_IMG_FOUND);
        }
        return artifactAuditResponse;
    }

    private CollectorItem getArtifactItemConfiguredToDashboard(ArtifactAuditRequest artifactAuditRequest, List<CollectorItem> artifactItems){
        Optional match =  artifactItems.stream().filter(artifactItem -> artifactItem.getOptions().get("artifactName").equals(artifactAuditRequest.getArtifactName())
                && artifactItem.getOptions().get("repoName").equals(artifactAuditRequest.getArtifactRepo())
                && artifactItem.getOptions().get("path").equals(artifactAuditRequest.getArtifactPath())
                && artifactItem.getOptions().get("instanceUrl").equals(artifactAuditRequest.getArtifactUrl())).findFirst();
        if(Objects.isNull(match)) return null;
        return (CollectorItem) match.get();
    }

    private long getLastUpdated(List<BinaryArtifact> binaryArtifacts) {
        BinaryArtifact createdTime = getBinaryArtifactWithMaxTimestamp(binaryArtifacts, Comparator.comparing(BinaryArtifact::getCreatedTimeStamp));
        BinaryArtifact modifiedTime = getBinaryArtifactWithMaxTimestamp(binaryArtifacts, Comparator.comparing(BinaryArtifact::getModifiedTimeStamp));
        return NumberUtils.max(createdTime.getCreatedTimeStamp(),modifiedTime.getModifiedTimeStamp());
    }

    private BinaryArtifact getBinaryArtifactWithMaxTimestamp(List<BinaryArtifact> binaryArtifacts, Comparator<BinaryArtifact> comparing) {
        if(CollectionUtils.isNotEmpty(binaryArtifacts)){
            return binaryArtifacts.stream().max(comparing).get();
        }
      return new BinaryArtifact();
    }

    private String getValue(CollectorItem collectorItem, String attribute) {
        return (String) collectorItem.getOptions().get(attribute);
    }

    private void evaluateArtifactForServiceAccountAndBuild(ArtifactAuditResponse artifactAuditResponse, boolean isBuild) {
        if (isBuild) {
            artifactAuditResponse.addAuditStatus(ArtifactAuditStatus.ART_SYS_ACCT_BUILD_AUTO);
        } else {
            artifactAuditResponse.addAuditStatus(ArtifactAuditStatus.ART_SYS_ACCT_BUILD_USER);
        }
    }

    private boolean isThirdParty(String repoName) {
        if(StringUtils.isEmpty(apiSettings.getThirdPartyRegex()) || StringUtils.isEmpty(repoName)) return false;
        return Pattern.compile(apiSettings.getThirdPartyRegex()).matcher(repoName).matches();
    }

    private ArtifactAuditResponse getErrorResponse(CollectorItem collectorItem, ArtifactAuditResponse errorAuditResponse, ArtifactAuditStatus artifactAuditStatus) {
        errorAuditResponse.addAuditStatus(artifactAuditStatus);
        errorAuditResponse.setLastExecutionTime(collectorItem.getLastUpdated());
        errorAuditResponse.setArtifactName(getValue(collectorItem, ARTIFACT_NAME));
        errorAuditResponse.setAuditEntity(collectorItem.getOptions());
        return errorAuditResponse;
    }

    private ArtifactAuditResponse artifactNotConfigured(){
        ArtifactAuditResponse notConfigured = new ArtifactAuditResponse();
        notConfigured.addAuditStatus(ArtifactAuditStatus.ARTIFACT_NOT_CONFIGURED);
        notConfigured.setErrorMessage("Unable to register in Hygieia, Artifact not configured ");
        return notConfigured;
    }

}
