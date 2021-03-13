package com.capitalone.dashboard.rest;

import com.capitalone.dashboard.model.AuditException;
import com.capitalone.dashboard.request.CodeReviewAuditRequest;
import com.capitalone.dashboard.response.CodeReviewAuditResponse;
import com.capitalone.dashboard.service.CodeReviewAuditService;
import com.capitalone.dashboard.util.CommonConstants;
import com.capitalone.dashboard.util.GitHubParsedUrl;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.web.bind.annotation.RequestMethod.GET;

@RestController
public class CodeReviewAuditController {

    private final HttpServletRequest httpServletRequest;
    private final CodeReviewAuditService codeReviewAuditService;

    private static final Logger LOGGER = LoggerFactory.getLogger(CodeReviewAuditController.class);

    @Autowired
    public CodeReviewAuditController(HttpServletRequest httpServletRequest, CodeReviewAuditService codeReviewAuditService) {
        this.httpServletRequest = httpServletRequest;
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
        request.setClientReference(httpServletRequest.getHeader(CommonConstants.HEADER_CLIENT_CORRELATION_ID));
        String requester = httpServletRequest.getHeader(CommonConstants.HEADER_API_USER);
        GitHubParsedUrl gitHubParsed = new GitHubParsedUrl(request.getRepo());
        String repoUrl = gitHubParsed.getUrl();
        Collection<CodeReviewAuditResponse> allPeerReviews = codeReviewAuditService.getPeerReviewResponses(repoUrl, request.getBranch(), request.getScmName(), request.getBeginDate(), request.getEndDate());
        Collection<CodeReviewAuditResponse> mutatedPeerReviews = new ArrayList<>();
        if(CollectionUtils.isNotEmpty(allPeerReviews)) {
            mutatedPeerReviews = allPeerReviews.parallelStream().filter(Objects::nonNull).collect(Collectors.toList());
            mutatedPeerReviews.parallelStream().forEach( mutatedPeerReview -> mutatedPeerReview.setClientReference(request.getClientReference()));
            String response_message="auditStatuses:" + mutatedPeerReviews.parallelStream().map(p -> p.toString()).collect(Collectors.joining(","));
            LOGGER.info("correlation_id="+request.getClientReference() +", application=hygieia, service=api-audit, uri=" + httpServletRequest.getRequestURI() +
                    ", requester=" + requester + ", response_status=success, response_code=" + HttpStatus.OK.value()+", response_status_message=" + response_message +
                    ", auditType=[CODE_REVIEW], repo_url=" + request.getRepo());
        }
        return ResponseEntity.ok().body(mutatedPeerReviews);
    }

}

