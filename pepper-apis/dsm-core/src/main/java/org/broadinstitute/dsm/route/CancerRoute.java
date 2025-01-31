package org.broadinstitute.dsm.route;

import java.util.ArrayList;
import java.util.List;

import org.broadinstitute.dsm.db.Cancer;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.lddp.handlers.util.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

public class CancerRoute implements Route {

    private static final Logger logger = LoggerFactory.getLogger(CancerRoute.class);

    @Override
    public Object handle(Request request, Response response) throws Exception {
        List<String> cancerList = new ArrayList<>();
        try {
            cancerList = Cancer.getCancers();
            return cancerList;
        } catch (Exception e) {
            logger.error("Cancer list attempt gave an error: ", e);
            return new Result(500, UserErrorMessages.CONTACT_DEVELOPER);
        }
    }
}
