package junit

import de.ser.doxis4.agentserver.AgentExecutionResult
import org.junit.*
import ser.GIBJiraTicketClassification

class TEST_GIBJiraTicketClassification {

    Binding binding

    @BeforeClass
    static void initSessionPool() {
        AgentTester.initSessionPool()
    }

    @Before
    void retrieveBinding() {
        binding = AgentTester.retrieveBinding()
    }

    @Test
    void testForAgentResult() {
        def agent = new GIBJiraTicketClassification();

        binding["AGENT_EVENT_OBJECT_CLIENT_ID"] = "SD0dGIB_JIRA_DOCS244882af0d-407c-4f84-99e5-b821b4f1f8a2182024-05-16T13:26:34.182Z011"

        def result = (AgentExecutionResult) agent.execute(binding.variables)
        assert result.resultCode == 0
    }

    @Test
    void testForJavaAgentMethod() {
        //def agent = new JavaAgent()
        //agent.initializeGroovyBlueline(binding.variables)
        //assert agent.getServerVersion().contains("Linux")
    }

    @After
    void releaseBinding() {
        AgentTester.releaseBinding(binding)
    }

    @AfterClass
    static void closeSessionPool() {
        AgentTester.closeSessionPool()
    }
}
