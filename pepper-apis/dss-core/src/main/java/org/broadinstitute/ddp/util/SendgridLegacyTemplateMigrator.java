package org.broadinstitute.ddp.util;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.broadinstitute.ddp.constants.NotificationTemplateVariables;

/*

run this query in the cloned database to pick up templates
for a particular study.  Then export them as json and wrap
up into the TemplatesToMigrate json format and use that
file to drive the script.

select distinct t.template_key,t.notification_template_id
from
event_action a,
event_action_type atype,
event_configuration c,
user_notification_event_action notification,
user_notification_template user_template,
notification_template t,
umbrella_study s
where
s.study_name = 'cmi-brain'
and
s.umbrella_study_id = c.umbrella_study_id
and
user_template.user_notification_event_action_id = notification.user_notification_event_action_id
and
user_template.notification_template_id = t.notification_template_id
and
notification.user_notification_event_action_id = a.event_action_id
and
c.event_action_id = a.event_action_id
and
atype.event_action_type_code = 'NOTIFICATION'
and
a.event_action_type_id = atype.event_action_type_id

 */
@Slf4j
public class SendgridLegacyTemplateMigrator {

    private Map<String, String> variablesToReplace = new HashMap<>();

    // todo arz update sendgrid api key for pepper-cmi/dev
    public static void main(String[] args) {


        var sourceSendgridApiKey = System.getenv("SOURCE_SENDGRID_API_KEY");
        var newSendgridApiKey = System.getenv("NEW_SENDGRID_API_KEY");

        String migrationFile = args[0];

        log.info("Using source API key " + sourceSendgridApiKey);
        var migrator = new SendgridLegacyTemplateMigrator();
        migrator.addVariableMapping(NotificationTemplateVariables.DDP_BASE_WEB_URL, NotificationTemplateVariables.BASE_WEB_URL);
        migrator.addVariableMapping(NotificationTemplateVariables.DDP_ACTIVITY_INSTANCE_GUID,
                NotificationTemplateVariables.ACTIVITY_INSTANCE_GUID);
        migrator.addVariableMapping(NotificationTemplateVariables.DDP_PARTICIPANT_FIRST_NAME,
                NotificationTemplateVariables.PARTICIPANT_FIRST_NAME);
        migrator.addVariableMapping(NotificationTemplateVariables.DDP_PROXY_FIRST_NAME,
                NotificationTemplateVariables.PROXY_FIRST_NAME);
        migrator.addVariableMapping(NotificationTemplateVariables.DDP_PROXY_LAST_NAME,
                NotificationTemplateVariables.PROXY_LAST_NAME);
        migrator.addVariableMapping(NotificationTemplateVariables.DDP_LINK, NotificationTemplateVariables.LINK);
        migrator.addVariableMapping(NotificationTemplateVariables.DDP_PARTICIPANT_LAST_NAME,
                NotificationTemplateVariables.DDP_PARTICIPANT_LAST_NAME);
        migrator.addVariableMapping(NotificationTemplateVariables.DDP_INVITATION_ID,
                NotificationTemplateVariables.INVITATION_ID);
        migrator.addVariableMapping(NotificationTemplateVariables.DDP_PARTICIPANT_EXIT_NOTES,
                NotificationTemplateVariables.PARTICIPANT_EXIT_NOTES);
        migrator.addVariableMapping(NotificationTemplateVariables.DDP_PARTICIPANT_FROM_EMAIL,
                NotificationTemplateVariables.PARTICIPANT_FROM_EMAIL);
        migrator.addVariableMapping(NotificationTemplateVariables.DDP_PARTICIPANT_GUID,
                NotificationTemplateVariables.PARTICIPANT_GUID);
        migrator.addVariableMapping(NotificationTemplateVariables.DDP_PARTICIPANT_HRUID,
                NotificationTemplateVariables.DDP_PARTICIPANT_HRUID);
        migrator.addVariableMapping(NotificationTemplateVariables.DDP_PROXY_GUID,
                NotificationTemplateVariables.PROXY_GUID);
        migrator.addVariableMapping(NotificationTemplateVariables.DDP_SALUTATION,
                NotificationTemplateVariables.SALUTATION);
        migrator.addVariableMapping(NotificationTemplateVariables.DDP_STUDY_GUID,
                NotificationTemplateVariables.STUDY_GUID);

        List<String> sqlUpdates = new ArrayList<>();
        TemplatesToMigrate templates = migrator.readTemplatesToMigrate(migrationFile);

        for (TemplateToMigrate template : templates.getTemplates()) {
            sqlUpdates.add(migrator.migrateTemplate(sourceSendgridApiKey, newSendgridApiKey, template));
        }

        for (String sqlUpdate : sqlUpdates) {
            System.out.println(sqlUpdate);
        }
    }

    private void addVariableMapping(String oldVariable, String newVariable) {
        this.variablesToReplace.put(oldVariable, newVariable);
    }

