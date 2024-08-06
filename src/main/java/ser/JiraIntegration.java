package ser;

import com.ser.blueline.*;
import com.ser.blueline.metaDataComponents.IArchiveClass;
import com.ser.blueline.metaDataComponents.IArchiveFolderClass;
import com.ser.foldermanager.IFolder;
import com.ser.foldermanager.IFolderConnection;
import de.ser.doxis4.agentserver.UnifiedAgent;
import io.github.openunirest.http.HttpResponse;
import io.github.openunirest.http.Unirest;
import net.rcarz.jiraclient.Attachment;
import net.rcarz.jiraclient.BasicCredentials;
import net.rcarz.jiraclient.Issue;
import net.rcarz.jiraclient.JiraClient;
import org.apache.commons.io.FilenameUtils;
import org.json.JSONObject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.*;


public class JiraIntegration extends UnifiedAgent {
    String procId = UUID.randomUUID().toString();
    String path = "";
    Logger log = LogManager.getLogger();
    ProcessHelper processHelper;
    JSONObject config;
    @Override
    protected Object execute() {
        if (getBpm() == null)
            return resultError("Null BPM object");

        Utils.session = getSes();
        Utils.bpm = getBpm();
        Utils.server = Utils.session.getDocumentServer();
        String srvn = Utils.session.getSystem().getName().toUpperCase();

        path = Conf.Paths.MainPath + "/JiraConnect_JiraIntegration@" + procId;
        Utils.loadDirectory(path);

        try {
            processHelper = new ProcessHelper(Utils.session);
            config = Utils.getSystemConfig(srvn, Conf.CfgNames.IntegrationName);
            intgJiraConnections();

            log.info("Tested.");
        } catch (Exception e) {
            //throw new RuntimeException(e);
            log.error("Exception       : " + e.getMessage());
            log.error("    Class       : " + e.getClass());
            log.error("    Stack-Trace : " + e.getStackTrace().toString());
            return resultRestart("Exception : " + e.getMessage(),10);
        }

        log.info("Finished");
        return resultSuccess("Ended successfully [" + srvn + "]");
    }
    public void intgJiraConnections() throws Exception{
        String[] cons = getCfgString("Connections").split(";");
        for(String conm : cons){
            String addr = getCfgString(conm + ".address");
            if(addr.isEmpty()){continue;}

            String user = getCfgString(conm + ".username");
            if(user.isEmpty()){continue;}

            String pass = getCfgString(conm + ".password");
            if(pass.isEmpty()){continue;}
            JiraClient jira = new JiraClient(addr, new BasicCredentials(user, pass));

            intgJiraProjects(conm, jira);
        }
    }
    public void intgJiraProjects(String conm, JiraClient jira) throws Exception{
        String[] prjs = getCfgString(conm + ".projects").split(";");
        if(prjs.length == 0){return;}

        for(String prjt : prjs){
            intgJiraIssues(conm, prjt, jira);
        }
    }
    public boolean intgJiraIssue(String conm, String prjt, JiraClient jira, JSONObject schm, List<String> muss, String mpth, Issue line) throws Exception{

        String cprj = conm + "." + prjt;
        Issue issu = jira.getIssue(line.getKey());
        JSONObject jisu = getIssueStructure(conm, jira, prjt, schm, issu);

        if(!checkIntgMustFields(jisu, muss)){return false;}

        List<IInformationObject> dxas = new ArrayList<>();

        String jpth = "";
        if(jpth.isEmpty() && jisu.has("html")){jpth = jisu.getString("html");}
        if(jpth.isEmpty() && jisu.has("json")){jpth = jisu.getString("json");}
        if(jpth.isEmpty()){return false;}

        String diky = conm + "." + prjt + "@" + line.getKey();
        IInformationObject fisu = getEFileIssue(cprj, diky);
        if(fisu == null){
            fisu = createFolderIssue();
            fisu.setDescriptorValue(Conf.Descriptors.Project, cprj);
            fisu.setDescriptorValue(Conf.Descriptors.DocID, diky);
        }

        String jsts = fisu.getDescriptorValue(Conf.Descriptors.Status, String.class);
        jsts = (jsts == null ? "" : jsts);

        if(Arrays.asList("Imported", "Ready", "Done").contains(jsts)){return false;}

        String dsky = conm + "." + prjt + "/" + line.getKey() + "/Issue-Document";
        IDocument disu = (IDocument) getDocumentIssue(cprj, diky, "ISSUE", dsky);
        String isun = line.getKey() + "." + FilenameUtils.getExtension(jpth);
        if(disu == null){
            disu = createDocumentIssue();
            addRepresentation(disu, mpth, jpth, isun);
        }
        disu.setDescriptorValue(Conf.Descriptors.Project, cprj);
        disu.setDescriptorValue(Conf.Descriptors.ParentID, diky);
        disu.setDescriptorValue(Conf.Descriptors.DocID, dsky);
        disu.setDescriptorValue(Conf.Descriptors.Type, "ISSUE");
        disu.setDescriptorValue(Conf.Descriptors.Name, isun);

        if(jisu.has("attachments")){
            JSONObject atcs = jisu.getJSONObject("attachments");

            for(String akey : atcs.keySet()){
                JSONObject atch = (JSONObject) atcs.get(akey);
                if(!atch.has("id")){continue;}
                if(!atch.has("path")){continue;}
                if(!atch.has("name")){continue;}

                String daky = conm + "." + prjt + "/" + line.getKey() + "/" + atch.getString("id");

                IDocument datc = (IDocument) getDocumentIssue(cprj, diky, "ATTACHMENT", daky);
                if(datc == null){
                    datc = createDocumentIssue();
                    addRepresentation(datc, mpth, atch.getString("path"), atch.getString("name"));
                }

                datc.setDescriptorValue(Conf.Descriptors.Project, cprj);
                datc.setDescriptorValue(Conf.Descriptors.ParentID, diky);
                datc.setDescriptorValue(Conf.Descriptors.DocID, daky);
                datc.setDescriptorValue(Conf.Descriptors.Type, "ATTACHMENT");
                datc.setDescriptorValue(Conf.Descriptors.Name, atch.getString("name"));
                dxas.add(datc);
            }
        }

        fisu.commit();
        for(IInformationObject dxat : dxas){
            setIntgFields(dxat, jisu);
            dxat.commit();
            Utils.connectToFolder((IFolder) fisu, "Attachments", dxat);
        }

        setIntgFields(disu, jisu);
        disu.commit();
        Utils.connectToFolder((IFolder) fisu, "Issue", disu);

        setIntgFields(fisu, jisu);
        fisu.setDescriptorValue(Conf.Descriptors.Status, "Imported");
        fisu.commit();

        return true;
    }
    public void intgJiraIssues(String conm, String prjt, JiraClient jira) throws Exception{
        String cprj = conm + "." + prjt;
        String srch = getCfgString(cprj + ".issueSearch");
        if(srch.isEmpty()){return;}

        Issue.SearchResult data = jira.searchIssues(srch);
        List<String> muss = getIssueMustFields(conm, prjt);
        List<String> trns = getIssueRunTransitions(conm, prjt);
        JSONObject schm = getIssueFieldScheme(conm, prjt);

        String mpth = path + "/_temp";
        Utils.loadDirectory(mpth);

        for (Issue line : data.issues){
            if(!intgJiraIssue(conm, prjt, jira, schm,  muss, mpth, line)){continue;}

            for(String trnn : trns){
                if(trnn.isBlank()){continue;}
                line.transition().execute(trnn);
            }
        }
    }
    public JSONObject getIssueStructure(String conm, JiraClient jira, String prjt, JSONObject schm, Issue line) throws Exception{
        JSONObject flns = getIssueDefnFields(schm, line);
        String isid = UUID.randomUUID().toString();
        List<Attachment> atcs = line.getAttachments();
        String xpth = path + "/" + isid;
        Utils.loadDirectory(xpth);

        String ipth = xpth + "/" + isid + ".json";
        downloadIssue(conm, line, ipth);

        String wpth = xpth + "/" + isid + ".html";
        downloadIssueDoc(conm, line, wpth);

        JSONObject rtrn = new JSONObject();
        rtrn.put("key", line.getKey());
        rtrn.put("json", ipth);
        rtrn.put("html", wpth);

        JSONObject alns = new JSONObject();
        for(Attachment atch : atcs){
            String dpth = xpth + "/" + atch.getFileName();
            downloadAttachment(conm, atch, dpth);
            String dmd5 = fileMD5Checksum(dpth);
            JSONObject alin= new JSONObject();

            alin.put("id", atch.getId());
            alin.put("name", atch.getFileName());
            alin.put("path", dpth);
            alin.put("hash", dmd5);

            alns.put(atch.getFileName(), alin);
        }

        rtrn.put("fields", flns);
        rtrn.put("attachments", alns);
        return rtrn;
    }
    public boolean checkIntgMustFields(JSONObject jisu, List<String> muss) throws Exception{
        if(!jisu.has("fields")){return false;}
        JSONObject flds = jisu.getJSONObject("fields");
        for(String must : muss){
            if(!flds.has(must)){return false;}
            if(flds.getString(must) == null){return false;}
            if(flds.getString(must).isEmpty()){return false;}

        }
        return true;
    }
    public void setIntgFields(IInformationObject info, JSONObject jisu) throws Exception{
        if(!jisu.has("fields")){return;}

        JSONObject flds = jisu.getJSONObject("fields");
        for(String fkey : flds.keySet()){
            if(!Utils.hasDescriptor(info, fkey)){continue;}
            info.setDescriptorValue(fkey, flds.getString(fkey));
        }
    }
    private IInformationObject getEFileIssue(String cprj, String docId) {
        StringBuilder builder = new StringBuilder();
        builder.append("TYPE = '").append(Conf.ClassIDs.EFile).append("'")
                .append(" AND ")
                .append(Conf.DescriptorLiterals.Project).append(" = '").append(cprj).append("'")
                .append(" AND ")
                .append(Conf.DescriptorLiterals.DocID).append(" = '").append(docId).append("'");
        String whereClause = builder.toString();

        IInformationObject[] informationObjects = processHelper.createQuery(new String[]{Conf.Databases.Main}, whereClause, "", 1, false);
        if(informationObjects.length < 1) {return null;}
        return informationObjects[0];
    }
    public  void addRepresentation(IDocument tdoc, String npth, String zpth, String znam) throws Exception {
        if(zpth.isEmpty()) {return;}

        String mpth = npth + "/" + UUID.randomUUID().toString();
        Utils.loadDirectory(mpth);

        String _zpth = mpth + "/" + FilenameUtils.getBaseName(znam) + "." + FilenameUtils.getExtension(zpth);
        Utils.copyFile(zpth, _zpth);
        IRepresentation zrep = tdoc.addRepresentation("." + FilenameUtils.getExtension(zpth), FilenameUtils.getBaseName(znam));
        zrep.addPartDocument(_zpth);

        tdoc.setDefaultRepresentation(tdoc.getRepresentationList().length - 1);
    }
    private IInformationObject getDocumentIssue(String cprj, String prnt, String dtyp, String id) {
        StringBuilder builder = new StringBuilder();
        builder.append("TYPE = '").append(Conf.ClassIDs.Document).append("'")
                .append(" AND ")
                .append(Conf.DescriptorLiterals.Project).append(" = '").append(cprj).append("'")
                .append(" AND ")
                .append(Conf.DescriptorLiterals.Type).append(" = '").append(dtyp).append("'")
                .append(" AND ")
                .append(Conf.DescriptorLiterals.ParentID).append(" = '").append(prnt).append("'")
                .append(" AND ")
                .append(Conf.DescriptorLiterals.DocID).append(" = '").append(id).append("'");
        String whereClause = builder.toString();

        IInformationObject[] informationObjects = processHelper.createQuery(new String[]{Conf.Databases.Document}, whereClause, "", 1, false);
        if(informationObjects.length < 1) {return null;}
        return informationObjects[0];
    }
    private IFolder createFolderIssue() throws Exception{
        IFolderConnection folderConnection = Utils.session.getFolderConnection();
        IFolder rtrn = folderConnection.createFolder();
        IArchiveFolderClass afc = Utils.server.getArchiveFolderClass(Conf.ClassIDs.EFile, Utils.session);
        IDatabase db = Utils.session.getDatabase(afc.getDefaultDatabaseID());
        rtrn.init(afc);
        rtrn.setDatabaseName(db.getDatabaseName());
        return rtrn;
    }
    public static IDocument createDocumentIssue() throws Exception {
        IArchiveClass ac = Utils.server.getArchiveClass(Conf.ClassIDs.Document, Utils.session);
        if(ac == null){throw new Exception("ArchiveClass not found (Id : " + Conf.ClassIDs.Document + ")");}
        IDatabase db = Utils.session.getDatabase(ac.getDefaultDatabaseID());
        return Utils.server.getClassFactory().getDocumentInstance(db.getDatabaseName(), ac.getID(), "0000" , Utils.session);
    }
    private void saveInputStreamToFile(InputStream inputStream, File file)  throws Exception {
        try (FileOutputStream outputStream = new FileOutputStream(file, false)) {
            int read;
            byte[] bytes = new byte[8192];
            while ((read = inputStream.read(bytes)) != -1) {
                outputStream.write(bytes, 0, read);
            }
        }
    }
    public void downloadIssue(String conm, Issue issu, String ipth) throws Exception {

        String user = getCfgString(conm + ".username");
        String pass = getCfgString(conm + ".password");

        Unirest.setTimeouts(0, 0);
        HttpResponse<InputStream> resp = Unirest
                .get(issu.getUrl() + "?fieldsByKeys=true&properties=*all&updateHistory=true")
                .header("Authorization", "Basic " + Base64.getEncoder().encodeToString((user + ":" + pass).getBytes()))
                .asBinary();
        saveInputStreamToFile(resp.getBody(), new File(ipth));

    }
    public void downloadIssueDoc(String conm, Issue issu, String ipth) throws Exception {

        String addr = getCfgString(conm + ".address");
        String user = getCfgString(conm + ".username");
        String pass = getCfgString(conm + ".password");

        Unirest.setTimeouts(0, 0);
        HttpResponse<InputStream> resp = Unirest
                .get(addr + "si/jira.issueviews:issue-html/" + issu.getKey() + "/" + issu.getKey() + ".html")
                .header("Authorization", "Basic " + Base64.getEncoder().encodeToString((user + ":" + pass).getBytes()))
                .asBinary();
        saveInputStreamToFile(resp.getBody(), new File(ipth));

    }
    public String fileMD5Checksum(String fpth) throws Exception {
        byte[] data = Files.readAllBytes(Paths.get(fpth));
        byte[] hash = MessageDigest.getInstance("MD5").digest(data);
        return (new BigInteger(1, hash)).toString(16);
    }
    public void downloadAttachment(String conm, Attachment atch, String dpth) throws Exception {

        String user = getCfgString(conm + ".username");
        String pass = getCfgString(conm + ".password");

        Unirest.setTimeouts(0, 0);
        HttpResponse<InputStream> resp = Unirest
                .get(atch.getContentUrl() + "?redirect=false")
                .header("Authorization", "Basic " + Base64.getEncoder().encodeToString((user + ":" + pass).getBytes()))
                .asBinary();
        saveInputStreamToFile(resp.getBody(), new File(dpth));

    }
    public JSONObject getIssueDefnFields(JSONObject schm, Issue line) throws Exception{
        JSONObject rtrn = new JSONObject();
        for(String ifld : schm.keySet()){
            Object lobj = line.getField(schm.getString(ifld));
            String lval = "";
            if(lval.isEmpty() && lobj instanceof net.sf.json.JSONArray){
                if(((net.sf.json.JSONArray) lobj).size() > 0) {
                    net.sf.json.JSONObject l1st = ((net.sf.json.JSONArray) lobj).getJSONObject(0);
                    if (l1st.has("value")) {
                        lval = l1st.getString("value");
                    }
                }
            }
            if(lval.isEmpty() && lobj instanceof net.sf.json.JSONObject){
                if(((net.sf.json.JSONObject) lobj).has("value")){
                    lval = ((net.sf.json.JSONObject) lobj).getString("value");
                }
            }
            if(lval.isEmpty() && lobj instanceof java.lang.String){
                if(lobj != null){
                    lval = lobj.toString();
                }
            }
            rtrn.put(ifld, lval);
        }
        return rtrn;
    }
    public List<String> getIssueMustFields(String conm, String prjt) throws Exception{
        return Arrays.asList(getCfgString(conm + "." + prjt + ".mustFields").split(";"));
    }
    public List<String> getIssueRunTransitions(String conm, String prjt) throws Exception{
        return Arrays.asList(getCfgString(conm + "." + prjt + ".runTransitions").split(";"));
    }
    public JSONObject getIssueFieldScheme(String conm, String prjt) throws Exception{
        String[] flns = getCfgString(conm + "." + prjt + ".fields").split(";");
        JSONObject rtrn = new JSONObject();
        for(String flnm : flns){
            String jfld = getCfgString(conm + "." + prjt + ".field." + flnm);
            if(jfld.isEmpty()){continue;}
            rtrn.put(flnm, jfld);
        }
        return rtrn;
    }
    public String getCfgString(String name){
        String rtrn = !config.has(name)
                || config.getString(name) == null
                || config.getString(name).isEmpty() ? "" : config.getString(name);
        return rtrn;
    }
}