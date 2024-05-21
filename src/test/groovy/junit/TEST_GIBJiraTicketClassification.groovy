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

        binding["AGENT_EVENT_OBJECT_CLIENT_ID"] = "SD0dGIB_JIRA_DOCS24a5366110-57b0-4148-8957-e95715d36420182024-05-17T12:39:27.435Z011"

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
