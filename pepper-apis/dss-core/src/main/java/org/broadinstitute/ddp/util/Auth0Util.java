package org.broadinstitute.ddp.util;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.auth0.client.mgmt.ManagementAPI;
import com.auth0.client.mgmt.filter.UserFilter;
import com.auth0.exception.APIException;
import com.auth0.exception.Auth0Exception;
import com.auth0.json.auth.TokenHolder;
import com.auth0.json.mgmt.users.User;
import com.auth0.json.mgmt.users.UsersPage;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.typesafe.config.Config;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.entity.ContentType;
import org.apache.http.util.EntityUtils;
import org.broadinstitute.ddp.client.Auth0ManagementClient;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.dao.JdbiAuth0Tenant;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dto.Auth0TenantDto;
import org.broadinstitute.ddp.db.dto.EnrollmentStatusDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.json.auth0.Auth0CallResponse;
import org.broadinstitute.ddp.security.JWTConverter;
import org.jdbi.v3.core.Handle;

@Slf4j
public class Auth0Util {
    public static final String USERNAME_PASSWORD_AUTH0_CONN_NAME = "Username-Password-Authentication";
    public static final String REFRESH_ENDPOINT = "oauth/token";
    private static final long RETRY_TIMEOUT = 1000;
    private final String baseUrl;
    // map of cached jwk providers so we don't hammer auth0
    private static final Map<String, JwkProvider> jwkProviderMap = new HashMap<>();