    private String migrateTemplate(String sourceApiKey, String newApiKey, TemplateToMigrate templateToMigrate) {
        String sqlUpdate = null;
        log.info("Migrating " + templateToMigrate.getSourceTemplateId());
        try {
            SendGrid sourceSendGrid = new SendGrid(sourceApiKey);
            SendGrid newSendGrid = new SendGrid(newApiKey);
            Request request = new Request();
            request.setMethod(Method.GET);
            request.setEndpoint("/templates/" + templateToMigrate.getSourceTemplateId());
            Response response = sourceSendGrid.api(request);

            SendgridTemplate template = new Gson().fromJson(response.getBody(), SendgridTemplate.class);

            for (TemplateVersion templateVersion : template.getVersions()) {
                if (templateVersion.isActive()) {
                    log.info("Active source template id/version is " + templateVersion.getTemplateId() + "/" + templateVersion.getId());

                    String newContent = replaceVariables(templateVersion.getHtmlContent(), variablesToReplace);

                    // create the new dynamic template in the new sendgrid account
                    Request newRequest = new Request();
                    newRequest.setMethod(Method.POST);
                    newRequest.setEndpoint("/templates");
                    DynamicSendGridTemplate newTemplate = new DynamicSendGridTemplate(template.getName());
                    newRequest.setBody(new Gson().toJson(newTemplate));
                    Response newResponse = newSendGrid.api(newRequest);

                    // now create a new version for the newly created template
                    newTemplate = new Gson().fromJson(newResponse.getBody(), DynamicSendGridTemplate.class);
                    newRequest.setEndpoint("/templates/" + newTemplate.getId() + "/versions");
                    newRequest.setBody(new Gson().toJson(newTemplate));

                    String newVersionName = "Migrated at " + new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").format(new Date());
                    TemplateVersion newVersion = new TemplateVersion(newVersionName, newTemplate.getId(), newContent,
                            templateVersion.getSubject(), true);
                    // todo arz check if active
                    newRequest.setBody(new Gson().toJson(newVersion));
                    // name has to be not blank in the version?
                    newResponse = newSendGrid.api(newRequest);

                    TemplateVersion newTemplateVersion = new Gson().fromJson(newResponse.getBody(), TemplateVersion.class);

                    log.info("Source template " + template.getId() + " has been migrated to new template " + newTemplate.getId());
                    sqlUpdate = templateToMigrate.generateUpdateSql(newTemplate.getId());
                } else {
                    log.info("Skipping inactive version of " + template.getId());
                }
            }
        } catch (Exception e) {
            log.error("Could not get templates", e);
        }
        return sqlUpdate;
    }

    private String replaceVariables(String templateContent, Map<String, String> variableMap) {

        for (Map.Entry<String, String> vars : variableMap.entrySet()) {
            String replacement = "{{ " + vars.getValue() + " }}";
            templateContent = templateContent.replace(vars.getKey(), replacement);
        }
        return templateContent;
    }

    private TemplatesToMigrate readTemplatesToMigrate(String file) {
        String templateUpdateJson = null;
        try {
            templateUpdateJson = FileUtils.readFileToString(new File(file));
        } catch (IOException e) {
            throw new RuntimeException("Could not read template update file " + file, e);
        }
        TemplatesToMigrate templates = new Gson().fromJson(templateUpdateJson, TemplatesToMigrate.class);
        return templates;
    }


    private static class DynamicSendGridTemplate {

        @SerializedName("name")
        private String name;

        @SerializedName("generation")
        private String generation = "dynamic";

        @SerializedName("id")
        private String id;

        public DynamicSendGridTemplate(String name) {
            this.name = name;
        }

        public String getId() {
            return id;
        }
    }

    private static class SendgridTemplate {

        @SerializedName("versions")
        private TemplateVersion[] versions;

        @SerializedName("id")
        private String id;

        @SerializedName("name")
        private String name;

        public TemplateVersion[] getVersions() {
            return versions;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }


        public String getActiveVersionId() {
            String activeVersionId = null;
            for (TemplateVersion version : versions) {
                if (version.isActive()) {
                    activeVersionId = version.getId();
                    break;
                }
            }
            return activeVersionId;
        }
    }

    private static class TemplateVersion {

        @SerializedName("id")
        private String id;

        @SerializedName("template_id")
        private String templateId;

        @SerializedName("active")
        private Integer active;

        @SerializedName("html_content")
        private String htmlContent;

        @SerializedName("subject")
        private String subject;

        @SerializedName("name")
        private String name;

        public TemplateVersion(String name, String templateId, String htmlContent, String subject, boolean isActive) {
            this.name = name;
            this.templateId = templateId;
            this.htmlContent = htmlContent;
            this.subject = subject;
            if (isActive) {
                this.active = 1;
            } else {
                this.active = 0;
            }
        }

        public boolean isActive() {
            log.info("active=" + active);
            return Integer.valueOf(1).equals(active);
        }

        public String getHtmlContent() {
            return htmlContent;
        }

        public String getId() {
            return id;
        }

        public String getTemplateId() {
            return templateId;
        }

        public String getSubject() {
            return subject;
        }
    }

    private static class TemplatesToMigrate {

        @SerializedName("templates")
        private List<TemplateToMigrate> templates;

        public List<TemplateToMigrate> getTemplates() {
            return templates;
        }
    }

    private static class TemplateToMigrate {

        @SerializedName("notification_template_id")
        private long templatePrimaryKey;

        @SerializedName("template_key")
        private String sourceTemplateId;

        public String generateUpdateSql(String newTemplateId) {
            return "update notification_template t set template_key = '" + newTemplateId + "' where t.notification_template_id = "
                    + templatePrimaryKey + ";";
        }

        public String getSourceTemplateId() {
            return sourceTemplateId;
        }
    }
}
