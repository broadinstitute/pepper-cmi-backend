package org.broadinstitute.ddp.housekeeping.schedule;

import java.time.Instant;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Bucket;
import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.client.GoogleBucketClient;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.elastic.ElasticSearchIndexType;
import org.broadinstitute.ddp.export.DataExportCoordinator;
import org.broadinstitute.ddp.export.DataExporter;
import org.broadinstitute.ddp.util.ConfigManager;
import org.broadinstitute.ddp.util.GoogleCredentialUtil;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;

@Slf4j
public class OnDemandExportJob implements Job {

    public static final String DATA_STUDY = "study";
    public static final String DATA_INDEX = "index";
    public static final String DATA_CSV = "csv";
    public static final String ALL_INDICES = "ALL_INDICES";

    private static DataExporter exporter;

    public static JobKey getKey() {
        return Keys.Export.OnDemandJob;
    }

    public static void register(Scheduler scheduler, Config cfg) throws SchedulerException {
        exporter = new DataExporter(cfg);
        JobDetail job = JobBuilder.newJob(OnDemandExportJob.class)
                .withIdentity(getKey())
                .requestRecovery(false)
                .storeDurably(true)
                .build();

        // Add job without a trigger schedule since it's by on-demand.
        scheduler.addJob(job, true);
        log.info("Added job {} to scheduler", getKey());
    }

    @Override
    public void execute(JobExecutionContext ctx) throws JobExecutionException {
        try {
            JobDataMap data = ctx.getMergedJobDataMap();
            String study = data.getString(DATA_STUDY);
            String index = data.getOrDefault(DATA_INDEX, null) != null
                    ? data.getString(DATA_INDEX) : null;
            boolean doCsv = data.getOrDefault(DATA_CSV, null) != null && data.getBoolean(DATA_CSV);
            log.info("Triggered on-demand export job for study={}, index={}, csv={}",
                    study, index == null ? "<null>" : index, doCsv);

            boolean exportCurrentlyRunning = ctx.getScheduler()
                    .getCurrentlyExecutingJobs().stream()
                    .anyMatch(jctx -> {
                        JobKey key = jctx.getJobDetail().getKey();
                        return key.equals(StudyDataExportJob.getKey());
                    });
            if (exportCurrentlyRunning) {
                log.warn("Regular data export job currently running, skipping job {}", getKey());
                return;
            }

            log.info("Running job {}", getKey());
            long start = Instant.now().toEpochMilli();
            run(study, index, doCsv);
            long elapsed = Instant.now().toEpochMilli() - start;
            log.info("Finished job {}. Took {}s", getKey(), elapsed / 1000);
        } catch (Exception e) {
            log.error("Error while executing job {}", getKey(), e);
            throw new JobExecutionException(e, false);
        }
    }

    private void run(String studyGuid, String index, boolean doCsv) {
        Config cfg = ConfigManager.getInstance().getConfig();

        StudyDto studyDto = TransactionWrapper.withTxn(TransactionWrapper.DB.APIS, handle ->
                handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid));
        if (studyDto == null) {
            log.warn("Unknown study '{}', skipping job {}", studyGuid, getKey());
            return;
        } else if (!studyDto.isDataExportEnabled()) {
            log.warn("Study {} does not have data export enabled, skipping job {}", studyGuid, getKey());
            return;
        }

        if (StringUtils.isBlank(studyDto.getExportBucket())) {
            log.error("Study {} does not have an export bucket configured but is marked for export.", studyDto.getGuid());
            return;
        }

        var coordinator = new DataExportCoordinator(exporter)
                .withBatchSize(cfg.getInt(ConfigFile.ELASTICSEARCH_EXPORT_BATCH_SIZE));

        if (index != null) {
            if (ALL_INDICES.equals(index)) {
                coordinator.includeIndex(ElasticSearchIndexType.ACTIVITY_DEFINITION);
                coordinator.includeIndex(ElasticSearchIndexType.PARTICIPANTS_STRUCTURED);
                coordinator.includeIndex(ElasticSearchIndexType.PARTICIPANTS);
                coordinator.includeIndex(ElasticSearchIndexType.USERS);
            } else {
                try {
                    var indexType = ElasticSearchIndexType.valueOf(index.toUpperCase());
                    coordinator.includeIndex(indexType);
                } catch (Exception e) {
                    log.error("Unknown index type: {}", index);
                    return;
                }
            }
        }

        if (doCsv) {
            String gcpProjectId = cfg.getString(ConfigFile.GOOGLE_PROJECT_ID);
            GoogleCredentials credentials = GoogleCredentialUtil
                    .initCredentials(cfg.getBoolean(ConfigFile.REQUIRE_DEFAULT_GCP_CREDENTIALS));
            if (credentials == null) {
                log.error("No Google credentials are provided, skipping job {}", getKey());
                return;
            }

            var bucketClient = new GoogleBucketClient(gcpProjectId, credentials);
            Bucket bucket = bucketClient.getBucket(studyDto.getExportBucket());
            if (bucket == null) {
                log.error("Could not find google bucket {}, skipping job {}", studyDto.getExportBucket(), getKey());
                return;
            }

            coordinator.includeCsv(bucket);
        }

        coordinator.export(studyDto);
    }
}
