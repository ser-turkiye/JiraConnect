package ser;

public class Conf {
    public static class CfgNames {
        public static final String IntegrationName = "JIRA_CONN";
    }
    public static class Paths {
        public static final String MainPath = "C:/tmp2/bulk/jira-integration";
    }
    public static class Databases{
        public static final String Main = "GIB_JIRA";
        public static final String Document = "GIB_JIRA_DOCS";
    }
    public static class ClassIDs{
        public static final String Issue = "8247ab44-47d5-4660-84a2-e6203107c7a1";
        public static final String Document = "94e7df22-126d-4100-a80d-767715256fc9";
    }
    public static class Descriptors{
        public static final String Project = "ObjectName";
        public static final String DocID = "ObjectDocID";
        public static final String ParentID = "ObjectDocumentReference";
        public static final String Type = "ObjectType";
        public static final String Name = "ObjectDescription";
        public static final String Status = "ObjectState";
        public static final String GIB_DocumentType = "_DocumentType";
        public static final String GIB_MainFolder = "_MainFolder";
    }
    public static class DescriptorLiterals{
        public static final String Project = "OBJECTNAME";
        public static final String DocID = "OBJECTDOCID";
        public static final String ParentID = "OBJECTDOCUMENTREFERENCE";
        public static final String Type = "OBJECTTYPE";
    }
}
