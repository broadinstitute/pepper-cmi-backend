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
import org.broadinstitute.ddp.client.ApiResult;
import org.broadinstitute.ddp.client.Auth0ManagementClient;
import org.broadinstitute.ddp.constants.Auth0Constants;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.constants.TestConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.AuthDao;
import org.broadinstitute.ddp.db.dao.ClientDao;
import org.broadinstitute.ddp.db.dao.JdbiAuth0Tenant;
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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

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
    public SharedTestUser getSharedTestUser() {
        if (testUser == null) {
            Config auth0Config = configManager.getConfig().getConfig("auth0");
            String auth0ClientId = auth0Config.getString(ConfigFile.BACKEND_AUTH0_TEST_CLIENT_ID);
            String auth0Secret = auth0Config.getString(ConfigFile.BACKEND_AUTH0_TEST_SECRET);
            String auth0clientName = auth0Config.getString(ConfigFile.BACKEND_AUTH0_TEST_CLIENT_NAME);
            String auth0Domain = auth0Config.getString(ConfigFile.DOMAIN);
            String mgmtClientId = auth0Config.getString(AUTH0_MGMT_API_CLIENT_ID);
            String mgmtSecret = auth0Config.getString(AUTH0_MGMT_API_CLIENT_SECRET);
            testUser = createNewTestUser(auth0Domain, auth0clientName, auth0ClientId, auth0Secret,
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

    public SharedTestUser getSharedAdminTestUser() {
        if (adminTestUser == null) {
            adminTestUser = createNewTestUser(getSharedUserEmailFromEnvironment(true));
            TransactionWrapper.useTxn(handle -> {
                StudyDto testStudy = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(TestConstants.TEST_STUDY_GUID);
                handle.attach(AuthDao.class).assignStudyAdmin(adminTestUser.getUserId(), testStudy.getId());
            });
        }
        return adminTestUser;
    }

    /**
     * Create a new test user using auth0 configuration
     * from the config file
     */
    public SharedTestUser createNewTestUser() {
        return TransactionWrapper.withTxn(handle -> {
            return createNewTestUser(null);
        });
    }

    private SharedTestUser createNewTestUser(String testUserEmail) {
        Config auth0Config = configManager.getConfig().getConfig("auth0");
        String auth0ClientId = auth0Config.getString(ConfigFile.BACKEND_AUTH0_TEST_CLIENT_ID);
        String auth0Secret = auth0Config.getString(ConfigFile.BACKEND_AUTH0_TEST_SECRET);
        String auth0clientName = auth0Config.getString(ConfigFile.BACKEND_AUTH0_TEST_CLIENT_NAME);
        String auth0Domain = auth0Config.getString(ConfigFile.DOMAIN);
        String mgmtClientId = auth0Config.getString(AUTH0_MGMT_API_CLIENT_ID);
        String mgmtSecret = auth0Config.getString(AUTH0_MGMT_API_CLIENT_SECRET);
        return createNewTestUser(auth0Domain, auth0clientName, auth0ClientId, auth0Secret,
                mgmtClientId, mgmtSecret, testUserEmail);
    }

    public SharedTestUser createNewTestUser(String auth0Domain, String auth0clientName,
                                            String auth0ClientId, String auth0Secret, String mgmtClientId,
                                            String mgmtSecret) {
        Config cfg = configManager.getConfig();
        Config auth0Config = cfg.getConfig("auth0");
        String testUserPassword = auth0Config.getString(ConfigFile.Auth0Testing.AUTH0_TEST_PASSWORD);
        String encryptionKey = auth0Config.getString(ConfigFile.ENCRYPTION_SECRET);
        return setupTestUser(auth0Domain, mgmtClientId, mgmtSecret, testUserPassword, auth0ClientId,
                auth0Secret, encryptionKey, auth0clientName, null);
    }

    private SharedTestUser createNewTestUser(String auth0Domain, String auth0clientName,
                                            String auth0ClientId, String auth0Secret, String mgmtClientId,
                                            String mgmtSecret, String testUserEmail) {
        Config cfg = configManager.getConfig();
        Config auth0Config = cfg.getConfig("auth0");
        String testUserPassword = auth0Config.getString(ConfigFile.Auth0Testing.AUTH0_TEST_PASSWORD);
        String encryptionKey = auth0Config.getString(ConfigFile.ENCRYPTION_SECRET);
        return setupTestUser(auth0Domain, mgmtClientId, mgmtSecret, testUserPassword, auth0ClientId,
                auth0Secret, encryptionKey, auth0clientName, testUserEmail);
    }


    public static synchronized SharedTestUserUtil getInstance() {
        if (instance == null) {
            instance = new SharedTestUserUtil(ConfigManager.getInstance());
        }
        return instance;
    }

    private SharedTestUser setupTestUser(String auth0Domain,
                                         String mgmtClientId,
                                         String mgmtClientSecret,
                                         String testUserPassword,
                                         String auth0BackendTestClientId,
                                         String auth0ClientSecret,
                                         String encryptionKey,
                                         String auth0BackendTestClientName,
                                         String testUserEmail) {

        SharedTestUser testUser = null;
        String jvmUser = System.getProperty("user.name");
        AtomicReference<User> auth0User = new AtomicReference<>();
        AtomicReference<UserDto> user = new AtomicReference<>();
        AtomicReference<UserProfile> profile = new AtomicReference<>();
        AtomicReference<Auth0TenantDto> tenantDto = new AtomicReference<>();
        AtomicReference<ClientDto> clientDto = new AtomicReference<>();

        // todo arz don't put auth0 stuff in the middle of our transactions

        AtomicReference<String> userEmail = new AtomicReference<>();
        userEmail.set("testuser-" + jvmUser + "-" + System.currentTimeMillis() + "@datadonationplatform.org");
        if (testUserEmail != null && !testUserEmail.isEmpty()) {
            userEmail.set(testUserEmail);
        }

        AtomicReference<String> existingUserGuid = new AtomicReference<>();
        var mgmtClient = new Auth0ManagementClient(auth0Domain, mgmtClientId, mgmtClientSecret);
        AtomicBoolean shouldCreateUser = new AtomicBoolean(true);
        AtomicBoolean shouldCreateAuth0User = new AtomicBoolean(true);
        ApiResult<User, APIException> existingAuth0User = mgmtClient.getAuth0UserByEmail(userEmail.get());
        if (!existingAuth0User.hasFailure()) {
            if (existingAuth0User.hasBody()) {
                auth0User.set(existingAuth0User.getBody());
                if (auth0User.get().getAppMetadata() != null) {
                    if (auth0User.get().getAppMetadata().get(AUTH0_TEST_USER_GUID_FIELD) != null) {
                        existingUserGuid.set(auth0User.get().getAppMetadata().get(AUTH0_TEST_USER_GUID_FIELD).toString());
                    }
                }
                shouldCreateAuth0User.set(false);
            }
        } else {
            throw new DDPException("Could not query auth0 user " + userEmail + ": " + existingAuth0User.getError(),
                    existingAuth0User.getThrown());
        }

        // we're using a few transactions here to avoid lengthy write locks that show up when we hit
        // auth0 rate limits because the locks break other tests that run in parallel in circle
        TransactionWrapper.useTxn(handle -> {
            JdbiUser userDao = handle.attach(JdbiUser.class);
            ClientDao clientDao = handle.attach(ClientDao.class);
            UserProfileDao profileDao = handle.attach(UserProfileDao.class);
            if (StringUtils.isNotBlank(existingUserGuid.get())) {
                user.set(userDao.findByUserGuid(existingUserGuid.get()));
                if (user.get() != null) {
                    log.info("Will use existing test user " + userEmail + ".");
                    shouldCreateUser.set(false);
                    profile.set(profileDao.findProfileByUserId(user.get().getUserId()).get());
                }
            }
            JdbiAuth0Tenant jdbiAuth0Tenant = handle.attach(JdbiAuth0Tenant.class);
            tenantDto.set(jdbiAuth0Tenant.insertIfNotExists(auth0Domain, mgmtClientId,
                    AesUtil.encrypt(mgmtClientSecret, encryptionKey)));

            log.info("Inserting new client row for auth0 client" + auth0BackendTestClientId + " tenant "
                    + tenantDto.get().getId());
            List<String> staticTestStudies = Arrays.asList(TestConstants.TEST_STUDY_GUID);
            long clientId = clientDao.registerClient(auth0BackendTestClientId, auth0ClientSecret, staticTestStudies,
                    encryptionKey, tenantDto.get().getId());
            clientDto.set(clientDao.getClientDao().getClientByAuth0ClientId(auth0BackendTestClientId).get());
        });

        if (shouldCreateUser.get()) {
            String userGuid = GuidUtils.randomUserGuid();
            String userHruid = GuidUtils.randomUserHruid();
            AtomicLong testUserId = new AtomicLong();

            if (shouldCreateAuth0User.get()) {
                log.info("Creating new test user " + testUserEmail + " with guid " + userGuid);
                ApiResult<User, APIException> createdUser = mgmtClient.createAuth0User(
                        Auth0ManagementClient.DEFAULT_DB_CONN_NAME, userEmail.get(), testUserPassword);
                if (createdUser.hasFailure()) {
                    if (createdUser.getError() != null && createdUser.getError().getStatusCode() == 409) {
                        // try to query by username.  we might have the user in auth0 but have the wrong auth0 user id
                        log.info("User " + userEmail + " already exists.  Will query by username.");
                        createdUser = mgmtClient.getAuth0UserByEmail(userEmail.get());
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
                auth0User.set(createdUser.getBody());
            } else {
                log.info("Test user {} already exists in auth0.  Querying to see if it clashes with another test in a different parallel test.", auth0User.get().getId());
                if (auth0User.get().getAppMetadata() != null && auth0User.get().getAppMetadata().containsKey(AUTH0_TEST_USER_GUID_FIELD)) {
                    String auth0AppMetadataUserGuid = auth0User.get().getAppMetadata().get(AUTH0_TEST_USER_GUID_FIELD).toString();
                    if (!userGuid.equals(auth0AppMetadataUserGuid)) {
                        throw new DDPException("Auth0 test user " + auth0User.get().getId() + " can't use different user guids "
                                + userGuid + " and " + auth0AppMetadataUserGuid + ".  Expect other tests to fail with authz errors or user id/guid mismatch errors."
                                + " If this is happening in circleCI, try reducing the test_parallelism setting in the yml files.");
                    }
                }
            }

            TransactionWrapper.useTxn(handle -> {
                JdbiUser userDao = handle.attach(JdbiUser.class);
                UserProfileDao profileDao = handle.attach(UserProfileDao.class);
                user.set(userDao.findByAuth0UserId(auth0User.get().getId(), tenantDto.get().getId()));
                if (user.get() == null) {
                    // possible race condition: different VMs in circle are trying to create
                    // the shared user at the same time
                    testUserId.set(userDao.insert(
                            auth0User.get().getId(),
                            userGuid,
                            clientDto.get().getId(),
                            userHruid));
                    profile.set(TestDataSetupUtil.createTestingProfile(handle, testUserId.get(), false));
                } else {
                    testUserId.set(user.get().getUserId());
                    profile.set(profileDao.findProfileByUserId(user.get().getUserId()).get());
                }

                log.info("Saved test user " + userGuid + " in the database with auth0 user id " + auth0User.get().getId()
                        + " and db id " + testUserId);
                user.set(userDao.findByUserId(testUserId.get()));
            });

            Map<String, Object> auth0UserMetadata = new HashMap<>();
            auth0UserMetadata.put(Auth0Constants.USER_METADATA_GUID_FIELD, userGuid);
            auth0UserMetadata.put("test_jvm_user", jvmUser);

            var result = mgmtClient.updateUserMetadata(auth0User.get().getId(), auth0UserMetadata);

            if (result.hasThrown() || result.hasError()) {
                var e = result.hasThrown() ? result.getThrown() : result.getError();
                throw new DDPException("Could not update auth0 metadata for test user with auth0 id "
                        + auth0User.get().getId() + " and guid " + userGuid, e);
            }

            Map<String, Object> auth0UserAppMetadata = new HashMap<>();
            auth0UserAppMetadata.put(AUTH0_TEST_USER_GUID_FIELD, userGuid);
            result = mgmtClient.updateUserAppMetadata(auth0User.get().getId(), auth0UserAppMetadata);

            if (result.hasThrown() || result.hasError()) {
                var e = result.hasThrown() ? result.getThrown() : result.getError();
                throw new DDPException("Could not update auth0 app metadata for test user with auth0 id "
                        + auth0User.get().getId() + " and guid " + userGuid, e);
            }
            log.info("Updated auth0 test user " + auth0User.get().getId() + " metadata to reference guid "
                    + userGuid);
        }

        testUser = new SharedTestUser(user.get().getUserId(), userEmail.get(), testUserPassword, user.get().getUserGuid(),
                user.get().getUserHruid(), clientDto.get().getId(), auth0User.get().getId(), profile.get(), auth0Domain,
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
