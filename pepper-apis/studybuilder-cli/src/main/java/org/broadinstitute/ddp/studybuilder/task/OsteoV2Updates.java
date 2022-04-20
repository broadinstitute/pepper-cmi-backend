package org.broadinstitute.ddp.studybuilder.task;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.studybuilder.task.osteo.OsteoAboutChildV2;
import org.broadinstitute.ddp.studybuilder.task.osteo.OsteoAboutYouV2;
import org.broadinstitute.ddp.studybuilder.task.osteo.OsteoConsentAddendumV2;
import org.broadinstitute.ddp.studybuilder.task.osteo.OsteoConsentVersion2;
import org.broadinstitute.ddp.studybuilder.task.osteo.OsteoDdp7601;
import org.broadinstitute.ddp.studybuilder.task.osteo.OsteoMRFv2;
import org.broadinstitute.ddp.studybuilder.task.osteo.OsteoNewActivities;
import org.broadinstitute.ddp.studybuilder.task.osteo.OsteoNewFamilyHistory;
import org.broadinstitute.ddp.studybuilder.task.osteo.OsteoPDFv2;
import org.broadinstitute.ddp.studybuilder.task.osteo.OsteoPrequalUpdate;
import org.jdbi.v3.core.Handle;

public class OsteoV2Updates implements CustomTask {

    List<CustomTask> tasks = new ArrayList<>();

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        tasks.add(new OsteoConsentVersion2());
        tasks.add(new OsteoDdp7601());
        tasks.add(new OsteoNewFamilyHistory());
        tasks.add(new OsteoAboutYouV2());
        tasks.add(new OsteoConsentAddendumV2());
        tasks.add(new OsteoPrequalUpdate());
        tasks.add(new OsteoNewActivities());
        tasks.add(new OsteoAboutChildV2());
        tasks.add(new OsteoMRFv2());
        tasks.add(new OsteoPDFv2());
        tasks.add(new OsteoFamilyHistoryReturnToDashboard());
        tasks.add(new OsteoSomaticConsentAddendum());
        tasks.add(new OsteoAdultConsentFixes());
        tasks.forEach(t -> t.init(cfgPath, studyCfg, varsCfg));
    }

    @Override
    public void run(Handle handle) {
        tasks.forEach(t -> t.run(handle));
    }
}
