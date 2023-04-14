package org.broadinstitute.ddp.util;

import com.auth0.client.auth.AuthAPI;
import com.auth0.exception.APIException;
import com.auth0.exception.Auth0Exception;
import com.auth0.json.auth.TokenHolder;
import com.auth0.json.mgmt.users.User;
import com.auth0.net.AuthRequest;
import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.client.ApiResult;
import org.broadinstitute.ddp.client.Auth0ManagementClient;
import org.broadinstitute.ddp.constants.Auth0Constants;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.constants.TestConstants;
import org.broadinstitute.ddp.db.dao.AuthDao;
import org.broadinstitute.ddp.db.dao.JdbiAuth0Tenant;
import org.broadinstitute.ddp.db.dao.JdbiClient;
import org.broadinstitute.ddp.db.dao.JdbiClientUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.db.dto.Auth0TenantDto;
import org.broadinstitute.ddp.db.dto.ClientDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.user.UserProfile;
import org.broadinstitute.ddp.security.AesUtil;
import org.jdbi.v3.core.Handle;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.broadinstitute.ddp.constants.ConfigFile.Auth0Testing.AUTH0_MGMT_API_CLIENT_ID;
import static org.broadinstitute.ddp.constants.ConfigFile.Auth0Testing.AUTH0_MGMT_API_CLIENT_SECRET;
import static org.broadinstitute.ddp.constants.TestConstants.AUTH0_TEST_USER_GUID_FIELD;

@Slf4j
public class SharedTestUserUtil {

    private SharedTestUser testUser;

    private SharedTestUser adminTestUser;

    private static SharedTestUserUtil instance;

    private final ConfigManager configManager;

