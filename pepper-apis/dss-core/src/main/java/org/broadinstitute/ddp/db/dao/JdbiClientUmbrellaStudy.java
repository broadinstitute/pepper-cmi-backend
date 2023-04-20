package org.broadinstitute.ddp.db.dao;

import java.util.List;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiClientUmbrellaStudy extends SqlObject {

    @SqlUpdate("insert into client__umbrella_study(client_id,umbrella_study_id) values(:clientId,:studyId)"
            + " ON DUPLICATE KEY UPDATE client_id =:clientId, umbrella_study_id = :studyId")
    @GetGeneratedKeys
    Long insert(@Bind("clientId") long clientId, @Bind("studyId") long studyId);

    /**
     * Upserts a row if it doesn't exist
     */
    @SqlUpdate("insert into client__umbrella_study(client_id,umbrella_study_id)\n"
            + " (select c.client_id, s.umbrella_study_id\n"
            + " from client c, umbrella_study s\n"
            + " where c.auth0_client_id = :auth0ClientId and s.guid = :studyGuid)\n"
            + " ON DUPLICATE KEY UPDATE client_id = (select c.client_id from client c where c.auth0_client_id = :auth0ClientId), \n"
            + "                         umbrella_study_id = (select s.umbrella_study_id from umbrella_study s where s.guid = :studyGuid)")
    @GetGeneratedKeys
    Long upsert(@Bind("auth0ClientId") String clientId, @Bind("studyGuid") String studyGuid);

    @SqlQuery("select us.guid from client__umbrella_study as cus "
            + "join client as c on c.client_id = cus.client_id "
            + "join umbrella_study as us on us.umbrella_study_id = cus.umbrella_study_id "
            + "join auth0_tenant t on t.auth0_tenant_id = c.auth0_tenant_id "
            + "where c.auth0_client_id = :auth0ClientId and t.auth0_domain = :auth0Domain "
            + "and not c.is_revoked")
    List<String> findPermittedStudyGuidsByAuth0ClientIdAndAuth0Domain(
            @Bind("auth0ClientId") String auth0ClientId,
            @Bind("auth0Domain") String auth0Domain
    );

    // Left for backward compatibility
    @SqlQuery("select us.guid from client__umbrella_study as cus "
            + "join client as c on c.client_id = cus.client_id "
            + "join umbrella_study as us on us.umbrella_study_id = cus.umbrella_study_id "
            + "join auth0_tenant t on t.auth0_tenant_id = us.auth0_tenant_id "
            + "where c.auth0_tenant_id = t.auth0_tenant_id "
            + "and c.auth0_client_id = :auth0ClientId"
            + "and not c.is_revoked")
    List<String> findPermittedStudyGuidsByAuth0ClientId(
            @Bind("auth0ClientId") String auth0ClientId
    );

    @SqlQuery("select us.guid from client__umbrella_study as cus "
            + "join client as c on c.client_id = cus.client_id "
            + "join umbrella_study as us on us.umbrella_study_id = cus.umbrella_study_id "
            + "where c.auth0_client_id = :auth0ClientId and c.auth0_tenant_id = :auth0TenantId "
            + "and not c.is_revoked")
    List<String> findPermittedStudyGuidsByAuth0ClientIdAndAuth0TenantId(
            @Bind("auth0ClientId") String auth0ClientId,
            @Bind("auth0TenantId") long auth0TenantId
    );

    /**
     * This is delete function works off client tables client_id NOT auth0ClientId
     * @return number of rows deleted, which could be more than one
     */
    @SqlUpdate("DELETE FROM "
            + "     client__umbrella_study "
            + "WHERE "
            + "     client_id = :clientId"
    )
    int deleteByInternalClientId(@Bind("clientId") Long clientId);
}
