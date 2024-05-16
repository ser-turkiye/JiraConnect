package ser;

import com.ser.blueline.IDocument;
import com.ser.blueline.IInformationObject;
import de.ser.doxis4.agentserver.UnifiedAgent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;


public class GIBJiraTicketClassification extends UnifiedAgent {
    Logger log = LogManager.getLogger();
    IInformationObject qaInfObj;
    ProcessHelper helper;
    IDocument document;
    String compCode;
    String docId;
    @Override
    protected Object execute() {
        if (getEventDocument() == null)
            return resultError("Null Document object");

        Utils.session = getSes();
        Utils.bpm = getBpm();
        Utils.server = Utils.session.getDocumentServer();
        Utils.loadDirectory(Conf.Paths.MainPath);

        document = getEventDocument();

        try {
            JSONObject wcfs = Utils.loadGIBJiraTicketClassifications();
            String fnam = document.getDescriptorValue(Conf.Descriptors.Name, String.class);
            JSONObject mcfg = null;
            for(String wkey : wcfs.keySet()){
                JSONObject wcfg = wcfs.getJSONObject(wkey);
                if(!wcfg.has("priority")){continue;}
                if(wcfg.getInt("priority") <= 0){continue;}

                String wcrd = wkey.toUpperCase();
                wcrd = wcrd.replaceAll("\\.", "");
                wcrd = wcrd.replaceAll("\\*", ".*");
                if(!fnam.toUpperCase().matches(wcrd)){continue;}
                if(mcfg != null && mcfg.getInt("priority") <= wcfg.getInt("priority")){continue;}
                mcfg = wcfg;
            }
            if(mcfg != null){
                document.setDescriptorValue(Conf.Descriptors.GIB_MainFolder,
                    mcfg.has("mainFolder") ? mcfg.getString("mainFolder") : "");
                document.setDescriptorValue(Conf.Descriptors.GIB_DocumentType,
                    mcfg.has("docType") ? mcfg.getString("docType") : "");
                document.commit();
            }

        } catch (Exception e) {
            //throw new RuntimeException(e);
            log.error("Exception       : " + e.getMessage());
            log.error("    Class       : " + e.getClass());
            log.error("    Stack-Trace : " + e.getStackTrace() );
            return resultError("Exception : " + e.getMessage());
        }

        log.info("Finished");
        return resultSuccess("Ended successfully");
    }
}