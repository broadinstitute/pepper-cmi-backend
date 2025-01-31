package org.broadinstitute.dsm.db;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.structure.ColumnName;
import org.broadinstitute.dsm.db.structure.DbDateConversion;
import org.broadinstitute.dsm.db.structure.SqlDateConverter;
import org.broadinstitute.dsm.db.structure.TableName;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.DBUtil;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;
import org.broadinstitute.lddp.db.SimpleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@TableName(name = DBConstants.DDP_PARTICIPANT, alias = DBConstants.DDP_PARTICIPANT_ALIAS,
        primaryKey = DBConstants.PARTICIPANT_ID, columnPrefix = "")
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Participant implements Cloneable {

    private static final Logger logger = LoggerFactory.getLogger(Participant.class);

    public static final String SQL_SELECT_PARTICIPANT = "SELECT p.participant_id, p.ddp_participant_id, p.assignee_id_mr, "
            + "p.assignee_id_tissue, p.ddp_instance_id, realm.instance_name, realm.base_url, realm.mr_attention_flag_d, "
            + "realm.tissue_attention_flag_d, realm.auth0_token, realm.notification_recipients, realm.migrated_ddp, "
            + "o.onc_history_id, o.created, o.reviewed, r.cr_sent, r.cr_received, r.notes, r.minimal_mr, r.abstraction_ready, "
            + "r.additional_values_json, ex.exit_date, ex.exit_by "
            + "FROM ddp_participant p LEFT JOIN ddp_instance realm on (p.ddp_instance_id = realm.ddp_instance_id) "
            + "LEFT JOIN ddp_onc_history o on (o.participant_id = p.participant_id) "
            + "LEFT JOIN ddp_participant_record r on (r.participant_id = p.participant_id) "
            + "LEFT JOIN ddp_participant_exit ex on (p.ddp_participant_id = ex.ddp_participant_id "
            + "AND p.ddp_instance_id = ex.ddp_instance_id) WHERE realm.instance_name = ? ";

    @ColumnName(DBConstants.PARTICIPANT_ID)
    private Long participantId;

    @ColumnName(DBConstants.DDP_PARTICIPANT_ID)
    private String ddpParticipantId;

    @TableName(name = DBConstants.DDP_PARTICIPANT, alias = DBConstants.DDP_PARTICIPANT_ALIAS, primaryKey = DBConstants.PARTICIPANT_ID,
            columnPrefix = "")
    @ColumnName(DBConstants.ASSIGNEE_ID_MR)
    private String assigneeIdMr;

    @ColumnName(DBConstants.DDP_INSTANCE_ID)
    private Integer ddpInstanceId;

    @TableName(name = DBConstants.DDP_PARTICIPANT, alias = DBConstants.DDP_PARTICIPANT_ALIAS, primaryKey = DBConstants.PARTICIPANT_ID,
            columnPrefix = "")
    @ColumnName(DBConstants.ASSIGNEE_ID_TISSUE)
    private String assigneeIdTissue;
    private String realm;

    @TableName(name = DBConstants.DDP_ONC_HISTORY, alias = DBConstants.DDP_ONC_HISTORY_ALIAS, primaryKey = DBConstants.PARTICIPANT_ID,
            columnPrefix = "")
    @ColumnName(DBConstants.ONC_HISTORY_CREATED)
    private String created;

    @TableName(name = DBConstants.DDP_ONC_HISTORY, alias = DBConstants.DDP_ONC_HISTORY_ALIAS, primaryKey = DBConstants.PARTICIPANT_ID,
            columnPrefix = "")
    @ColumnName(DBConstants.ONC_HISTORY_REVIEWED)
    private String reviewed;

    @TableName(name = DBConstants.DDP_PARTICIPANT_RECORD, alias = DBConstants.DDP_PARTICIPANT_RECORD_ALIAS,
            primaryKey = DBConstants.PARTICIPANT_ID, columnPrefix = "")
    @ColumnName(DBConstants.CR_SENT)
    @DbDateConversion(SqlDateConverter.STRING_DAY)
    private String crSent;

    @TableName(name = DBConstants.DDP_PARTICIPANT_RECORD, alias = DBConstants.DDP_PARTICIPANT_RECORD_ALIAS,
            primaryKey = DBConstants.PARTICIPANT_ID, columnPrefix = "")
    @ColumnName(DBConstants.CR_RECEIVED)
    @DbDateConversion(SqlDateConverter.STRING_DAY)
    private String crReceived;

    @TableName(name = DBConstants.DDP_PARTICIPANT_RECORD, alias = DBConstants.DDP_PARTICIPANT_RECORD_ALIAS,
            primaryKey = DBConstants.PARTICIPANT_ID, columnPrefix = "")
    @ColumnName(DBConstants.NOTES)
    private String notes;

    @TableName(name = DBConstants.DDP_PARTICIPANT_RECORD, alias = DBConstants.DDP_PARTICIPANT_RECORD_ALIAS,
            primaryKey = DBConstants.PARTICIPANT_ID, columnPrefix = "")
    @ColumnName(DBConstants.MINIMAL_MR)
    private Boolean minimalMr;

    @TableName(name = DBConstants.DDP_PARTICIPANT_RECORD, alias = DBConstants.DDP_PARTICIPANT_RECORD_ALIAS,
            primaryKey = DBConstants.PARTICIPANT_ID, columnPrefix = "")
    @ColumnName(DBConstants.ABSTRACTION_READY)
    private Boolean abstractionReady;

    @TableName(name = DBConstants.DDP_PARTICIPANT_RECORD, alias = DBConstants.DDP_PARTICIPANT_RECORD_ALIAS,
            primaryKey = DBConstants.PARTICIPANT_ID, columnPrefix = "")
    @ColumnName(DBConstants.ADDITIONAL_VALUES_JSON)
    @JsonProperty("dynamicFields")
    @SerializedName("dynamicFields")
    private String additionalValuesJson;

    @JsonProperty("dynamicFields")
    public Map<String, Object> getDynamicFields() {
        return ObjectMapperSingleton.readValue(additionalValuesJson, new TypeReference<Map<String, Object>>() {
        });
    }

    @TableName(name = DBConstants.DDP_PARTICIPANT_EXIT, alias = DBConstants.DDP_PARTICIPANT_EXIT_ALIAS,
            primaryKey = DBConstants.PARTICIPANT_ID, columnPrefix = "")
    @ColumnName(DBConstants.EXIT_DATE)
    private Long exitDate;

    public Participant() {
    }

    public Participant(Long participantId, String ddpParticipantId, String assigneeIdMr, String assigneeIdTissue, String instanceName,
                       String created, String reviewed, String crSent, String crReceived, String notes, Boolean minimalMr,
                       Boolean abstractionReady, String additionalValuesJson, Long exitDate) {
        this.participantId = participantId;
        this.ddpParticipantId = ddpParticipantId;
        this.assigneeIdMr = assigneeIdMr;
        this.assigneeIdTissue = assigneeIdTissue;
        this.realm = instanceName;
        this.created = created;
        this.reviewed = reviewed;
        this.crSent = crSent;
        this.crReceived = crReceived;
        this.notes = notes;
        this.minimalMr = minimalMr;
        this.abstractionReady = abstractionReady;
        this.additionalValuesJson = additionalValuesJson;
        this.exitDate = exitDate;
    }

    public Participant(Long participantId, String ddpParticipantId, String assigneeIdMr, Integer ddpInstanceId, String assigneeIdTissue,
                       String realm, String created, String reviewed, String crSent, String crReceived, String notes, Boolean minimalMr,
                       Boolean abstractionReady, String additionalValuesJson, Long exitDate) {
        this.participantId = participantId;
        this.ddpParticipantId = ddpParticipantId;
        this.assigneeIdMr = assigneeIdMr;
        this.ddpInstanceId = ddpInstanceId;
        this.assigneeIdTissue = assigneeIdTissue;
        this.realm = realm;
        this.created = created;
        this.reviewed = reviewed;
        this.crSent = crSent;
        this.crReceived = crReceived;
        this.notes = notes;
        this.minimalMr = minimalMr;
        this.abstractionReady = abstractionReady;
        this.additionalValuesJson = additionalValuesJson;
        this.exitDate = exitDate;
    }

    //For TissueList
    public Participant(String participantId, String ddpParticipantId, String assigneeIdTissue) {
        this(Long.parseLong(participantId), ddpParticipantId, null, assigneeIdTissue, null, null, null, null, null, null, false, false,
                null, null);
    }

    public Participant(long participantId, String ddpParticipantId, int ddpInstanceId) {
        this.participantId    = participantId;
        this.ddpParticipantId = ddpParticipantId;
        this.ddpInstanceId    = ddpInstanceId;
    }

    public static Participant getParticipant(@NonNull Map<String, Assignee> assignees, @NonNull String realm, @NonNull ResultSet rs)
            throws SQLException {
        String assigneeMR = null;
        String assigneeTissue = null;
        if (assignees != null && !assignees.isEmpty()) {
            String assigneeIdMR = rs.getString(DBConstants.ASSIGNEE_ID_MR);
            if (StringUtils.isNotBlank(assigneeIdMR) && assignees.get(assigneeIdMR) != null) {
                assigneeMR = assignees.get(assigneeIdMR).getName();
            }
            String assigneeIdTissue = rs.getString(DBConstants.ASSIGNEE_ID_TISSUE);
            if (StringUtils.isNotBlank(assigneeIdTissue) && assignees.get(assigneeIdTissue) != null) {
                assigneeTissue = assignees.get(assigneeIdTissue).getName();
            }
        }
        Participant participant =
                new Participant(rs.getLong(DBConstants.PARTICIPANT_ID), rs.getString(DBConstants.DDP_PARTICIPANT_ID), assigneeMR,
                        assigneeTissue, realm, rs.getString(DBConstants.ONC_HISTORY_CREATED),
                        rs.getString(DBConstants.ONC_HISTORY_REVIEWED), rs.getString(DBConstants.CR_SENT),
                        rs.getString(DBConstants.CR_RECEIVED),
                        rs.getString(DBConstants.DDP_PARTICIPANT_RECORD_ALIAS + DBConstants.ALIAS_DELIMITER + DBConstants.NOTES),
                        rs.getBoolean(DBConstants.DDP_PARTICIPANT_RECORD_ALIAS + DBConstants.ALIAS_DELIMITER + DBConstants.MINIMAL_MR),
                        rs.getBoolean(
                                DBConstants.DDP_PARTICIPANT_RECORD_ALIAS + DBConstants.ALIAS_DELIMITER + DBConstants.ABSTRACTION_READY),
                        rs.getString(DBConstants.DDP_PARTICIPANT_RECORD_ALIAS + DBConstants.ALIAS_DELIMITER
                                + DBConstants.ADDITIONAL_VALUES_JSON), (Long) rs.getObject(DBConstants.EXIT_DATE));
        return participant;
    }

    public static Map<String, Participant> getParticipants(@NonNull String realm) {
        return getParticipants(realm, null);
    }

    public static Map<String, Participant> getParticipants(@NonNull String realm, String queryAddition) {
        logger.info("Collection participant information");
        Map<String, Participant> participants = new HashMap<>();
        HashMap<String, Assignee> assignees = Assignee.getAssigneeMap(realm);
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(DBUtil.getFinalQuery(SQL_SELECT_PARTICIPANT, queryAddition))) {
                stmt.setString(1, realm);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        participants.put(rs.getString(DBConstants.DDP_PARTICIPANT_ID), getParticipant(assignees, realm, rs));
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Couldn't get list of participants ", results.resultException);
        }
        logger.info("Got " + participants.size() + " participants in DSM DB for " + realm);
        return participants;
    }

    public Boolean isMinimalMr() {
        return minimalMr;
    }

    @Override
    public Participant clone() {
        try {
            return (Participant) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}
