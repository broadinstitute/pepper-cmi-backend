package org.broadinstitute.dsm.model.patch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.OncHistory;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.MedicalRecordUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//Patch class only for new added oncHistoryDetails otherwise ExistingRecordPatch is used
public class OncHistoryDetailPatch extends BasePatch {

    static final Logger logger = LoggerFactory.getLogger(OncHistoryDetailPatch.class);
    public static final String ONC_HISTORY_DETAIL_ID = "oncHistoryDetailId";

    static {
        NULL_KEY = new HashMap<>();
        NULL_KEY.put(NAME_VALUE, null);
    }

    private Number mrID;
    private String oncHistoryDetailId;

    {
        nameValues.add(new NameValue("request", OncHistoryDetail.STATUS_REVIEW));
        resultMap.put(NAME_VALUE, GSON.toJson(nameValues));
    }

    public OncHistoryDetailPatch(Patch patch) {
        super(patch);
    }

    @Override
    public Object doPatch() {
        return isNameValuePairs() ? patchNameValuePairs() : patchNameValuePair();
    }

    private void prepare() {
        if (StringUtils.isNotBlank(patch.getParentId())) {
            mrID = MedicalRecordUtil.isInstitutionTypeInDB(patch.getParentId());
        }
        if (mrID == null) {
            if (StringUtils.isNotBlank(patch.getDdpParticipantId())) {
                // mr of that type doesn't exist yet, so create an institution and mr
                MedicalRecordUtil.writeInstitutionIntoDb(patch.getDdpParticipantId(), MedicalRecordUtil.NOT_SPECIFIED, patch.getRealm());
                String participantId = MedicalRecordUtil.getParticipantIdByDdpParticipantId(patch.getDdpParticipantId(), patch.getRealm());
                if (StringUtils.isBlank(participantId)) {
                    throw new RuntimeException("Error adding new institution for oncHistory for pt w/ id " + patch.getParentId());
                }
                patch.setParentId(participantId);
                mrID = MedicalRecordUtil.isInstitutionTypeInDB(patch.getParentId());
            } else {
                throw new RuntimeException("Error adding new institution for oncHistory for pt w/ id " + patch.getParentId());
            }
        }
        if (mrID != null) {
            oncHistoryDetailId = OncHistoryDetail.createNewOncHistoryDetail(mrID.toString(), patch.getUser());
        }
        resultMap.put(ONC_HISTORY_DETAIL_ID, oncHistoryDetailId);
    }

    @Override
    protected Object patchNameValuePairs() {
        prepare();
        if (mrID == null) {
            logger.error("No medical record id for oncHistoryDetails ");
            return NULL_KEY;
        }
        processMultipleNameValues();
        return resultMap;
    }

    @Override
    protected Object patchNameValuePair() {
        prepare();
        Optional<Object> maybeSingleNameValue = processSingleNameValue();
        return maybeSingleNameValue.orElse(resultMap);
    }

    @Override
    Object handleSingleNameValue() {
        if (Patch.patch(oncHistoryDetailId, patch.getUser(), patch.getNameValue(), dbElement)) {
            nameValues.addAll(setWorkflowRelatedFields(patch));
            DDPInstance instance = DDPInstance.getDDPInstance(patch.getRealm());
            List<NameValue> nameValuesOd = new ArrayList<>();
            nameValuesOd.add(patch.getNameValue());
            nameValuesOd.add(new NameValue("oD.ddpInstanceId", instance.getDdpInstanceId()));
            exportToESWithId(oncHistoryDetailId, nameValuesOd);
            //set oncHistoryDetails created if it is a oncHistoryDetails value without a ID, otherwise created should already be set
            if (dbElement.getTableName().equals(DBConstants.DDP_ONC_HISTORY_DETAIL)) {
                NameValue oncHistoryCreated = OncHistory.setOncHistoryCreated(patch.getParentId(), patch.getUser());
                if (oncHistoryCreated.getValue() != null) {
                    exportToESWithId(patch.getParentId(), oncHistoryCreated);
                    nameValues.add(oncHistoryCreated);
                }
            }
        }
        resultMap.put(NAME_VALUE, GSON.toJson(nameValues));
        return resultMap;
    }

    @Override
    protected String getIdForES() {
        return oncHistoryDetailId;
    }

    @Override
    Optional<Object> processEachNameValue(NameValue nameValue) {
        Patch.patch(oncHistoryDetailId, patch.getUser(), nameValue, dbElement);
        return Optional.empty();
    }

}
