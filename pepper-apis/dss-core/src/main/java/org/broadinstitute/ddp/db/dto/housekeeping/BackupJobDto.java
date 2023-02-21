package org.broadinstitute.ddp.db.dto.housekeeping;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Data
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class BackupJobDto {
    @ColumnName("run_name")
    private String runName;

    @ColumnName("start_time")
    private String startTime;

    @ColumnName("end_time")
    private String endTime;

    @ColumnName("database_name")
    private String databaseName;

    @ColumnName("status")
    private String status;

    public static Instant parseDateString(String dateString) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss[.SSS]XXX").parse(dateString).toInstant();
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

}
