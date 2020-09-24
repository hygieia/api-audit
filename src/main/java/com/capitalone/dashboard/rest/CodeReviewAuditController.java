package com.capitalone.dashboard.rest;

import com.capitalone.dashboard.model.AuditException;
import com.capitalone.dashboard.request.CodeReviewAuditRequest;
import com.capitalone.dashboard.response.CodeReviewAuditResponse;
import com.capitalone.dashboard.service.CodeReviewAuditService;
import com.capitalone.dashboard.util.GitHubParsedUrl;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.web.bind.annotation.RequestMethod.GET;

@RestController
public class CodeReviewAuditController {

    private final CodeReviewAuditService codeReviewAuditService;

    @Autowired
    public CodeReviewAuditController(CodeReviewAuditService codeReviewAuditService) {
        this.codeReviewAuditService = codeReviewAuditService;
    }


    /**
     * Peer Review
     * - Check commit author v/s who merged the pr
     * - peer review of a pull request
     * - check whether there are direct commits to base
     *
     * @param request caller request
     * @return response
     */
    @RequestMapping(value = "/peerReview", method = GET, produces = APPLICATION_JSON_VALUE)
	@ApiOperation(value = "Audit status of peer review validation", notes = "Returns the audit status as passed or failed for the peer review of a pull request, based on the following checks: <br />" +  "• No direct commits merged to master/release branch <br />" + "• Any change made to the master/release branch is reviewed by a second person before merge ", response = CodeReviewAuditResponse.class, responseContainer = "List")
    public ResponseEntity<Iterable<CodeReviewAuditResponse>> peerReviewByRepo(@Valid CodeReviewAuditRequest request) throws AuditException {
        GitHubParsedUrl gitHubParsed = new GitHubParsedUrl(request.getRepo());
        String repoUrl = gitHubParsed.getUrl();
        Collection<CodeReviewAuditResponse> allPeerReviews = codeReviewAuditService.getPeerReviewResponses(repoUrl, request.getBranch(), request.getScmName(), request.getBeginDate(), request.getEndDate());
        Collection<CodeReviewAuditResponse> mutatedPeerReviews = new ArrayList<>();
        if(CollectionUtils.isNotEmpty(allPeerReviews)) {
            mutatedPeerReviews = allPeerReviews.parallelStream().filter(Objects::nonNull).collect(Collectors.toList());
            mutatedPeerReviews.parallelStream().forEach( mutatedPeerReview -> mutatedPeerReview.setClientReference(request.getClientReference()));
        }
        return ResponseEntity.ok().body(mutatedPeerReviews);
    }

}