    public Auth0Util(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * Verifies the JWT and decodes it.  Safe to use everywhere.
     */
    public static String getVerifiedAuth0UserId(String idToken, String auth0Domain) {
        JwkProvider jwkProvider;
        synchronized (jwkProviderMap) {
            jwkProvider = jwkProviderMap.get(auth0Domain);
            if (jwkProvider == null) {
                jwkProvider = new JwkProviderBuilder(auth0Domain).cached(100, 3L, TimeUnit.HOURS).build();
                jwkProviderMap.put(auth0Domain, jwkProvider);
                log.info("Created new jwk provider for {} ", auth0Domain);
            }
        }
        DecodedJWT verifiedJWT = JWTConverter.verifyDDPToken(idToken, jwkProvider);
        return verifiedJWT.getSubject();
    }

    /**
     * Get management api client using credentials for the given domain.
     */
    public static Auth0ManagementClient getManagementClientForDomain(Handle handle, String auth0Domain) {
        Auth0TenantDto auth0TenantDto = handle.attach(JdbiAuth0Tenant.class).findByDomain(auth0Domain);
        return new Auth0ManagementClient(
                auth0TenantDto.getDomain(),
                auth0TenantDto.getManagementClientId(),
                auth0TenantDto.getManagementClientSecret());
    }

    public static boolean isUserCredentialsValid(
            String userGuid, String userName, String password, String auth0ClientId, Handle handle) {

        Auth0TenantDto auth0TenantDto = handle.attach(JdbiAuth0Tenant.class).findByUserGuid(userGuid);
        String studyGuid;
        List<EnrollmentStatusDto> enrollmentList = handle.attach(JdbiUserStudyEnrollment.class).findByUserGuid(userGuid);
        if (CollectionUtils.isNotEmpty(enrollmentList)) {
            studyGuid = enrollmentList.get(0).getStudyGuid();
            //todo need to revisit to handle users registered in multiple studies
        } else {
            log.warn("User : {} not enrolled in any study ", userGuid);
            return false;
        }

        boolean isValid = false;
        try {
            isValid = verifyAuth0UserCredentials(auth0ClientId, auth0TenantDto.getDomain(),
                    userName, password, studyGuid);
        } catch (IOException e) {
            log.warn("Attempt to verify auth0 user: {} by user credentials failed with error: {}", userGuid, e);
        }
        return isValid;
    }

    private static boolean verifyAuth0UserCredentials(String auth0ClientId, String auth0Domain,
                                                      String userName, String password, String studyGuid) throws IOException {

        //try to get new token with passed username and pwd. If call is SUCCESS, its valid credentials
        UserLoginPayload payload =
                new UserLoginPayload(auth0ClientId, userName, password, studyGuid);
        Request request = Request.Post(auth0Domain + Auth0Util.REFRESH_ENDPOINT)
                .bodyString(new Gson().toJson(payload), ContentType.APPLICATION_JSON);
        return request.execute().handleResponse(httpResponse -> {
            String responseString = EntityUtils.toString(httpResponse.getEntity());
            int status = httpResponse.getStatusLine().getStatusCode();
            if (status == 200) {
                return true;
            } else {
                log.warn("Attempt to verify user: {} by user credentials failed with status: {} : Error: {}",
                        userName, status, responseString);
                return false;
            }
        });
    }

    /**
     * Updates the user's data in Auth0
     *
     * @param mgmtAPI     Management API instance used to call Auth0
     * @param userDto     Holds the user GUID and Auth0 ID besides other data
     * @param newUserData A new user data to be updated in Auth0
     * @return The result of the Auth0 call
     */
    private static Auth0CallResponse updateUserData(ManagementAPI mgmtAPI, UserDto userDto, User newUserData) {
        if (userDto.getAuth0UserId().isEmpty()) {
            throw new DDPException("can not update user data for a non-Auth0 user");
        }

        // Calling Auth0
        var userGuid = userDto.getUserGuid();
        var auth0UserId = userDto.getAuth0UserId().get();
        log.info("Trying to update the data for the user {}. Auth0 user id = {}", userGuid, userDto.getAuth0UserId());

        try {
            retryIfRateLimited(mgmtAPI.users().update(auth0UserId, newUserData));
        } catch (APIException e) {
            // A specific Auth0 API issue occurred. Relay the status code
            String errMsg = "Auth0 API call failed with the code " + e.getStatusCode() + ". Reason: " + e.getMessage()
                    + ". Description: " + e.getDescription();
            log.error(errMsg + ". User GUID: {}, Auth0 user id {}", userGuid, userDto.getAuth0UserId(), e);
            return new Auth0CallResponse(e.getStatusCode(), errMsg);
        } catch (Auth0Exception e) {
            // An unspecified Auth0 API issue occurred. Return HTTP 500
            String errMsg = "Auth0 API call failed. Reason: " + e.getMessage();
            log.error(errMsg + ". User GUID: {}, Auth0 user id {}", userGuid, userDto.getAuth0UserId(), e);
            return new Auth0CallResponse(500, errMsg);
        }
        // All fine
        log.info("Data for the user {} (Auth0 user id {}) was updated successfully", userGuid, userDto.getAuth0UserId());
        return new Auth0CallResponse(200, null);
    }

    /**
     * Updates the user's email in Auth0
     *
     * @param mgmtAPI    Auth0 Management API instance
     * @param userDto    user for which you want to update the email
     * @param newEmail   A new email of this user
     * @param isVerified Whether email is verified or not, will not set status if `null` (so we get the default behavior)
     * @return The result of the Auth0 call
     */
    public static Auth0CallResponse updateUserEmail(
            ManagementAPI mgmtAPI,
            UserDto userDto,
            String newEmail,
            Boolean isVerified
    ) {
        User payload = new User();
        payload.setEmail(newEmail);
        if (isVerified != null) {
            payload.setEmailVerified(isVerified);
        }
        log.info("Trying to set the email to {} for the user {}", newEmail, userDto.getUserGuid());
        return updateUserData(mgmtAPI, userDto, payload);
    }

    /**
     * Updates the user's password in Auth0
     *
     * @param mgmtAPI     Auth0 Management API instance
     * @param userDto     user for which you want to update the password
     * @param newPassword A new password of this user
     * @return The result of the Auth0 call
     */
    public static Auth0CallResponse updateUserPassword(
            ManagementAPI mgmtAPI,
            UserDto userDto,
            String newPassword
    ) {
        User payload = new User();
        payload.setPassword(newPassword);
        log.info("Trying to set the password for the user {}", userDto.getUserGuid());
        return updateUserData(mgmtAPI, userDto, payload);
    }


    /**
     * Exchanges the auth0 one-time code for a refresh token, id token, and access token.
     *
     * @param authCode    the code value returned from auth0 during login
     * @param redirectUri the url of the site the user logged in from
     */
    public RefreshTokenResponse getRefreshTokenFromCode(String authCode, String auth0ClientId,
                                                        String auth0ClientSecret, String redirectUri) {
        RequestRefreshTokenPayload payload = new RequestRefreshTokenPayload(auth0ClientId,
                auth0ClientSecret, authCode, redirectUri);
        Request request = Request.Post(baseUrl + REFRESH_ENDPOINT)
                .bodyString(new Gson().toJson(payload), ContentType.APPLICATION_JSON);

        String responseBody;
        try {
            AtomicBoolean responseOk = new AtomicBoolean(false);
            responseBody = retryIfRateLimited(request).handleResponse(httpResponse -> {
                responseOk.set(200 == httpResponse.getStatusLine().getStatusCode());
                return EntityUtils.toString(httpResponse.getEntity());
            });
            if (!responseOk.get()) {
                throw new RuntimeException("Could not get refresh token.  Auth0 responded with " + responseBody);
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not get a refresh token", e);
        }
        return new Gson().fromJson(responseBody, RefreshTokenResponse.class);
    }

    public static <T> T retryIfRateLimited(Request req, ResponseHandler<T> responseHandler) throws IOException {
        Response res = req.execute();
        return res.handleResponse(httpResponse -> {
            if (httpResponse.getStatusLine().getStatusCode() == 429) {
                sleepBeforeRetry();
                return req.execute().handleResponse(responseHandler);
            } else {
                return res.handleResponse(responseHandler);
            }
        });
    }

    /**
     * Uses the refreshToken to re-issue an access and id token.
     *
     * @param refreshToken the refresh token returned from
     *                     {@link #getRefreshTokenFromCode(String, String, String, String)}
     */
    public RefreshTokenResponse refreshToken(String auth0ClientId, String auth0ClientSecret, String refreshToken) {
        RefreshTokenPayload payload = new RefreshTokenPayload(auth0ClientId, auth0ClientSecret, refreshToken);
        Request request = Request.Post(baseUrl + REFRESH_ENDPOINT)
                .bodyString(new Gson().toJson(payload), ContentType.APPLICATION_JSON);

        try {
            return retryIfRateLimited(request, (httpResponse -> {
                int status = httpResponse.getStatusLine().getStatusCode();
                if (status == 200) {
                    return new Gson().fromJson(EntityUtils.toString(httpResponse.getEntity()),
                            RefreshTokenResponse.class);
                } else {
                    throw new RuntimeException("Attempt to refresh token returned " + status + ":"
                            + EntityUtils.toString(httpResponse.getEntity()));
                }
            }));
        } catch (IOException e) {
            throw new RuntimeException("Could not refresh token", e);
        }
    }

    /**
     * Creates a ManagementAPI instance eligible to manipulate the user in Auth0
     *
     * @param userGuid GUID of the user to manage
     * @return A ManagementAPI instance able to change the user's data in Auth0
     */
    public static ManagementAPI getManagementApiInstanceForUser(String userGuid, Handle handle) {
        var mgmtClient = Auth0ManagementClient.forUser(handle, userGuid);
        String mgmtToken = mgmtClient.getToken();
        String auth0Domain = mgmtClient.getDomain();
        return new ManagementAPI(auth0Domain, mgmtToken);
    }

    /**
     * Returns all users that have the given email address
     */
    public List<User> getAuth0UsersByEmail(String emailAddress, String mgmtApiToken) throws Auth0Exception {
        ManagementAPI auth0Mgmt = new ManagementAPI(baseUrl, mgmtApiToken);
        return retryIfRateLimited(auth0Mgmt.users().listByEmail(emailAddress, null));
    }

    /**
     * Returns all users that have the given email address and associated with passed connection
     */
    public List<User> getAuth0UsersByEmail(String emailAddress, String mgmtApiToken, String connection) throws Auth0Exception {
        ManagementAPI auth0Mgmt = new ManagementAPI(baseUrl, mgmtApiToken);
        String query = "email:" + emailAddress + " AND identities.connection:" + connection;
        UserFilter userFilter = new UserFilter().withQuery(query).withSearchEngine("v3");
        return retryIfRateLimited(auth0Mgmt.users().list(userFilter)).getItems();
    }

    public Map<String, String> getAuth0UsersByEmails(Set<String> emailIds, String mgmtApiToken) {
        if (emailIds == null || emailIds.isEmpty()) {
            return new HashMap<>();
        }

        int maxPerPage = 100;
        Map<String, String> results = new HashMap<>(); //<email, userId>
        List<String> ids = new ArrayList<>(emailIds);
        ManagementAPI auth0Mgmt = new ManagementAPI(baseUrl, mgmtApiToken);

        for (int i = 0; i < ids.size(); i += maxPerPage) {
            int end = Math.min(i + maxPerPage, ids.size());
            List<String> subset = ids.subList(i, end);

            String query = String.format("identities.connection:\"%s\" AND email:(%s)",
                    USERNAME_PASSWORD_AUTH0_CONN_NAME, String.join(" ", subset));

            UserFilter filter = new UserFilter()
                    .withFields("user_id,email", true)
                    .withPage(0, 100)
                    .withQuery(query)
                    .withSearchEngine("v3");

            try {
                UsersPage page = retryIfRateLimited(auth0Mgmt.users().list(filter));
                for (User user : page.getItems()) {
                    results.put(user.getEmail(), user.getId());
                }
            } catch (Auth0Exception e) {
                log.error("Error while retrieving auth0 user ids via email lookup, continuing pagination", e);
            }
        }

        return results;
    }

    /**
     * Finds email for each auth0UserId. Since Auth0 API is paginated, this will attempt to work through all given auth0UserIds but any
     * API errors during the process will be consumed. This means results might not contain all requested user emails and caller should
     * handle such scenario.
     *
     * @param auth0UserIds    the auth0UserIds to query emails for
     * @param mgmtApiToken    management token to access API
     * @param auth0Connection the auth0 connection, if not provided will fallback to 'Username-Password-Authentication'
     * @return mapping of auth0UserId to email
     */
    public Map<String, String> getEmailsByAuth0UserIdsAndConnection(Set<String> auth0UserIds, String mgmtApiToken,
                                                                    String auth0Connection) {
        if (auth0UserIds == null || auth0UserIds.isEmpty()) {
            return new HashMap<>();
        }

        int maxPerPage = 50;
        Map<String, String> results = new HashMap<>();
        List<String> ids = new ArrayList<>(auth0UserIds);
        ManagementAPI auth0Mgmt = new ManagementAPI(baseUrl, mgmtApiToken);

        // IMPORTANT: In order to satisfy certain limits and restrictions, especially URL length limits, we "pagination"
        // through the auth0UserIds. We had issues with 100 auth0UserIds, so 50 in the Lucene query seems like a safe bet.
        // 50 also happens to be Auth0's default for this parameter.
        for (int i = 0; i < ids.size(); i += maxPerPage) {
            int end = Math.min(i + maxPerPage, ids.size());
            List<String> subset = ids.subList(i, end);

            // NOTE: Lucene syntax likes OR operator, but using a space also works. We do the latter to save space on URL limit.
            String query = String.format("user_id:(%s)", String.join(" ", subset));

            UserFilter filter = new UserFilter()
                    .withFields("user_id,email", true)
                    .withPage(0, maxPerPage)
                    .withQuery(query)
                    .withSearchEngine("v3");

            try {
                UsersPage page = retryIfRateLimited(auth0Mgmt.users().list(filter));
                for (User user : page.getItems()) {
                    results.put(user.getId(), user.getEmail());
                }
            } catch (Auth0Exception e) {
                log.error("Error while retrieving auth0 user emails, continuing pagination", e);
            }
        }

        return results;
    }

    /**
     * Creates a new, unregistered user
     */
    public static TestingUser createTestingUser(Auth0ManagementClient mgmtClient) {
        String email = "test+" + System.currentTimeMillis() + "@datadonationplatform.org";
        String password = Math.random() + "Aa1";
        String connection = Auth0ManagementClient.DEFAULT_DB_CONN_NAME;
        var result = mgmtClient.createAuth0User(connection, email, password);
        if (result.hasFailure()) {
            throw new RuntimeException(result.hasThrown() ? result.getThrown() : result.getError());
        }
        User createdUser = result.getBody();
        return new TestingUser(createdUser.getId(), createdUser.getEmail(), password);
    }

    /**
     * Creates a new user (unregistered migration user)
     */
    public User createAuth0User(String emailId, String pwd, String mgmtApiToken) throws Auth0Exception {
        ManagementAPI auth0Mgmt = new ManagementAPI(baseUrl, mgmtApiToken);
        User user = new User();
        user.setEmail(emailId);
        user.setPassword(pwd);
        user.setConnection(USERNAME_PASSWORD_AUTH0_CONN_NAME);
        return retryIfRateLimited(auth0Mgmt.users().create(user));
    }

    /**
     * Auth0 account deletion .
     * You probably should not ever call this unless no other option.
     */
    public static void deleteUserFromAuth0(Handle handle, Config cfg, String email) throws Auth0Exception {
        Config auth0Config = cfg.getConfig(ConfigFile.AUTH0);
        String auth0Domain = auth0Config.getString(ConfigFile.DOMAIN);
        log.info("Domain : {} ", auth0Domain);

        Auth0TenantDto auth0TenantDto = handle.attach(JdbiAuth0Tenant.class).findByDomain(auth0Domain);
        var mgmtClient = new Auth0ManagementClient(
                auth0Domain,
                auth0TenantDto.getManagementClientId(),
                auth0TenantDto.getManagementClientSecret());

        String connectionId = mgmtClient
                .getConnectionByName(Auth0ManagementClient.DEFAULT_DB_CONN_NAME)
                .getBody().getId();

        String mgmtToken = mgmtClient.getToken();
        ManagementAPI mgmtAPI = new ManagementAPI(auth0Domain, mgmtToken);
        retryIfRateLimited(mgmtAPI.connections().deleteUser(connectionId, email));
    }

    /**
     * Generate a JWT token with a short expiration time. Useful for creating auth tokens required by DSM.
     *
     * @param algorithm the algorithm to sign the token with
     * @param issuer    the token issuer
     * @return token string
     * @throws com.auth0.jwt.exceptions.JWTCreationException if failed to create token
     */
    public static String generateShortLivedJwtToken(Algorithm algorithm, String issuer) {
        long expiresAt = Instant.now().plus(1, ChronoUnit.MINUTES).toEpochMilli();
        JWTCreator.Builder builder = JWT.create();
        builder.withIssuer(issuer);
        builder.withExpiresAt(new Date(expiresAt));
        return builder.sign(algorithm);
    }

    public static Response retryIfRateLimited(Request req) throws IOException {
        Response response = req.execute();
        int statusCode = response.returnResponse().getStatusLine().getStatusCode();
        if (statusCode == 429) {
            log.warn("Pausing for retry after hitting rate limit.");
            sleepBeforeRetry();
            return req.execute();
        } else {
            return response;
        }
    }

    private static void sleepBeforeRetry() {
        try {
            Thread.sleep(RETRY_TIMEOUT);
        } catch (InterruptedException e) {
            log.error("Interrupted during sleep", e);
        }
    }

    public static <T> T retryIfRateLimited(com.auth0.net.Request<T> req) throws Auth0Exception {
        try {
            return req.execute();
        } catch (APIException e) {
            if (e.getStatusCode() == 429) {
                log.warn("Pausing for retry after hitting rate limit.");
                sleepBeforeRetry();
                return req.execute();
            } else {
                throw e;
            }
        }
    }

    /**
     * Wrapper class around {@link User} and {@link TokenHolder} that
     * keeps track of password, user guid, and post-logged-in token.
     */
    @Data
    @AllArgsConstructor
    public static class TestingUser {
        private Long userId;
        private String userGuid;
        private String userHruid;

        private String auth0Id;
        private String email;
        private String password;
        private String token;

        public TestingUser(String auth0Id, String email, String password) {
            this.auth0Id = auth0Id;
            this.email = email;
            this.password = password;
        }
    }

    private static class RefreshTokenPayload {
        @SerializedName("grant_type")
        private String grantType = "refresh_token";

        @SerializedName("client_id")
        private String clientId;

        @SerializedName("client_secret")
        private String clientSecret;

        @SerializedName("refresh_token")
        private String refreshToken;

        @SerializedName("scope")
        private String scope = "openid profile";

        public RefreshTokenPayload(String clientId, String clientSecret, String refreshToken) {
            this.clientId = clientId;
            this.clientSecret = clientSecret;
            this.refreshToken = refreshToken;
        }
    }

    @Getter
    public static class RefreshTokenResponse {
        @SerializedName("id_token")
        private String idToken;

        @SerializedName("access_token")
        private String accessToken;

        @SerializedName("expires_in")
        private int expiresIn;

        /**
         * only set on initial exchange from {@link #getRefreshTokenFromCode(String, String, String, String)}.
         **/
        @SerializedName("refresh_token")
        private String refreshToken;

        /**
         * Instantiates RefreshTokenResponse object.
         */
        public RefreshTokenResponse(String idToken, String accessToken, int expiresIn) {
            this.idToken = idToken;
            this.accessToken = accessToken;
            this.expiresIn = expiresIn;
        }
    }

    public static class RequestRefreshTokenPayload {

        @SerializedName("grant_type")
        private String grantType = "authorization_code";

        @SerializedName("client_id")
        private String auth0ClientId;

        @SerializedName("client_secret")
        private String auth0Secret;

        @SerializedName("code")
        private String auth0Code;

        @SerializedName("redirect_uri")
        private String redirectUri;

        /**
         * Instantiates RequestRefreshTokenPayload object.
         */
        public RequestRefreshTokenPayload(String auth0ClientId,
                                          String auth0Secret,
                                          String auth0Code,
                                          String redirectUri) {
            this.auth0ClientId = auth0ClientId;
            this.auth0Secret = auth0Secret;
            this.auth0Code = auth0Code;
            this.redirectUri = redirectUri;
        }

    }

    private static class UserLoginPayload {
        @SerializedName("username")
        private String userName;

        @SerializedName("password")
        private String password;

        @SerializedName("grant_type")
        private String grantType = "password";

        @SerializedName("client_id")
        private String clientId;

        @SerializedName("study_guid")
        private String studyGuid;

        public UserLoginPayload(String auth0ClientId, String userName, String password, String studyGuid) {
            this.clientId = auth0ClientId;
            this.userName = userName;
            this.password = password;
            this.studyGuid = studyGuid;
        }
    }
}
