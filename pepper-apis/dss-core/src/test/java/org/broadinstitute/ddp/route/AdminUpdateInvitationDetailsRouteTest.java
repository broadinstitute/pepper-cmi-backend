package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static org.junit.Assert.assertEquals;

import io.restassured.mapper.ObjectMapperType;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.constants.TestConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.InvitationDao;
import org.broadinstitute.ddp.db.dao.InvitationFactory;
import org.broadinstitute.ddp.db.dao.InvitationSql;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.json.admin.UpdateInvitationDetailsPayload;
import org.broadinstitute.ddp.util.SharedTestUserUtil;
import org.junit.BeforeClass;
import org.junit.Test;

public class AdminUpdateInvitationDetailsRouteTest extends IntegrationTestSuite.TestCase {

    private static String urlTemplate;

    private static SharedTestUserUtil.SharedTestUser testAdminUser;

    private static SharedTestUserUtil.SharedTestUser testUser;

    private static StudyDto testStudy = null;

    @BeforeClass
    public static void setupData() {
        urlTemplate = RouteTestUtil.getTestingBaseUrl() + RouteConstants.API.ADMIN_STUDY_INVITATION_DETAILS
                .replace(RouteConstants.PathParam.STUDY_GUID, "{study}");
        TransactionWrapper.useTxn(handle ->  {
            testUser = SharedTestUserUtil.getInstance().getSharedTestUser(handle);
            testAdminUser = SharedTestUserUtil.getInstance().getSharedAdminTestUser(handle);
            testStudy = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(TestConstants.TEST_STUDY_GUID);
        });
    }

    @Test
    public void testNotStudyAdmin() {
        var payload = new UpdateInvitationDetailsPayload("foobar", "notes notes");
        given().auth().oauth2(testUser.getToken())
                .pathParam("study", testStudy.getGuid())
                .body(payload, ObjectMapperType.GSON)
                .when().patch(urlTemplate)
                .then().assertThat()
                .statusCode(401);
    }

    @Test
    public void testInvitationNotFound() {
        var payload = new UpdateInvitationDetailsPayload("foobar", "notes notes");
        given().auth().oauth2(testAdminUser.getToken())
                .pathParam("study", testStudy.getGuid())
                .body(payload, ObjectMapperType.GSON)
                .when().post(urlTemplate)
                .then().assertThat()
                .statusCode(404);
    }

    @Test
    public void testNotesAreUpdated() {
        var invitation = TransactionWrapper.withTxn(handle -> handle.attach(InvitationFactory.class)
                .createRecruitmentInvitation(testStudy.getId(), "invite" + System.currentTimeMillis()));
        try {
            var payload = new UpdateInvitationDetailsPayload(invitation.getInvitationGuid(), "notes notes");
            given().auth().oauth2(testAdminUser.getToken())
                    .pathParam("study", testStudy.getGuid())
                    .body(payload, ObjectMapperType.GSON)
                    .when().post(urlTemplate)
                    .then().assertThat().statusCode(200);
            TransactionWrapper.useTxn(handle -> {
                var actual = handle.attach(InvitationDao.class)
                        .findByInvitationGuid(testStudy.getId(), invitation.getInvitationGuid());
                assertEquals("notes notes", actual.get().getNotes());
            });
        } finally {
            if (invitation != null) {
                TransactionWrapper.useTxn(handle -> handle.attach(InvitationSql.class)
                        .deleteById(invitation.getInvitationId()));
            }
        }
    }
}
