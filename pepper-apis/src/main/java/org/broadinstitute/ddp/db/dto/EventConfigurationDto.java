package org.broadinstitute.ddp.db.dto;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.event.NotificationServiceType;
import org.broadinstitute.ddp.model.event.NotificationType;
import org.broadinstitute.ddp.model.event.PdfAttachment;

/**
 * A DTO representing the event configuration, left joined with possible trigger and action configuration info
 */
public class EventConfigurationDto {
    private long eventConfigurationId;
    private EventTriggerType eventTriggerType;
    private EventActionType eventActionType;
    private Integer postDelaySeconds;
    private boolean dispatchToHousekeeping;
    private String preconditionExpression;
    private String cancelExpression;
    private Integer maxOccurrencesPerUser;
    private String gcpTopic; // FIXME should not be a base field of EventAction

    /**
     * Triggers
     **/
    /* ACTIVITY_STATUS */
    private InstanceStatusType instanceStatusType;
    private Long activityStatusTriggerStudyActivityId;

    /* WORKFLOW_STATE */
    private Long workflowStateId;
    private Boolean triggerAutomatically;

    /* JOIN_MAILING_LIST */
    // No sub-table

    /* DSM_NOTIFICATION */
    private String dsmNotificationEventType; // FIXME Turn into real enum

    /* MEDICAL_UPDATE */
    // No sub-table

    /* USER_NOT_IN_STUDY */
    // No sub-table

    /* USER_REGISTERED */
    // No sub-table

    /* EXIT_REQUEST */
    // No sub-table

    /**
     * Actions
     **/
    /* ANNOUNCEMENT */
    private Long announcementMsgTemplateId;
    private Boolean isAnnouncementPermanent;
    private Boolean createAnnouncementForProxies;

    /* NOTIFICATION */
    private NotificationType notificationType;
    private NotificationServiceType notificationServiceType;
    private Long notificationTemplateId;
    private Long linkedActivityId;
    private List<PdfAttachment> notificationPdfAttachments = new ArrayList<>();

    /* PDF_GENERATION */
    private Long pdfGenerationDocumentConfigurationId;


    /* ACTIVITY_INSTANCE_CREATION */
    private Long activityInstanceCreationStudyActivityId;
    private Long activityInstanceCreationCopyConfigurationId;

    /* USER_ENROLLED */
    // No sub-table

    /* COPY_ANSWER */
    private Long copyActionCopyConfigurationId;

    /* CREATE_INVITATION */
    private Long contactEmailQuestionStableCodeId;
    private String contactEmailQuestionStableId;
    private Boolean markExistingInvitationsAsVoided;

    /* MARK_ACTIVITIES_READ_ONLY, HIDE_ACTIVITIES */
    private Set<Long> targetActivityIds = new HashSet<>();

    /* REVOKE_PROXIES */
    // No sub-table

    public EventConfigurationDto(long eventConfigurationId,
                                 EventTriggerType eventTriggerType,
                                 EventActionType eventActionType,
                                 int postDelaySeconds,
                                 boolean dispatchToHousekeeping,
                                 String preconditionExpression,
                                 String cancelExpression,
                                 Integer maxOccurrencesPerUser,
                                 String gcpTopic,
                                 InstanceStatusType instanceStatusType,
                                 Long activityStatusTriggerStudyActivityId,
                                 Long workflowStateId,
                                 Boolean triggerAutomatically,
                                 String dsmNotificationEventType,
                                 Long announcementMsgTemplateId,
                                 Boolean announcementIsPermanent,
                                 Boolean announcementCreateForProxies,
                                 NotificationType notificationType,
                                 NotificationServiceType notificationServiceType,
                                 Long notificationTemplateId,
                                 Long linkedActivityId,
                                 Long pdfGenerationDocumentConfigurationId,
                                 Long activityInstanceCreationStudyActivityId,
                                 Long activityInstanceCreationCopyConfigurationId,
                                 Long copyActionCopyConfigurationId,
                                 Long contactEmailQuestionStableCodeId,
                                 String contactEmailQuestionStableId,
                                 Boolean markExistingInvitationsAsVoided) {
        this.eventConfigurationId = eventConfigurationId;
        this.eventTriggerType = eventTriggerType;
        this.eventActionType = eventActionType;
        this.postDelaySeconds = postDelaySeconds;
        this.dispatchToHousekeeping = dispatchToHousekeeping;
        this.preconditionExpression = preconditionExpression;
        this.cancelExpression = cancelExpression;
        this.maxOccurrencesPerUser = maxOccurrencesPerUser;
        this.gcpTopic = gcpTopic;
        this.instanceStatusType = instanceStatusType;
        this.activityStatusTriggerStudyActivityId = activityStatusTriggerStudyActivityId;
        this.workflowStateId = workflowStateId;
        this.triggerAutomatically = triggerAutomatically;
        this.dsmNotificationEventType = dsmNotificationEventType;
        this.announcementMsgTemplateId = announcementMsgTemplateId;
        this.isAnnouncementPermanent = announcementIsPermanent;
        this.createAnnouncementForProxies = announcementCreateForProxies;
        this.notificationType = notificationType;
        this.notificationServiceType = notificationServiceType;
        this.notificationTemplateId = notificationTemplateId;
        this.linkedActivityId = linkedActivityId;
        this.pdfGenerationDocumentConfigurationId = pdfGenerationDocumentConfigurationId;
        this.activityInstanceCreationStudyActivityId = activityInstanceCreationStudyActivityId;
        this.activityInstanceCreationCopyConfigurationId = activityInstanceCreationCopyConfigurationId;
        this.copyActionCopyConfigurationId = copyActionCopyConfigurationId;
        this.contactEmailQuestionStableCodeId = contactEmailQuestionStableCodeId;
        this.contactEmailQuestionStableId = contactEmailQuestionStableId;
        this.markExistingInvitationsAsVoided = markExistingInvitationsAsVoided;
    }

