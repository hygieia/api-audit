package com.capitalone.dashboard.common;

import com.capitalone.dashboard.ApiSettings;
import com.capitalone.dashboard.model.Commit;
import com.capitalone.dashboard.model.CommitStatus;
import com.capitalone.dashboard.model.GitRequest;
import com.capitalone.dashboard.model.Review;
import com.capitalone.dashboard.model.ServiceAccount;
import com.capitalone.dashboard.repository.CommitRepository;
import com.capitalone.dashboard.repository.ServiceAccountRepository;
import com.capitalone.dashboard.response.AuditReviewResponse;
import com.capitalone.dashboard.status.CodeReviewAuditStatus;
import org.bson.types.ObjectId;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RunWith(MockitoJUnitRunner.class)
public class CommonCodeReviewTest {

    private ApiSettings apiSettings = new ApiSettings();
    @Mock
    private CommitRepository commitRepository;
    @Mock
    private ServiceAccountRepository serviceAccountRepository;

    @Test
    public void testIsServiceAccount() {
        apiSettings.setServiceAccountOU(TestConstants.SERVICE_ACCOUNTS);
        Assert.assertFalse(CommonCodeReview.isServiceAccount("CN=hygieiaUser,OU=Developers,OU=All Users,DC=basic,DC=ds,DC=industry,DC=com", apiSettings));
        Assert.assertTrue(CommonCodeReview.isServiceAccount("CN=hygieiaUser,OU=Service Accounts,DC=basic,DC=ds,DC=industry,DC=com", apiSettings));
    }

    @Test
    public void testIsFileTypeWhitelistedPass() {
        List<String> whitelistedFile = new ArrayList<>();
        whitelistedFile.add("pom.xml");
        whitelistedFile.add("readme.md");
        apiSettings.setDirectCommitWhitelistedFiles(whitelistedFile);
        Commit commit = makeCommitWhitelistedFiles();
        Assert.assertTrue(CommonCodeReview.isFileTypeWhitelisted(commit, apiSettings));
    }

    @Test
    public void testIsFileTypeWhitelistedFail() {
        List<String> whitelistedFile = new ArrayList<>();
        whitelistedFile.add("pom.xml");
        whitelistedFile.add("readme.md");
        apiSettings.setDirectCommitWhitelistedFiles(whitelistedFile);
        Commit commit = makeCommit();
        Assert.assertFalse(CommonCodeReview.isFileTypeWhitelisted(commit, apiSettings));
    }

    @Test
    public void testComputePeerReviewStatusForServiceAccount() {
        apiSettings.setServiceAccountOU(TestConstants.SERVICE_ACCOUNTS);
        apiSettings.setPeerReviewContexts("context");
        apiSettings.setPeerReviewApprovalText("approved by");
        Mockito.when(serviceAccountRepository.findAll()).thenReturn(Stream.of(makeServiceAccount()).collect(Collectors.toList()));
        AuditReviewResponse<CodeReviewAuditStatus> codeReviewAuditRequestAuditReviewResponse = new AuditReviewResponse<>();
        Assert.assertEquals(true, CommonCodeReview.computePeerReviewStatus(makeGitRequest("Service Accounts", makeCommitList()), apiSettings, codeReviewAuditRequestAuditReviewResponse, makeCommitList(), commitRepository,serviceAccountRepository));
        Assert.assertEquals(true, codeReviewAuditRequestAuditReviewResponse.getAuditStatuses().toString().contains("PEER_REVIEW_BY_SERVICEACCOUNT"));
    }

