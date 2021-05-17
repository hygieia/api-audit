package com.capitalone.dashboard.evaluator;

import com.capitalone.dashboard.ApiSettings;
import com.capitalone.dashboard.common.CommonCodeReview;
import com.capitalone.dashboard.model.Collector;
import com.capitalone.dashboard.model.CollectorItem;
import com.capitalone.dashboard.model.CollectorType;
import com.capitalone.dashboard.model.Commit;
import com.capitalone.dashboard.model.CommitType;
import com.capitalone.dashboard.model.Dashboard;
import com.capitalone.dashboard.model.GitRequest;
import com.capitalone.dashboard.model.Review;
import com.capitalone.dashboard.model.SCM;
import com.capitalone.dashboard.model.CollectionError;
import com.capitalone.dashboard.repository.CollectorRepository;
import com.capitalone.dashboard.model.ServiceAccount;
import com.capitalone.dashboard.model.AuditException;
import com.capitalone.dashboard.repository.CommitRepository;
import com.capitalone.dashboard.repository.GitRequestRepository;
import com.capitalone.dashboard.request.ArtifactAuditRequest;
import com.capitalone.dashboard.response.CodeReviewAuditResponseV2;
import com.capitalone.dashboard.status.CodeReviewAuditStatus;
import com.capitalone.dashboard.util.GitHubParsedUrl;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.capitalone.dashboard.repository.ServiceAccountRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class CodeReviewEvaluator extends Evaluator<CodeReviewAuditResponseV2> {
        private final CommitRepository commitRepository;
        private final GitRequestRepository gitRequestRepository;
        private final CollectorRepository collectorRepository;
        private final ServiceAccountRepository serviceAccountRepository;

        protected ApiSettings settings;
        private static final String BRANCH = "branch";
        private static final String REPO_URL = "url";

    @Autowired
    public CodeReviewEvaluator(CommitRepository commitRepository, GitRequestRepository gitRequestRepository,
                                CollectorRepository collectorRepository, ServiceAccountRepository serviceAccountRepository,
                                ApiSettings settings) {
        this.commitRepository = commitRepository;
        this.gitRequestRepository = gitRequestRepository;
        this.collectorRepository = collectorRepository;
        this.settings = settings;
        this.serviceAccountRepository = serviceAccountRepository;
    }


    @Override
    public Collection<CodeReviewAuditResponseV2> evaluate(Dashboard dashboard, long beginDate, long endDate, Map<?, ?> data) throws AuditException {
        List<CodeReviewAuditResponseV2> responseV2s = new ArrayList<>();
        List<CollectorItem> repoItems = getCollectorItems(dashboard, CollectorType.SCM);
        if (CollectionUtils.isEmpty(repoItems)) {
            throw new AuditException("No code repository configured", AuditException.NO_COLLECTOR_ITEM_CONFIGURED);
        }

        //making sure we have a goot url?
        repoItems.forEach(repoItem -> {
            String scmUrl = (String) repoItem.getOptions().get("url");
            String scmBranch = (String) repoItem.getOptions().get("branch");
            GitHubParsedUrl gitHubParsed = new GitHubParsedUrl(scmUrl);
            String parsedUrl = gitHubParsed.getUrl(); //making sure we have a goot url?
            CodeReviewAuditResponseV2 reviewResponse = null;

            if (repoItem.isPushed()) {
                Collector githubCollector = collectorRepository.findByName("GitHub");
                List<CollectorItem> collectorItemList = new ArrayList<>();
                List<ObjectId> collectorIdList = new ArrayList<>();
                collectorIdList.add(githubCollector.getId());
                Iterable<CollectorItem> collectorItemIterable
                        = collectorItemRepository.findAllByOptionNameValueAndCollectorIdsIn(REPO_URL, parsedUrl, collectorIdList);

                for (CollectorItem ci : collectorItemIterable) {
                    if (scmBranch.equalsIgnoreCase((String) ci.getOptions().get(BRANCH))) { continue; }

                    collectorItemList.add(ci);
                }

                reviewResponse = evaluate(repoItem, collectorItemList, beginDate, endDate, null);
            } else {
                reviewResponse = evaluate(repoItem, beginDate, endDate, null);
            }

            reviewResponse.setUrl(parsedUrl);
            reviewResponse.setBranch(scmBranch);
            reviewResponse.setLastUpdated(repoItem.getLastUpdated());
            responseV2s.add(reviewResponse);
        });
        return responseV2s;
    }

    @Override
    public Collection<CodeReviewAuditResponseV2> evaluateNextGen(ArtifactAuditRequest artifactAuditRequest, Dashboard dashboard, long beginDate, long endDate, Map<?, ?> data) throws AuditException {
        return null;
    }


    @Override
    public CodeReviewAuditResponseV2 evaluate(CollectorItem collectorItem, long beginDate, long endDate, Map<?, ?> data) {
        return getPeerReviewResponses(collectorItem, new ArrayList<>(), beginDate, endDate);
    }

    protected CodeReviewAuditResponseV2 evaluate(CollectorItem collectorItem, List<CollectorItem> collectorItemList, long beginDate, long endDate, Map<?, ?> data) {
        return getPeerReviewResponses(collectorItem, collectorItemList, beginDate, endDate);
    }

    /**
     * Return an empty response in error situation
     *
     * @param repoItem
     * @param scmBranch
     * @param scmUrl
     * @return
     */
    protected CodeReviewAuditResponseV2 getErrorResponse(CollectorItem repoItem, String scmBranch, String scmUrl) {
        CodeReviewAuditResponseV2 noPRsCodeReviewAuditResponse = new CodeReviewAuditResponseV2();
        noPRsCodeReviewAuditResponse.addAuditStatus(CodeReviewAuditStatus.COLLECTOR_ITEM_ERROR);
        noPRsCodeReviewAuditResponse.setAuditEntity(repoItem.getOptions());
        noPRsCodeReviewAuditResponse.setLastUpdated(repoItem.getLastUpdated());
        noPRsCodeReviewAuditResponse.setBranch(scmBranch);
        noPRsCodeReviewAuditResponse.setUrl(scmUrl);
        noPRsCodeReviewAuditResponse.setErrorMessage(repoItem.getErrors() == null ? null : repoItem.getErrors().get(0).getErrorMessage());
        return noPRsCodeReviewAuditResponse;
    }

    private CodeReviewAuditResponseV2 getPeerReviewResponses(CollectorItem repoItem,
                                                             List<CollectorItem> collectorItemList,
                                                             long beginDt, long endDt) {

        CodeReviewAuditResponseV2 reviewAuditResponseV2 = new CodeReviewAuditResponseV2();

        if (repoItem == null) {
            reviewAuditResponseV2.addAuditStatus(CodeReviewAuditStatus.REPO_NOT_CONFIGURED);
            return reviewAuditResponseV2;
        }
        reviewAuditResponseV2.setAuditEntity(repoItem.getOptions());

        String scmUrl = (String) repoItem.getOptions().get("url");
        String scmBranch = (String) repoItem.getOptions().get("branch");

        GitHubParsedUrl gitHubParsed = new GitHubParsedUrl(scmUrl);

        String parsedUrl = gitHubParsed.getUrl(); //making sure we have a goot url?

        if (StringUtils.isEmpty(scmBranch) || StringUtils.isEmpty(scmUrl)) {
            return getErrorResponse(repoItem, scmBranch, parsedUrl);
        }

        //if the collector item is pending data collection
        if (repoItem.getLastUpdated() == 0) {
            reviewAuditResponseV2.addAuditStatus(CodeReviewAuditStatus.PENDING_DATA_COLLECTION);

            reviewAuditResponseV2.setLastUpdated(repoItem.getLastUpdated());
            reviewAuditResponseV2.setBranch(scmBranch);
            reviewAuditResponseV2.setUrl(scmUrl);
            return reviewAuditResponseV2;
        }

        List<GitRequest> pullRequests = gitRequestRepository.findByCollectorItemIdAndMergedAtIsBetween(repoItem.getId(), beginDt-1, endDt+1);
        List<Commit> allCommits = commitRepository.findByCollectorItemIdAndScmCommitTimestampIsBetween(repoItem.getId(), beginDt-1, endDt+1);
        //Filter empty commits
        List<Commit> commits = allCommits.stream().filter(commit -> commit.getNumberOfChanges()>0).collect(Collectors.toList());
        commits.sort(Comparator.comparing(Commit::getScmCommitTimestamp).reversed());
        pullRequests.sort(Comparator.comparing(GitRequest::getMergedAt).reversed());

        if (hasValidRepoError(repoItem, pullRequests, commits, beginDt, endDt)) {
            return getErrorResponse(repoItem, scmBranch, parsedUrl);
        }

        if (CollectionUtils.isEmpty(pullRequests)) {
            reviewAuditResponseV2.addAuditStatus(CodeReviewAuditStatus.NO_PULL_REQ_FOR_DATE_RANGE);
        }
        if (CollectionUtils.isEmpty(commits)) {
            reviewAuditResponseV2.addAuditStatus(CodeReviewAuditStatus.NO_COMMIT_FOR_DATE_RANGE);
        }
        reviewAuditResponseV2.setUrl(parsedUrl);
        reviewAuditResponseV2.setBranch(scmBranch);
        reviewAuditResponseV2.setLastCommitTime(CollectionUtils.isEmpty(commits)? 0 : commits.get(0).getScmCommitTimestamp());
        reviewAuditResponseV2.setLastPRMergeTime(CollectionUtils.isEmpty(pullRequests)? 0 : pullRequests.get(0).getMergedAt());
        reviewAuditResponseV2.setLastUpdated(repoItem.getLastUpdated());

        List<String> allPrCommitShas = new ArrayList<>();
        pullRequests.stream().filter(pr -> "merged".equalsIgnoreCase(pr.getState())).forEach(pr -> {
            auditPullRequest(repoItem, pr, commits, allPrCommitShas, reviewAuditResponseV2);
        });

        //check any commits not directly tied to pr
        commits.stream().filter(commit -> !allPrCommitShas.contains(commit.getScmRevisionNumber()) && StringUtils.isEmpty(commit.getPullNumber()) && commit.getType() == CommitType.New).forEach(reviewAuditResponseV2::addDirectCommit);

        //check any commits not directly tied to pr
        List<Commit> commitsNotDirectlyTiedToPr = new ArrayList<>();
        commits.forEach(commit -> {
            // flag any commits made by an LDAP unauthenticated user
            checkCommitByLDAPUnauthUser(reviewAuditResponseV2, commit);

            if (!checkPrCommitsAndCommitType(allPrCommitShas, commit)) { return; }

            if ( isCommitEligibleForDirectCommitsForPushedRepo(repoItem, commit, collectorItemList, beginDt, endDt)
                    || isCommitEligibleForDirectCommitsForPulledRepo(repoItem, commit) ) {
                commitsNotDirectlyTiedToPr.add(commit);
                // check for service account and increment version tag for service account on direct commits.
                auditDirectCommits(reviewAuditResponseV2, commit);
            }
        });

        return reviewAuditResponseV2;
    }

    private boolean hasValidRepoError(CollectorItem repoItem, List<GitRequest> pullRequests, List<Commit> commits, long beginDt, long endDt) {
        List<CollectionError> colErrors = repoItem.getErrors();
        return (CollectionUtils.isNotEmpty(colErrors)
                && colErrors.stream().anyMatch(err -> isWithinTimeRange(err.getTimestamp(), beginDt, endDt))
                && CollectionUtils.isEmpty(pullRequests)
                && CollectionUtils.isEmpty(commits));
    }

    private boolean isWithinTimeRange(long timestamp, long beginDt, long endDt) {
        return (timestamp >= beginDt && timestamp <= endDt);
    }

    /**
     * Flag commit made by an unauthenticated user that is not by a service account and has non-code changes with an approved message
     * Adds SCM_AUTHOR_LOGIN_INVALID status at Code Review level
     */
    private void checkCommitByLDAPUnauthUser(CodeReviewAuditResponseV2 reviewAuditResponseV2, Commit commit) {
        if (StringUtils.isNotEmpty(commit.getScmAuthorType()) && settings.getLdapdnCheckIgnoredAuthorTypes().contains(commit.getScmAuthorType())) {
            return;
        }
        if (StringUtils.isEmpty(commit.getScmAuthorLDAPDN()) &&
                !CommonCodeReview.matchIncrementVersionTag(commit.getScmCommitLog(), settings)) {
            reviewAuditResponseV2.addAuditStatus(CodeReviewAuditStatus.SCM_AUTHOR_LOGIN_INVALID);
            // add commit made by unauth user to commitsByLDAPUnauthUsers list
            reviewAuditResponseV2.addCommitByLDAPUnauthUser(commit);
        }
    }

    private boolean checkPrCommitsAndCommitType(List<String> allPrCommitShas, Commit commit) {
        if (Objects.isNull(commit)) return false;
        if (CollectionUtils.isEmpty(allPrCommitShas)) return true;
        return (commit.getType() == CommitType.New) && !allPrCommitShas.contains(commit.getScmRevisionNumber());
    }

    private boolean isCommitEligibleForDirectCommitsForPushedRepo(CollectorItem repoItem, Commit commit,
                                                                  List<CollectorItem> collectorItemList,
                                                                  long beginDt, long endDt) {
        return repoItem.isPushed()
                && !existsApprovedPROnAnotherBranch(repoItem, commit, collectorItemList, beginDt, endDt);
    }

    private boolean isCommitEligibleForDirectCommitsForPulledRepo(CollectorItem repoItem, Commit commit) {
        return !repoItem.isPushed() && StringUtils.isEmpty(commit.getPullNumber());
    }

    protected void auditPullRequest(CollectorItem repoItem, GitRequest pr, List<Commit> commits,
                                    List<String> allPrCommitShas, CodeReviewAuditResponseV2 reviewAuditResponseV2) {
        Commit mergeCommit = Optional.ofNullable(commits)
                                .orElseGet(Collections::emptyList).stream()
                                .filter(c -> Objects.equals(c.getScmRevisionNumber(), pr.getScmRevisionNumber()))
                                .findFirst().orElse(null);

        if (mergeCommit == null) {
            mergeCommit = Optional.ofNullable(commits)
                            .orElseGet(Collections::emptyList).stream()
                            .filter(c -> Objects.equals(c.getScmRevisionNumber(), pr.getScmMergeEventRevisionNumber()))
                            .findFirst().orElse(null);
        }

        CodeReviewAuditResponseV2.PullRequestAudit pullRequestAudit = new CodeReviewAuditResponseV2.PullRequestAudit();
        pullRequestAudit.setPullRequest(pr);
        List<Commit> allCommitsRelatedToPr = pr.getCommits();
        List<Commit> commitsRelatedToPr = allCommitsRelatedToPr.stream().filter(commit -> commit.getNumberOfChanges()>0).collect(Collectors.toList());
        commitsRelatedToPr.sort(Comparator.comparing(e -> (e.getScmCommitTimestamp())));

        if (mergeCommit == null) {
            pullRequestAudit.addAuditStatus(CodeReviewAuditStatus.MERGECOMMITER_NOT_FOUND);
        } else {
            commitsRelatedToPr.add(mergeCommit);
            if(Objects.nonNull(mergeCommit.getScmAuthorLogin())){
                pullRequestAudit.addAuditStatus(pr.getUserId().equalsIgnoreCase(mergeCommit.getScmAuthorLogin()) ? CodeReviewAuditStatus.COMMITAUTHOR_EQ_MERGECOMMITER : CodeReviewAuditStatus.COMMITAUTHOR_NE_MERGECOMMITER);
            }else{
                pullRequestAudit.addAuditStatus(pr.getUserId().equalsIgnoreCase(mergeCommit.getScmCommitterLogin()) ? CodeReviewAuditStatus.COMMITAUTHOR_EQ_MERGECOMMITER : CodeReviewAuditStatus.COMMITAUTHOR_NE_MERGECOMMITER);
            }
        }
        allPrCommitShas.addAll(commitsRelatedToPr.stream().map(SCM::getScmRevisionNumber).collect(Collectors.toList()));

        // Check peer reviews
        boolean peerReviewed = CommonCodeReview.computePeerReviewStatus(pr, settings, pullRequestAudit, commits, commitRepository, serviceAccountRepository);
        pullRequestAudit.addAuditStatus(peerReviewed ? CodeReviewAuditStatus.PULLREQ_REVIEWED_BY_PEER : CodeReviewAuditStatus.PULLREQ_NOT_PEER_REVIEWED);
        String sourceRepo = pr.getSourceRepo();
        String targetRepo = pr.getTargetRepo();
        pullRequestAudit.addAuditStatus(sourceRepo == null ? CodeReviewAuditStatus.GIT_FORK_STRATEGY : sourceRepo.equalsIgnoreCase(targetRepo) ? CodeReviewAuditStatus.GIT_BRANCH_STRATEGY : CodeReviewAuditStatus.GIT_FORK_STRATEGY);
        auditCommitAfterPrMerge(reviewAuditResponseV2, pullRequestAudit, pr, commitsRelatedToPr);
        auditCommitsAfterReviews(reviewAuditResponseV2, pullRequestAudit, pr);
        reviewAuditResponseV2.addPullRequest(pullRequestAudit);
    }

    /**
     * Flag commit(s) made after pull request merge as violation
     * Adds COMMIT_AFTER_PR_MERGE status at both PR and Code Review level
     */
    protected void auditCommitAfterPrMerge(CodeReviewAuditResponseV2 reviewAuditResponseV2, CodeReviewAuditResponseV2.PullRequestAudit pullRequestAudit, GitRequest pr, List<Commit> commitsRelatedToPr) {
        if (CollectionUtils.isEmpty(commitsRelatedToPr)) { return; }
        List <Commit> commitsAfterPrMerge = commitsRelatedToPr.stream().filter(Objects::nonNull).filter(commit -> (
                commit.getScmCommitTimestamp() > pr.getMergedAt()
                )).collect(Collectors.toList());
        if (CollectionUtils.isEmpty(commitsAfterPrMerge)) { return; }

        pullRequestAudit.addAuditStatus(CodeReviewAuditStatus.COMMIT_AFTER_PR_MERGE);
        // if code review audit status doesn't already contain this status, then add it
        if (!reviewAuditResponseV2.getAuditStatuses().contains(CodeReviewAuditStatus.COMMIT_AFTER_PR_MERGE)) {
            reviewAuditResponseV2.addAuditStatus(CodeReviewAuditStatus.COMMIT_AFTER_PR_MERGE);
        }
        // add specific commit(s) made after PR merge to commitAfterPrMerge list
        commitsAfterPrMerge.forEach(reviewAuditResponseV2::addCommitAfterPrMerge);
    }

    /**
     * Flag commits made after peer reviews as violations, exclude commits that are merge commits from target branches
     */
    protected void auditCommitsAfterReviews(CodeReviewAuditResponseV2 reviewAuditResponseV2, CodeReviewAuditResponseV2.PullRequestAudit pullRequestAudit, GitRequest pr) {
        List<Review> reviewsRelatedToPr = pr.getReviews().stream().filter(review -> StringUtils.equals(review.getState(),"APPROVED")).sorted(Comparator.comparing(Review::getUpdatedAt)).collect(Collectors.toList());
        if(CollectionUtils.isEmpty(reviewsRelatedToPr)) { return; }

        List<Commit> commitsRelatedToPr = pr.getCommits().stream().filter(commit -> commit.getNumberOfChanges()>0).collect(Collectors.toList());
        if(CollectionUtils.isEmpty(commitsRelatedToPr)) { return; }

        long lastReviewTimestamp = reviewsRelatedToPr.get(reviewsRelatedToPr.size() - 1).getUpdatedAt();
        List<Commit> commitsAfterPrReviews = commitsRelatedToPr.stream().filter(Objects::nonNull).filter(commit -> (
                commit.getScmCommitTimestamp() > lastReviewTimestamp
                && !isMergeCommitFromTargetBranch(commit, pr))
        ).collect(Collectors.toList());

        if(CollectionUtils.isEmpty(commitsAfterPrReviews)) { return; }
        reviewAuditResponseV2.addAuditStatus(CodeReviewAuditStatus.COMMITS_AFTER_PR_REVIEWS);
        pullRequestAudit.addAuditStatus(CodeReviewAuditStatus.COMMITS_AFTER_PR_REVIEWS);
        commitsAfterPrReviews.forEach(reviewAuditResponseV2::addCommitAfterPrReviews);
    }

    /**
     * Check if a commit is a merge commit from target branches
     * this type of commits has default commit logs that look like "merge branch 'target_branch' into ..."
     * Note that this ins't the ideal way to check this as commit logs can be modified by users
     */
    public boolean isMergeCommitFromTargetBranch(Commit commit, GitRequest pr) {
        if(commit == null || pr == null) return false;
        String commitLog = commit.getScmCommitLog();
        List<String> mergeCommitFromTargetBranchRegEx = settings.getMergeCommitFromTargetBranchRegEx();
        for (String mergeCommitRegex : mergeCommitFromTargetBranchRegEx) {
            Pattern pattern = Pattern.compile(mergeCommitRegex, Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(commitLog);
            if (matcher.matches() && (StringUtils.equalsIgnoreCase(pr.getScmBranch(), matcher.group(2))
                    || StringUtils.equalsIgnoreCase(pr.getScmUrl(), matcher.group(2)))) {
                    // exit only if matches, else check other patterns
                    return true;
                }
            }
        return false;
    }

    protected boolean existsApprovedPROnAnotherBranch(CollectorItem repoItem, Commit commit, List<CollectorItem> collectorItemList,
                                                      long beginDt, long endDt) {
        CollectorItem collectorItem = Optional.ofNullable(collectorItemList)
                                        .orElseGet(Collections::emptyList).stream()
                                        .filter(ci -> existsApprovedPRForCollectorItem(repoItem, commit, ci, beginDt, endDt))
                                        .findFirst().orElse(null);
        return (collectorItem != null);
    }

    protected boolean existsApprovedPRForCollectorItem(CollectorItem repoItem, Commit commit, CollectorItem collectorItem,
                                                       long beginDt, long endDt) {
        List<GitRequest> mergedPullRequests
                = gitRequestRepository.findByCollectorItemIdAndMergedAtIsBetween(collectorItem.getId(), beginDt-1, endDt+1);

        List<Commit> commits
                = commitRepository.findByCollectorItemIdAndScmCommitTimestampIsBetween(collectorItem.getId(), beginDt-1, endDt+1);

        GitRequest mergedPullRequestFound
                = Optional.ofNullable(mergedPullRequests)
                .orElseGet(Collections::emptyList).stream()
                .filter(mergedPullRequest -> evaluateMergedPullRequest(repoItem, mergedPullRequest, commit, commits))
                .findFirst().orElse(null);

        return (mergedPullRequestFound != null);
    }

    private boolean evaluateMergedPullRequest (CollectorItem repoItem, GitRequest mergedPullRequest,
                                               Commit commit, List<Commit> commits) {
        Commit matchingCommit = findAMatchingCommit(mergedPullRequest, commit, commits);
        if (matchingCommit == null) { return false; }

        List<String> allPrCommitShas = new ArrayList<>();
        CodeReviewAuditResponseV2 reviewAuditResponseV2 = new CodeReviewAuditResponseV2();

        // Matching commit found, now make sure the PR for the matching commit passes all the audit checks
        auditPullRequest(repoItem, mergedPullRequest, commits, allPrCommitShas, reviewAuditResponseV2);
        CodeReviewAuditResponseV2.PullRequestAudit pullRequestAudit = reviewAuditResponseV2.getPullRequests().get(0);

        return (pullRequestAudit != null) && codeReviewAuditResponseCheck(pullRequestAudit);
    }

    protected boolean codeReviewAuditResponseCheck(CodeReviewAuditResponseV2.PullRequestAudit pullRequestAudit) {
        for (CodeReviewAuditStatus status : pullRequestAudit.getAuditStatuses()) {
            if ((status == CodeReviewAuditStatus.COMMITAUTHOR_EQ_MERGECOMMITER)
                    || (status == CodeReviewAuditStatus.PULLREQ_NOT_PEER_REVIEWED)) {
                return false;
            }
        }
        return true;
    }

    protected Commit findAMatchingCommit(GitRequest mergedPullRequest, Commit commitToBeFound, List<Commit> commitsOnTheRepo) {
        List<Commit> commitsRelatedToPr = mergedPullRequest.getCommits();

        // So, will find the matching commit based on the criteria below for "Merge Only" case.
        Commit matchingCommit
                = Optional.ofNullable(commitsRelatedToPr)
                    .orElseGet(Collections::emptyList).stream()
                    .filter(commitRelatedToPr -> checkIfCommitsMatch(commitRelatedToPr, commitToBeFound))
                    .findFirst().orElse(null);
        // For "Squash and Merge", or a "Rebase and Merge":
        // The merged commit will not be part of the commits in the PR.
        // The PR will only have the original commits when the PR was opened.
        // Search for the commit in the list of commits on the repo in the db
        if (matchingCommit == null) {
            String pullNumber = mergedPullRequest.getNumber();
            matchingCommit
                    = Optional.ofNullable(commitsOnTheRepo)
                        .orElseGet(Collections::emptyList).stream()
                        .filter(commitOnRepo -> Objects.equals(pullNumber, commitToBeFound.getPullNumber())
                            && checkIfCommitsMatch(commitOnRepo, commitToBeFound))
                        .findFirst().orElse(null);
        }

        return matchingCommit;
    }

    protected boolean checkIfCommitsMatch(Commit commit1, Commit commit2) {
        return Objects.equals(commit1.getScmRevisionNumber(), commit2.getScmRevisionNumber())
                && Objects.equals(commit1.getScmAuthor(), commit2.getScmAuthor())
                && Objects.equals(commit1.getScmCommitTimestamp(), commit2.getScmCommitTimestamp())
                && Objects.equals(commit1.getScmCommitLog(), commit2.getScmCommitLog());
    }

    protected void auditDirectCommits(CodeReviewAuditResponseV2 reviewAuditResponseV2, Commit commit) {
        Stream<String> combinedStream
                = Stream.of(commit.getFilesAdded(), commit.getFilesModified(),commit.getFilesRemoved()).filter(Objects::nonNull).flatMap(Collection::stream);
        Collection<String> collectionCombined = combinedStream.collect(Collectors.toList());
       if (CommonCodeReview.checkForServiceAccount(commit.getScmAuthorLDAPDN(), settings,getAllServiceAccounts(),commit.getScmAuthor(),collectionCombined.stream().collect(Collectors.toList()),true,reviewAuditResponseV2)) {
            reviewAuditResponseV2.addAuditStatus(CodeReviewAuditStatus.COMMITAUTHOR_EQ_SERVICEACCOUNT);
            auditIncrementVersionTag(reviewAuditResponseV2, commit, CodeReviewAuditStatus.DIRECT_COMMIT_NONCODE_CHANGE_SERVICE_ACCOUNT);
        } else  if (StringUtils.isBlank(commit.getScmAuthorLDAPDN())) {
           auditIncrementVersionTag(reviewAuditResponseV2, commit, CodeReviewAuditStatus.DIRECT_COMMIT_NONCODE_CHANGE);
        }else {
            auditIncrementVersionTag(reviewAuditResponseV2, commit, CodeReviewAuditStatus.DIRECT_COMMIT_NONCODE_CHANGE_USER_ACCOUNT);
        }
    }

    protected void auditIncrementVersionTag(CodeReviewAuditResponseV2 reviewAuditResponseV2, Commit commit, CodeReviewAuditStatus directCommitIncrementVersionTagStatus) {
        if (CommonCodeReview.matchIncrementVersionTag(commit.getScmCommitLog(), settings)) {
            reviewAuditResponseV2.addAuditStatus(directCommitIncrementVersionTagStatus);
        } else {
           addDirectCommitsToBase(reviewAuditResponseV2,commit);
        }
    }

    private void addDirectCommitsToBase(CodeReviewAuditResponseV2 reviewAuditResponseV2,Commit commit){
        if(commit.isFirstEverCommit()){
            reviewAuditResponseV2.addAuditStatus(CodeReviewAuditStatus.DIRECT_COMMITS_TO_BASE_FIRST_COMMIT );
        }else if(StringUtils.isEmpty(commit.getPullNumber())){
                reviewAuditResponseV2.addAuditStatus(CodeReviewAuditStatus.DIRECT_COMMITS_TO_BASE);
                reviewAuditResponseV2.addDirectCommitsToBase(commit);
        }
   }

    public Map<String,String> getAllServiceAccounts(){
        List<ServiceAccount> serviceAccounts = (List<ServiceAccount>) serviceAccountRepository.findAll();
        return serviceAccounts.stream().collect(Collectors.toMap(ServiceAccount :: getServiceAccountName, ServiceAccount::getFileNames));
    }

}
