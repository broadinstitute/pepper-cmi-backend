package org.broadinstitute.ddp.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.fluent.Request;
import org.apache.http.util.EntityUtils;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiMailAddress;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.model.address.OLCPrecision;
import org.broadinstitute.ddp.model.study.StudyParticipantsInfo;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.service.OLCService;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

@Slf4j
public class GetParticipantInfoRouteStandaloneTest extends IntegrationTestSuite.TestCase {
    private static Gson gson;

    private static TestDataSetupUtil.GeneratedTestData testData;
    private final Set<String> mailAddressesToDelete = new HashSet<>();

    @BeforeClass
    public static void beforeClass() {
        gson = new Gson();
        testData = TestDataSetupUtil.generateBasicUserTestData();
    }

    @After
    public void breakdown() {
        TransactionWrapper.useTxn(handle -> mailAddressesToDelete.forEach(guid ->
                assertTrue(handle.attach(JdbiMailAddress.class).deleteAddressByGuid(guid))));
    }

    private StudyParticipantsInfo getEnrolledParticipantInfo(int expectedHttpStatus) throws IOException {
        String url = RouteTestUtil.getTestingBaseUrl()
                + RouteConstants.API.PARTICIPANTS_INFO_FOR_STUDY.replace(RouteConstants.PathParam.STUDY_GUID, testData.getStudyGuid());
        HttpResponse httpResponse = Request.Get(url).execute().returnResponse();
        assertEquals(expectedHttpStatus, httpResponse.getStatusLine().getStatusCode());
        log.info("Returned body : {}", EntityUtils.toString(httpResponse.getEntity()));

        String entity = EntityUtils.toString(httpResponse.getEntity());
        return gson.fromJson(entity, StudyParticipantsInfo.class);
    }

    @Test
    public void testHappyPath() throws IOException {
        OLCPrecision studyPrecision = testData.getTestingStudy().getOlcPrecision();
        TransactionWrapper.useTxn(handle -> {
            JdbiUserStudyEnrollment jdbiUserStudyEnrollment = handle.attach(JdbiUserStudyEnrollment.class);
            jdbiUserStudyEnrollment
                    .changeUserStudyEnrollmentStatus(testData.getUserGuid(), testData.getStudyGuid(), EnrollmentStatusType.ENROLLED);

            mailAddressesToDelete.add(TestDataSetupUtil.createTestingMailAddress(handle, testData).getGuid());
        });
        StudyParticipantsInfo participantsInfo = getEnrolledParticipantInfo(HttpStatus.SC_OK);
        assertNotNull(participantsInfo);
        assertEquals(1, participantsInfo.getParticipantInfoList().size());

        String plusCodeForStudyToCorrectPrecision = OLCService
                .convertPlusCodeToPrecision(
                        testData.getMailAddress().getPlusCode(),
                        studyPrecision);
        assertEquals(plusCodeForStudyToCorrectPrecision, participantsInfo.getParticipantInfoList().get(0).getLocation());
    }

    @Test
    public void testReturnsNullIfNotConfiguredCorrectly_missingPrecision() throws IOException {
        try {
            TransactionWrapper.useTxn(handle -> {
                int rowsUpdated = handle.attach(JdbiUmbrellaStudy.class).updateOlcPrecisionForStudy(null, testData.getStudyGuid());
                assertEquals(1, rowsUpdated);
            });
            StudyParticipantsInfo participantsInfo = getEnrolledParticipantInfo(HttpStatus.SC_INTERNAL_SERVER_ERROR);
            assertTrue(CollectionUtils.isEmpty(participantsInfo.getParticipantInfoList()));
        } finally {
            int rowsUpdated = TransactionWrapper.withTxn(handle -> handle.attach(JdbiUmbrellaStudy.class)
                    .updateOlcPrecisionForStudy(OLCPrecision.MEDIUM, testData.getStudyGuid()));
            assertEquals(1, rowsUpdated);
        }

    }

    @Test
    public void testReturnsNullIfNotConfiguredCorrectly_doesNotShareLocation() throws IOException {
        try {
            TransactionWrapper.useTxn(handle -> {
                int rowsUpdated = handle.attach(JdbiUmbrellaStudy.class).updateShareLocationForStudy(false, testData.getStudyGuid());
                assertEquals(1, rowsUpdated);
            });
            StudyParticipantsInfo participantsInfo = getEnrolledParticipantInfo(HttpStatus.SC_INTERNAL_SERVER_ERROR);
            assertTrue(CollectionUtils.isEmpty(participantsInfo.getParticipantInfoList()));
        } finally {
            TransactionWrapper.useTxn(handle -> {
                int rowsUpdated = handle.attach(JdbiUmbrellaStudy.class).updateShareLocationForStudy(true, testData.getStudyGuid());
                assertEquals(1, rowsUpdated);
            });
        }

    }
}