    public long getEventConfigurationId() {
        return eventConfigurationId;
    }

    public EventTriggerType getEventTriggerType() {
        return eventTriggerType;
    }

    public EventActionType getEventActionType() {
        return eventActionType;
    }

    public int getPostDelaySeconds() {
        return postDelaySeconds;
    }

    public boolean dispatchToHousekeeping() {
        return dispatchToHousekeeping;
    }

    public String getPreconditionExpression() {
        return preconditionExpression;
    }

    public String getCancelExpression() {
        return cancelExpression;
    }

    public Integer getMaxOccurrencesPerUser() {
        return maxOccurrencesPerUser;
    }

    public String getGcpTopic() {
        return gcpTopic;
    }

    public InstanceStatusType getInstanceStatusType() {
        return instanceStatusType;
    }

    public Long getActivityStatusTriggerStudyActivityId() {
        return activityStatusTriggerStudyActivityId;
    }

    public Long getWorkflowStateId() {
        return workflowStateId;
    }

    public Boolean getTriggerAutomatically() {
        return triggerAutomatically;
    }

    public String getDsmNotificationEventType() {
        return dsmNotificationEventType;
    }

    public Long getAnnouncementMsgTemplateId() {
        return announcementMsgTemplateId;
    }

    public Boolean isAnnouncementPermanent() {
        return isAnnouncementPermanent;
    }

    public Boolean shouldCreateAnnouncementForProxies() {
        return createAnnouncementForProxies;
    }

    public NotificationType getNotificationType() {
        return notificationType;
    }

    public NotificationServiceType getNotificationServiceType() {
        return notificationServiceType;
    }

    public Long getNotificationTemplateId() {
        return notificationTemplateId;
    }

    public Long getLinkedActivityId() {
        return linkedActivityId;
    }

    public Long getActivityInstanceCreationStudyActivityId() {
        return activityInstanceCreationStudyActivityId;
    }

    public Long getActivityInstanceCreationCopyConfigurationId() {
        return activityInstanceCreationCopyConfigurationId;
    }

    public Long getCopyActionCopyConfigurationId() {
        return copyActionCopyConfigurationId;
    }

    public Long getPdfGenerationDocumentConfigurationId() {
        return pdfGenerationDocumentConfigurationId;
    }

    public void addNotificationPdfAttachment(Long pdfDocumentConfigurationId, Boolean generateIfMissing) {
        notificationPdfAttachments.add(new PdfAttachment(pdfDocumentConfigurationId, generateIfMissing));
    }

    public List<PdfAttachment> getNotificationPdfAttachments() {
        return notificationPdfAttachments;
    }

    public Long getContactEmailQuestionStableCodeId() {
        return contactEmailQuestionStableCodeId;
    }

    public String getContactEmailQuestionStableId() {
        return contactEmailQuestionStableId;
    }

    public Boolean shouldMarkExistingInvitationsAsVoided() {
        return markExistingInvitationsAsVoided;
    }

    public void addTargetActivityId(long activityId) {
        targetActivityIds.add(activityId);
    }

    public Set<Long> getTargetActivityIds() {
        return targetActivityIds;
    }
}