    @Test
    public void testComputePeerReviewStatusForAllUsers() {
        apiSettings.setServiceAccountOU(TestConstants.SERVICE_ACCOUNTS);
        apiSettings.setPeerReviewContexts("context");
        apiSettings.setPeerReviewApprovalText("approved by");
        Mockito.when(serviceAccountRepository.findAll()).thenReturn(Stream.of(makeServiceAccount()).collect(Collectors.toList()));
        AuditReviewResponse<CodeReviewAuditStatus> codeReviewAuditRequestAuditReviewResponse = new AuditReviewResponse<>();
        Assert.assertEquals(false, CommonCodeReview.computePeerReviewStatus(makeGitRequest("All Users", makeCommitListSelfReviewed()), apiSettings, codeReviewAuditRequestAuditReviewResponse, makeCommitListSelfReviewed(), commitRepository,serviceAccountRepository));
        Assert.assertEquals(Boolean.TRUE,codeReviewAuditRequestAuditReviewResponse.getAuditStatuses().contains(CodeReviewAuditStatus.PEER_REVIEW_GHR));
        Assert.assertEquals(Boolean.TRUE,codeReviewAuditRequestAuditReviewResponse.getAuditStatuses().contains(CodeReviewAuditStatus.PEER_REVIEW_BY_SERVICEACCOUNT));
        Assert.assertEquals(Boolean.TRUE,codeReviewAuditRequestAuditReviewResponse.getAuditStatuses().contains(CodeReviewAuditStatus.PEER_REVIEW_GHR_SELF_APPROVAL));
    }

    private GitRequest makeGitRequest(String userAccount, List<Commit> commitList) {
        GitRequest pr = new GitRequest();
        pr.setCommitStatuses(Stream.of(makeCommitStatus()).collect(Collectors.toList()));
        pr.setReviews(Stream.of(makeReview()).collect(Collectors.toList()));
        pr.setMergeAuthor("hygieiaUser");
        pr.setMergeAuthorLDAPDN("CN=hygieiaUser,OU=" + userAccount + ",DC=basic,DC=ds,DC=industry,DC=com");
        pr.setCommits(commitList);
        pr.setScmBranch("master");
        pr.setSourceBranch("branch");

        return pr;
    }

    private List<Commit> makeCommitList() {
        Commit c1 = makeCommit();
        return new ArrayList<>(Arrays.asList(c1));
    }

    private List<Commit> makeCommitListSelfReviewed() {
        Commit c1 = makeCommit();
        Commit c2 = makeCommitByReviewer();
        return new ArrayList<>(Arrays.asList(c1, c2));
    }

    private Commit makeCommit() {
        Commit c = new Commit();
        c.setId(ObjectId.get());
        c.setScmCommitLog("Merge branch master into branch");
        c.setScmAuthor("hygieiaUser");
        c.setScmAuthorLDAPDN("CN=hygieiaUser,OU=Service Accounts,DC=basic,DC=ds,DC=industry,DC=com");
        c.setScmCommitTimestamp(100000000);
        c.setScmAuthorLogin("hygieiaUser");
        c.setFilesAdded(Arrays.asList("source1.java", "source2.java"));
        return c;
    }

    private Commit makeCommitWhitelistedFiles() {
        Commit c = new Commit();
        c.setId(ObjectId.get());
        c.setScmCommitLog("Merge branch master into branch");
        c.setScmAuthor("hygieiaUser");
        c.setScmAuthorLDAPDN("CN=hygieiaUser,OU=Service Accounts,DC=basic,DC=ds,DC=industry,DC=com");
        c.setScmCommitTimestamp(100000000);
        c.setScmAuthorLogin("hygieiaUser");
        c.setFilesAdded(Arrays.asList("readme.md", "pom.xml"));
        return c;
    }

    private Commit makeCommitByReviewer() {
        Commit c = new Commit();
        c.setId(ObjectId.get());
        c.setScmCommitLog("self-reviewed");
        c.setScmAuthor("reviewer");
        c.setScmAuthorLDAPDN("CN=hygieiaUser,OU=Service Accounts,DC=basic,DC=ds,DC=industry,DC=com");
        c.setScmCommitTimestamp(100000001);
        c.setScmAuthorLogin("reviewer");
        return c;

    }

    private CommitStatus makeCommitStatus() {
        CommitStatus cs = new CommitStatus();
        cs.setState("success");
        cs.setContext("context");
        cs.setDescription("approved by hygieiaUser");
        return cs;
    }

    private Review makeReview() {
        Review r = new Review();
        r.setState("approved");
        r.setAuthor("reviewer");
        r.setAuthorLDAPDN("CN=hygieiaUser,OU=Service Accounts,DC=basic,DC=ds,DC=industry,DC=com");
        r.setCreatedAt(200000000);
        r.setUpdatedAt(200000000);
        return r;
    }

    private ServiceAccount makeServiceAccount(){
        return new ServiceAccount("servUserName","pom.xml,test.json");
    }
}
