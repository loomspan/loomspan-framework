package ai.loomspan.internal.springai;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import java.util.Map;

/** Production tool boundary plus explicit invocation state for direct-ChatClient fixtures. */
public final class OrdinaryRepairTestClients {
    private OrdinaryRepairTestClients() {}

    public static ChatClient.Builder builder(ChatModel model) {
        return ChatClient.builder(model, ObservationRegistry.NOOP, null, null,
                ToolCallingAdvisor.builder().toolCallingManager(new CanonicalToolCallingManager(
                        ToolCallingManager.builder().unlimitedCallsPerTool().unlimitedTotalToolCalls().build())));
    }

    public static ChatClient.ChatClientRequestSpec prepare(ChatClient.ChatClientRequestSpec request) {
        var state = new OrdinaryFinalOutputRepair();
        return request.toolContext(Map.of(OrdinaryFinalOutputRepair.CONTEXT_KEY, state))
                .advisors(spec -> spec.param(OrdinaryFinalOutputRepair.CONTEXT_KEY, state));
    }
}