    private SharedTestUserUtil(ConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * Uses config file values from "auth0" secret to create
     * the shared test user if it doesn't exist already.
     * @return
     */
    public SharedTestUser getSharedTestUser(Handle handle) {
        // todo arz use seed email address from env and check that first.
        if (testUser == null) {
            Config auth0Config = configManager.getConfig().getConfig("auth0");
            String auth0ClientId = auth0Config.getString(ConfigFile.BACKEND_AUTH0_TEST_CLIENT_ID);
            String auth0Secret = auth0Config.getString(ConfigFile.BACKEND_AUTH0_TEST_SECRET);
            String auth0clientName = auth0Config.getString(ConfigFile.BACKEND_AUTH0_TEST_CLIENT_NAME);
            String auth0Domain = auth0Config.getString(ConfigFile.DOMAIN);
            String mgmtClientId = auth0Config.getString(AUTH0_MGMT_API_CLIENT_ID);
            String mgmtSecret = auth0Config.getString(AUTH0_MGMT_API_CLIENT_SECRET);
            testUser = createNewTestUser(handle, auth0Domain, auth0clientName, auth0ClientId, auth0Secret,
                    mgmtClientId, mgmtSecret, getSharedUserEmailFromEnvironment(false));
        }
        return testUser;
    }

    private static String getSharedUserEmailFromEnvironment(boolean isAdmin) {
        String testUserBase = System.getenv(TestConstants.DYNAMIC_SHARED_TEST_USER);
        String testUserEmail = null;
        if (testUserBase != null && !testUserBase.isBlank()) {
            testUserEmail = testUserBase;
            if (isAdmin) {
                testUserEmail +=  "-admin";
            }
            testUserEmail += "@datadonationplatform.org";
        }
        return testUserEmail;
    }

    public SharedTestUser getSharedAdminTestUser(Handle handle) {
        String adminUser = getSharedUserEmailFromEnvironment(true);
        if (StringUtils.isNotBlank(adminUser)) {
            // todo arz See if the user exists already.  It may have been created
            // in another parallel VM

        }
        if (adminTestUser == null) {
            adminTestUser = createNewTestUser(handle, getSharedUserEmailFromEnvironment(true));
            StudyDto testStudy = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(TestConstants.TEST_STUDY_GUID);
            handle.attach(AuthDao.class).assignStudyAdmin(adminTestUser.getUserId(), testStudy.getId());
        }
        return adminTestUser;
    }

    /**
     * Create a new test user using auth0 configuration
     * from the config file
     */
    public SharedTestUser createNewTestUser(Handle handle) {
        return createNewTestUser(handle, null);
    }

    // todo arz separate table to coordinate locking across vms when setting up test users
    private SharedTestUser createNewTestUser(Handle handle, String testUserEmail) {
        Config auth0Config = configManager.getConfig().getConfig("auth0");
        String auth0ClientId = auth0Config.getString(ConfigFile.BACKEND_AUTH0_TEST_CLIENT_ID);
        String auth0Secret = auth0Config.getString(ConfigFile.BACKEND_AUTH0_TEST_SECRET);
        String auth0clientName = auth0Config.getString(ConfigFile.BACKEND_AUTH0_TEST_CLIENT_NAME);
        String auth0Domain = auth0Config.getString(ConfigFile.DOMAIN);
        String mgmtClientId = auth0Config.getString(AUTH0_MGMT_API_CLIENT_ID);
        String mgmtSecret = auth0Config.getString(AUTH0_MGMT_API_CLIENT_SECRET);
        return createNewTestUser(handle, auth0Domain, auth0clientName, auth0ClientId, auth0Secret,
                mgmtClientId, mgmtSecret, testUserEmail);
    }

    public SharedTestUser createNewTestUser(Handle handle, String auth0Domain, String auth0clientName,
                                            String auth0ClientId, String auth0Secret, String mgmtClientId,
                                            String mgmtSecret) {
        Config cfg = configManager.getConfig();
        Config auth0Config = cfg.getConfig("auth0");
        String testUserPassword = auth0Config.getString(ConfigFile.Auth0Testing.AUTH0_TEST_PASSWORD);
        String encryptionKey = auth0Config.getString(ConfigFile.ENCRYPTION_SECRET);
        String encryptedAuth0BackendClientSecret = AesUtil.encrypt(auth0Secret, encryptionKey);
        LanguageStore.init(handle);
        return setupTestUser(handle, auth0Domain, mgmtClientId, mgmtSecret, testUserPassword, auth0ClientId,
                auth0Secret, encryptedAuth0BackendClientSecret, auth0clientName, null);
    }

    private SharedTestUser createNewTestUser(Handle handle, String auth0Domain, String auth0clientName,
                                            String auth0ClientId, String auth0Secret, String mgmtClientId,
                                            String mgmtSecret, String testUserEmail) {
        Config cfg = configManager.getConfig();
        Config auth0Config = cfg.getConfig("auth0");
        String testUserPassword = auth0Config.getString(ConfigFile.Auth0Testing.AUTH0_TEST_PASSWORD);
        String encryptionKey = auth0Config.getString(ConfigFile.ENCRYPTION_SECRET);
        String encryptedAuth0BackendClientSecret = AesUtil.encrypt(auth0Secret, encryptionKey);
        LanguageStore.init(handle);
        return setupTestUser(handle, auth0Domain, mgmtClientId, mgmtSecret, testUserPassword, auth0ClientId,
                auth0Secret, encryptedAuth0BackendClientSecret, auth0clientName, testUserEmail);
    }


    public static synchronized SharedTestUserUtil getInstance() {
        if (instance == null) {
            instance = new SharedTestUserUtil(ConfigManager.getInstance());
        }
        return instance;
    }

    private SharedTestUser setupTestUser(Handle handle,
                                         String auth0Domain,
                                         String mgmtClientId,
                                         String mgmtClientSecret,
                                         String testUserPassword,
                                         String auth0BackendTestClientId,
                                         String auth0ClientSecret,
                                         String encryptedTestClientSecret,
                                         String auth0BackendTestClientName,
                                         String testUserEmail) {

        SharedTestUser testUser = null;
        String jvmUser = System.getProperty("user.name");
        JdbiUser userDao = handle.attach(JdbiUser.class);
        JdbiClient clientDao = handle.attach(JdbiClient.class);
        JdbiUmbrellaStudy studyDao = handle.attach(JdbiUmbrellaStudy.class);
        JdbiClientUmbrellaStudy clientStudyDao = handle.attach(JdbiClientUmbrellaStudy.class);
        UserProfileDao profileDao = handle.attach(UserProfileDao.class);
        User auth0User = null;
        UserDto user = null;
        UserProfile profile = null;
        Auth0TenantDto tenantDto = null;

        String userEmail = "testuser-" + jvmUser + "-" + System.currentTimeMillis() + "@datadonationplatform.org";
        if (testUserEmail != null && !testUserEmail.isEmpty()) {
            userEmail = testUserEmail;
        }

        var mgmtClient = new Auth0ManagementClient(auth0Domain, mgmtClientId, mgmtClientSecret);
        boolean shouldCreateUser = true;
        ApiResult<User, APIException> existingAuth0User = mgmtClient.getAuth0UserByEmail(userEmail);
        if (!existingAuth0User.hasFailure()) {
            if (existingAuth0User.hasBody()) {
                auth0User = existingAuth0User.getBody();
                if (auth0User.getAppMetadata() != null) {
                    String existingUserGuid = auth0User.getAppMetadata().get(AUTH0_TEST_USER_GUID_FIELD).toString();
                    if (StringUtils.isNotBlank(existingUserGuid)) {
                        user = userDao.findByUserGuid(existingUserGuid);
                        if (user != null) {
                            log.info("Will use existing test user " + userEmail + ".");
                            shouldCreateUser = false;
                            profile = profileDao.findProfileByUserId(user.getUserId()).get();
                        }
                    }
                }
            }
        } else {
            throw new DDPException("Could not query auth0 user " + userEmail + ": " + existingAuth0User.getError(),
                    existingAuth0User.getThrown());
        }

        ClientDto clientDto = null;
        JdbiAuth0Tenant jdbiAuth0Tenant = handle.attach(JdbiAuth0Tenant.class);
        tenantDto = jdbiAuth0Tenant.insertIfNotExists(auth0Domain, mgmtClientId,
                encryptedTestClientSecret);

        Optional<ClientDto> optClientDto = clientDao.findByAuth0ClientIdAndAuth0TenantId(auth0BackendTestClientId,
                tenantDto.getId());
        if (!optClientDto.isPresent()) {
            log.info("Inserting new client row for auth0 client" + auth0BackendTestClientId);
            clientDao.insertClient(auth0BackendTestClientId, encryptedTestClientSecret,
                    tenantDto.getId(), null);
            clientDto = clientDao.findByAuth0ClientIdAndAuth0TenantId(auth0BackendTestClientId,
                    tenantDto.getId()).get();
            List<String> staticTestStudies = Arrays.asList(TestConstants.TEST_STUDY_GUID);
            for (String staticTestStudy : staticTestStudies) {
                StudyDto studyDto = studyDao.findByStudyGuid(staticTestStudy);
                if (studyDto == null) {
                    throw new DDPException("Could not find static test study " + staticTestStudy);
                }
                clientStudyDao.insert(clientDto.getId(), studyDto.getId());
                log.info("Added study {} to the list of studies for auth0 client {}", staticTestStudy,
                        auth0BackendTestClientId);
            }
        } else {
            clientDto = optClientDto.get();
        }

        if (shouldCreateUser) {
            String userGuid = GuidUtils.randomUserGuid();
            String userHruid = GuidUtils.randomUserHruid();
            long testUserId = -1;
            log.info("Creating new test user " + testUserEmail + " with guid " + userGuid);
            ApiResult<User, APIException> createdUser = mgmtClient.createAuth0User(
                    Auth0ManagementClient.DEFAULT_DB_CONN_NAME, userEmail, testUserPassword);
            if (createdUser.hasFailure()) {
                if (createdUser.getError() != null && createdUser.getError().getStatusCode() == 409) {
                    // try to query by username.  we might have the user in auth0 but have the wrong auth0 user id
                    log.info("User " + userEmail + " already exists.  Will query by username.");
                    createdUser = mgmtClient.getAuth0UserByEmail(userEmail);
                    if (createdUser.hasFailure()) {
                        throw new DDPException("Could not find test user " + userEmail + " in auth0",
                                createdUser.hasThrown() ? createdUser.getThrown() : createdUser.getError());
                    }
                } else {
                    throw new DDPException("Could not find test user " + userEmail + " in auth0",
                            createdUser.hasThrown() ? createdUser.getThrown() : createdUser.getError());
                }
            }
            log.info("Created new test auth0 user " + createdUser.getBody().getId() + " for user guid "
                    + userGuid);
            auth0User = createdUser.getBody();

            user = userDao.findByAuth0UserId(auth0User.getId(), tenantDto.getId());
            if (user == null) {
                // possible race condition: different VMs in circle are trying to create
                // the shared user at the same time
                testUserId = userDao.insert(
                        auth0User.getId(),
                        userGuid,
                        clientDto.getId(),
                        userHruid);
                profile = TestDataSetupUtil.createTestingProfile(handle, testUserId, false);
            } else {
                testUserId = user.getUserId();
                profile = profileDao.findProfileByUserId(user.getUserId()).get();
            }

            log.info("Saved test user " + userGuid + " in the database with auth0 user id " + auth0User.getId()
                    + " and db id " + testUserId);
            user = userDao.findByUserId(testUserId);

            Map<String, Object> auth0UserMetadata = new HashMap<>();
            auth0UserMetadata.put(Auth0Constants.USER_METADATA_GUID_FIELD, userGuid);
            auth0UserMetadata.put("test_jvm_user", jvmUser);

            var result = mgmtClient.updateUserMetadata(auth0User.getId(), auth0UserMetadata);

            if (result.hasThrown() || result.hasError()) {
                var e = result.hasThrown() ? result.getThrown() : result.getError();
                throw new DDPException("Could not update auth0 metadata for test user with auth0 id "
                        + auth0User.getId() + " and guid " + userGuid, e);
            }

            Map<String, Object> auth0UserAppMetadata = new HashMap<>();
            auth0UserAppMetadata.put(AUTH0_TEST_USER_GUID_FIELD, userGuid);
            result = mgmtClient.updateUserAppMetadata(auth0User.getId(), auth0UserAppMetadata);

            if (result.hasThrown() || result.hasError()) {
                var e = result.hasThrown() ? result.getThrown() : result.getError();
                throw new DDPException("Could not update auth0 app metadata for test user with auth0 id "
                        + auth0User.getId() + " and guid " + userGuid, e);
            }
            log.info("Updated auth0 test user " + auth0User.getId() + " metadata to reference guid "
                    + userGuid);
        }

        testUser = new SharedTestUser(user.getUserId(), userEmail, testUserPassword, user.getUserGuid(),
                user.getUserHruid(), clientDto.getId(), auth0User.getId(), profile, auth0Domain,
                auth0BackendTestClientId, auth0ClientSecret, auth0BackendTestClientName);
        log.info("Will use shared test user " + testUser);

        return testUser;
    }

    public static class SharedTestUser {

        private long userId;
        private final String userEmail;

        private final String userPassword;

        private final String userGuid;

        private final String userHruid;

        private final long pepperClientId;

        private final String auth0UserId;
        private final UserProfile profile;
        private final String auth0Domain;
        private final String auth0ClientId;
        private final String auth0ClientSecret;
        private final String auth0ClientName;

        private String token;

        public SharedTestUser(long userId, String userEmail, String userPassword, String userGuid, String userHruid,
                              long pepperClientId, String auth0UserId, UserProfile profile, String auth0Domain,
                              String auth0ClientId, String auth0ClientSecret, String auth0ClientName) {
            this.userId = userId;
            this.userEmail = userEmail;
            this.userPassword = userPassword;
            this.userGuid = userGuid;
            this.userHruid = userHruid;
            this.pepperClientId = pepperClientId;
            this.auth0UserId = auth0UserId;
            this.profile = profile;
            this.auth0Domain = auth0Domain;
            this.auth0ClientId = auth0ClientId;
            this.auth0ClientSecret = auth0ClientSecret;
            this.auth0ClientName = auth0ClientName;
        }

        public String getToken() {
            if (token == null) {
                AuthAPI auth = new AuthAPI(auth0Domain, auth0ClientId, auth0ClientSecret);
                AuthRequest authRequest = auth.login(userEmail, getUserPassword()).setRealm(auth0ClientName);
                TokenHolder tokenHolder = null;
                try {
                    tokenHolder = authRequest.execute();
                } catch (Auth0Exception e) {
                    throw new DDPException("Could not login and get token for " + userEmail, e);
                }

                token = tokenHolder.getIdToken();
            }
            return token;
        }

        public long getUserId() {
            return userId;
        }

        public UserProfile getProfile() {
            return profile;
        }

        public String getAuth0UserId() {
            return auth0UserId;
        }

        public String getUserEmail() {
            return userEmail;
        }

        public String getUserPassword() {
            return userPassword;
        }

        public String getUserGuid() {
            return userGuid;
        }

        public String getUserHruid() {
            return userHruid;
        }

        public long getPepperClientId() {
            return pepperClientId;
        }

        @Override
        public String toString() {
            return "SharedTestUser{"
                    + "userEmail='" + userEmail + '\''
                    + ", userPassword='" + userPassword + '\''
                    + ", userGuid='" + userGuid + '\''
                    + ", userHruid='" + userHruid + '\''
                    + ", pepperClientId=" + pepperClientId
                    + '}';
        }
    }
}
