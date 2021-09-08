package com.capitalone.dashboard.evaluator;

import com.capitalone.dashboard.ApiSettings;
import com.capitalone.dashboard.model.CollectorItem;
import com.capitalone.dashboard.model.LibraryPolicyResult;
import com.capitalone.dashboard.repository.LibraryPolicyResultsRepository;
import com.capitalone.dashboard.response.LibraryPolicyAuditResponse;
import com.capitalone.dashboard.status.LibraryPolicyAuditStatus;
import org.bson.types.ObjectId;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)

public class LibraryPolicyEvaluatorTest {

    @InjectMocks
    private LibraryPolicyEvaluator libraryPolicyEvaluator;

    @Mock
    private LibraryPolicyResultsRepository libraryPolicyResultsRepository;

    @Before
    public void setup() {
        libraryPolicyEvaluator.setSettings(getSettings());
    }

    @Test
    public void testEvaluate_LibraryScan_OK() {
        CollectorItem collectorItem = new CollectorItem();
        collectorItem.setId(ObjectId.get());
        when(libraryPolicyResultsRepository.findTopByCollectorItemIdOrderByEvaluationTimestampDesc(any(ObjectId.class))).thenReturn(getLibraryPolicyResult());
        LibraryPolicyAuditResponse response = libraryPolicyEvaluator.evaluate(collectorItem, 125634536, 6235243, null);
        Assert.assertEquals(true, response.getAuditStatuses().toString().contains(LibraryPolicyAuditStatus.LIBRARY_POLICY_AUDIT_OK.name()));
    }


    private ApiSettings getSettings() {
        ApiSettings settings = new ApiSettings();
        settings.setCriticalLicenseVulnerabilitiesAge(0);
        settings.setCriticalLicenseVulnerabilitiesAge(0);
        settings.setHighLicenseVulnerabilitiesAge(30);
        settings.setHighSecurityVulnerabilitiesAge(30);
        return settings;
    }

    private LibraryPolicyResult getLibraryPolicyResult() {
        LibraryPolicyResult libraryPolicyResult = new LibraryPolicyResult();
        libraryPolicyResult.setTimestamp(0);
        libraryPolicyResult.setEvaluationTimestamp(1631040519000L);
        libraryPolicyResult.setReportUrl("https://WS.com/123456");
        return libraryPolicyResult;
    }
}
