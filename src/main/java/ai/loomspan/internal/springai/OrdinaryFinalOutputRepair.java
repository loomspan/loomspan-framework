package ai.loomspan.internal.springai;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolExecutionResult;

/** Invocation-owned completed work and raw reply; never stored on a client or advisor. */
public final class OrdinaryFinalOutputRepair {
    public static final String CONTEXT_KEY = "loomspan.ordinary-final-output-repair";
    private static final String SEGMENT_KEY = CONTEXT_KEY + ".segment";
    private final List<Message> completed = new ArrayList<>();
    private AssistantMessage latestRaw;
    private boolean terminal;

    public void captureRaw(AssistantMessage message) {
        latestRaw = Objects.requireNonNull(message);
    }

    public static void capture(ChatClientRequest request, ChatClientResponse response) {
        var state = request.context().get(CONTEXT_KEY);
        if (state instanceof OrdinaryFinalOutputRepair repair) {
            repair.latestRaw = response == null || response.chatResponse() == null
                    || response.chatResponse().getResult() == null ? null : response.chatResponse().getResult().getOutput();
        }
    }

    static OrdinaryFinalOutputRepair from(Prompt prompt) {
        if (prompt.getOptions() instanceof ToolCallingChatOptions options
                && options.getToolContext() != null
                && options.getToolContext().get(CONTEXT_KEY) instanceof OrdinaryFinalOutputRepair state) return state;
        return null;
    }

    void requireDispatchAllowed() {
        if (terminal) throw new IllegalStateException("Tool execution is prohibited during ordinary final-output repair");
    }

    void completed(Prompt prompt, ToolExecutionResult result) {
        var baseline = prompt.getInstructions();
        var history = result.conversationHistory();
        if (history.size() < baseline.size() || !history.subList(0, baseline.size()).equals(baseline))
            throw new IllegalStateException("Tool history does not retain its request prefix");
        completed.addAll(history.subList(baseline.size(), history.size()));
    }

    /** Replace only the explicitly owned segment; feedback outside it remains intact. */
    public static ChatClientRequest repair(ChatClientRequest request, UnaryOperator<Prompt> feedback,
            List<? extends Message> trailingFeedback) {
        if (!(request.context().get(CONTEXT_KEY) instanceof OrdinaryFinalOutputRepair state))
            throw new IllegalStateException("Ordinary final-output repair is missing its call-local context");
        state.terminal = true;
        if (state.latestRaw == null) throw new IllegalStateException("Ordinary final-output repair is missing its raw provider reply");
        var messages = new ArrayList<>(request.prompt().getInstructions());
        if (request.context().get(SEGMENT_KEY) instanceof Segment segment) {
            if (segment.owner != state || segment.start < 0 || segment.start + segment.messages.size() > messages.size())
                throw new IllegalStateException("Invalid ordinary repair replay ownership");
            for (int i = 0; i < segment.messages.size(); i++) {
                if (messages.get(segment.start + i) != segment.messages.get(i))
                    throw new IllegalStateException("Ordinary repair replay segment changed outside its owner");
            }
            messages.subList(segment.start, segment.start + segment.messages.size()).clear();
        }
        Prompt baseline = feedback.apply(new Prompt(messages, request.prompt().getOptions()));
        var replay = new ArrayList<>(state.completed);
        replay.add(state.latestRaw);
        replay.addAll(trailingFeedback);
        var all = new ArrayList<>(baseline.getInstructions());
        int start = all.size();
        all.addAll(replay);
        if (!(baseline.getOptions() instanceof ToolCallingChatOptions options))
            throw new IllegalStateException("Ordinary repair requires typed tool-calling options");
        // An explicit empty callback list overrides the typed request/default tool surface.
        ToolCallingChatOptions copy;
        if (options instanceof org.springframework.ai.openai.responses.OpenAiResponsesChatOptions typed) {
            copy = typed.mutate().toolCallbacks(List.of()).hostedTools(List.of()).build();
        } else if (options instanceof org.springframework.ai.google.genai.GoogleGenAiChatOptions typed) {
            copy = typed.mutate().toolCallbacks(List.of()).googleSearchRetrieval(false).build();
        } else {
            copy = options.mutate().toolCallbacks(List.of()).build();
        }
        return request.mutate().prompt(new Prompt(all, copy))
                .context(SEGMENT_KEY, new Segment(state, start, List.copyOf(replay))).build();
    }

    private record Segment(OrdinaryFinalOutputRepair owner, int start, List<Message> messages) { }
}
