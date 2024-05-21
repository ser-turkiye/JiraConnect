package ser;

import com.ser.blueline.*;
import com.ser.blueline.bpm.IWorkbasket;
import com.ser.blueline.metaDataComponents.IArchiveClass;
import com.sun.xml.bind.v2.schemagen.Util;
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
            helper = new ProcessHelper(Utils.session);
            XTRObjects.setSession(Utils.session);
            XTRObjects.setBpm(Utils.bpm);

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
            if(mcfg == null){throw new Exception("Wildcard config not found.");}

            String mfld = (mcfg.has("mainFolder") && mcfg.getString("mainFolder") != null ?
                    mcfg.getString("mainFolder") : "");
            if(mfld.isEmpty()){throw new Exception("Wildcard.mainFolder is empty.");}

            String dtyp = (mcfg.has("docType") && mcfg.getString("docType") != null ?
                    mcfg.getString("docType") : "");

            document.setDescriptorValue(Conf.Descriptors.GIB_MainFolder, mfld);
            document.setDescriptorValue(Conf.Descriptors.GIB_DocumentType, dtyp);
            document.commit();

            String gjra = "__JiraResponsibles";
            IGroup egrp = XTRObjects.findGroup(gjra);
            if(egrp == null){
                egrp = XTRObjects.createGroup(gjra);
                egrp.commit();
            }
            if(egrp == null){throw new Exception("Not found/create group '" + gjra + "'");}

            IWorkbasket gwbk = XTRObjects.getFirstWorkbasket(egrp);
            if(gwbk == null){
                gwbk = XTRObjects.createWorkbasket(egrp);
                gwbk.commit();
            }
            if(gwbk == null){throw new Exception("Not found/create workbasket '" + gjra + "'");}

            IDocument docGIB = copyDocument(document, mfld);
            docGIB.setDescriptorValue("Sender", gwbk.getID());
            docGIB.commit();

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
    public static IDocument copyDocument(IInformationObject docu, String dtyp) throws Exception {
        IArchiveClass ac = Utils.server.getArchiveClassByName(Utils.session, dtyp);
        if(ac == null){throw new Exception("ArchiveClass not found (Name : " + dtyp + ")");}
        IDatabase db = Utils.session.getDatabase(ac.getDefaultDatabaseID());
        IDocument rtrn = Utils.server.getClassFactory().getDocumentInstance(db.getDatabaseName(), ac.getID(), "0000" , Utils.session);
        rtrn = Utils.server.copyDocument2(Utils.session, (IDocument) docu, rtrn, CopyScope.COPY_DESCRIPTORS, CopyScope.COPY_PART_DOCUMENTS);
        return rtrn;
    }
}