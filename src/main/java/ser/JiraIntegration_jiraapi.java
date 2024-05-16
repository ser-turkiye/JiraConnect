package ser;

import de.ser.doxis4.agentserver.UnifiedAgent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;


public class JiraIntegration_jiraapi extends UnifiedAgent {
    Logger log = LogManager.getLogger();
    ProcessHelper processHelper;
    JSONObject config;
    Connection conn;
    List<String> types = new ArrayList<>();
    @Override
    protected Object execute() {
        if (getBpm() == null)
            return resultError("Null BPM object");

        Utils.session = getSes();
        Utils.bpm = getBpm();
        Utils.server = Utils.session.getDocumentServer();
        Utils.loadDirectory(Conf.Paths.MainPath);

        try {
            processHelper = new ProcessHelper(Utils.session);
            config = Utils.getSystemConfig(Conf.CfgNames.IntegrationName);

            log.info("Tested.");
        } catch (Exception e) {
            //throw new RuntimeException(e);
            log.error("Exception       : " + e.getMessage());
            log.error("    Class       : " + e.getClass());
            log.error("    Stack-Trace : " + e.getStackTrace() );
            return resultRestart("Exception : " + e.getMessage(),10);
        }

        log.info("Finished");
        return resultSuccess("Ended successfully");
    }
}