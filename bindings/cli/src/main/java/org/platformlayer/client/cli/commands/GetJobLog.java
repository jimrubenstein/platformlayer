package org.platformlayer.client.cli.commands;

import java.io.PrintWriter;

import org.apache.log4j.Priority;
import org.kohsuke.args4j.Argument;
import org.platformlayer.PlatformLayerClientException;
import org.platformlayer.PlatformLayerClient;
import com.fathomdb.cli.autocomplete.AutoCompletor;
import com.fathomdb.cli.autocomplete.SimpleAutoCompleter;
import com.fathomdb.cli.commands.Ansi;
import org.platformlayer.client.cli.autocomplete.AutoCompleteJobId;
import org.platformlayer.jobs.model.JobLog;
import org.platformlayer.jobs.model.JobLogLine;

public class GetJobLog extends PlatformLayerCommandRunnerBase {
    @Argument
    String jobId;

    public GetJobLog() {
        super("get", "log");
    }

    @Override
    public Object runCommand() throws PlatformLayerClientException {
        PlatformLayerClient client = getPlatformLayerClient();

        JobLog jobLog = client.getJobLog(jobId);

        return jobLog;
    }

    @Override
    public AutoCompletor getAutoCompleter() {
        return new SimpleAutoCompleter(new AutoCompleteJobId());
    }

    @Override
    public void formatRaw(Object o, PrintWriter writer) {
        Ansi ansi = new Ansi(writer);

        JobLog jobLog = (JobLog) o;
        for (JobLogLine line : jobLog) {
            if (line.level >= Priority.ERROR_INT) {
                ansi.setColorRed();
            } else if (line.level >= Priority.WARN_INT) {
                ansi.setColorYellow();
            } else if (line.level >= Priority.INFO_INT) {
                ansi.setColorGreen();
            } else {
                ansi.setColorBlue();
            }

            writer.println(line.message);
            if (line.exception != null) {
                for (String exceptionLine : line.exception.info) {
                    writer.println(exceptionLine);
                }
            }
        }

        ansi.reset();
    }

}
