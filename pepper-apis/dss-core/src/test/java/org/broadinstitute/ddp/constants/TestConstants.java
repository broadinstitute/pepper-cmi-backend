package org.broadinstitute.ddp.constants;

import org.broadinstitute.ddp.client.Auth0ManagementClient;
import org.broadinstitute.ddp.model.activity.types.InstitutionType;
import org.broadinstitute.ddp.model.user.UserProfile;

public class TestConstants {

    /**
     * base name of umbrella used for testing.  not used for demo.  inserted only during testing.
     */
    public static final String BACKEND_BASE_TEST_UMBRELLA = "btu";

    /**
     * base name study used for testing.  not used for demo.  inserted only during testing.
     */
    public static final String BACKEND_BASE_TEST_STUDY_NAME = "ddp-test-study";

    public static final String TEST_STUDY_GUID = "TESTSTUDY1";
    public static final String SECOND_STUDY_GUID = "TESTSTUDY2";

    // Some profile info for our test user
    public static final String TEST_USER_PROFILE_FIRST_NAME = "Grace";
    public static final String TEST_USER_PROFILE_LAST_NAME = "Hopper";
    public static final int TEST_USER_PROFILE_BIRTH_DAY = 9;
    public static final int TEST_USER_PROFILE_BIRTH_MONTH = 12;
    public static final int TEST_USER_PROFILE_BIRTH_YEAR = 1906;
    public static final UserProfile.SexType TEST_USER_PROFILE_SEX = UserProfile.SexType.FEMALE;
    public static final String TEST_USER_PROFILE_PREFERRED_LANGUAGE = "en";
    public static final Boolean TEST_USER_PROFILE_SKIP_LANGUAGE_POPUP = false;
    public static final String TEST_USER_MAIL_ADDRESS_STREET_NAME = "86 Brattle Street";
    public static final String TEST_USER_MAIL_ADDRESS_STREET_APT = "Apt 42";
    public static final String TEST_USER_MAIL_ADDRESS_CITY = "Cambridge";
    public static final String TEST_USER_MAIL_ADDRESS_STATE = "MA";
    public static final String TEST_USER_MAIL_ADDRESS_COUNTRY = "US";
    public static final String TEST_USER_MAIL_ADDRESS_ZIP = "02138";
    public static final String TEST_USER_MAIL_ADDRESS_PHONE = "555-555-5555";
    // Institution Data for test user
    public static final String TEST_INSTITUTION_GUID = "AABBCCDD97";
    public static final InstitutionType TEST_INSTITUTION_TYPE = InstitutionType.INSTITUTION;
    public static final String TEST_INSTITUTION_NAME = "Princeton-Plainsboro Teaching Hospital";
    public static final String TEST_INSTITUTION_PHYSICIAN_NAME = "House MD";
    public static final String TEST_INSTITUTION_CITY = "West Windsor Township";
    public static final String TEST_INSTITUTION_STATE = "New Jersey";

    public static final String TEST_INSTITUTION_COUNTRY = "United States";
    public static final String TEST_INSTITUTION_ZIP = "02144";
    public static final String TEST_INSTITUTION_PHONE = "617-867-5309";
    public static final String TEST_INSTITUTION_LEGACY_GUID = "GUID.GUID.GUID.123456";
    public static final String TEST_INSTITUTION_STREET = "Main street";
    // when running tests in parallel in circleci, we use this environment variable
    // to make sure that each batch of tests uses a shared user in order to minimize
    // auth0 management api rate limits and reduce the number of auth0 accounts
    public static final String DYNAMIC_SHARED_TEST_USER = "SHARED_TEST_USER";

    /**
     * Field in auth0 user's app metadata field that is used by testing
     * clients when querying user guid during registration
     */
    public static final String AUTH0_TEST_USER_GUID_FIELD = Auth0ManagementClient.APP_METADATA_TEST_GUID;


    public static String getTestStudyTissuePexEXPR(String testId, String activityUuid, String tissueStableId) {
        return "user.studies[\"" + testId + "\"].forms[\"" + activityUuid + "\"].questions[\"" + tissueStableId + "\"].answers.hasTrue()";
    }

    public static String getTestStudyBloodPexEXPR(String testId, String activityUuid, String bloodStableId) {
        return "user.studies[\"" + testId + "\"].forms[\"" + activityUuid + "\"].questions[\"" + bloodStableId + "\"].answers.hasTrue()";
    }

    public static String getTestStudyOverallPexEXPR(String testId, String activityUuid, String signatureQuestionId) {
        return "user.studies[\"" + testId + "\"].forms[\"" + activityUuid + "\"].questions[\""
                + signatureQuestionId + "\"].answers.hasText()";
    }
}
