package ai.loomspan.internal.springai;

import ai.loomspan.internal.runtime.tool.BoundCapability;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.DefaultToolCallResultConverter;
import org.springframework.ai.tool.execution.ToolExecutionException;

import java.util.List;
import java.util.Map;

final class SpringAiToolCallbackAdapter
{
    private static final DefaultToolCallResultConverter RESULTS = new DefaultToolCallResultConverter();

    private SpringAiToolCallbackAdapter() {}

    static List<ToolCallback> adapt(List<BoundCapability> capabilities)
    {
        return capabilities.stream().map(SpringAiToolCallbackAdapter::adapt).toList();
    }

    private static ToolCallback adapt(BoundCapability capability)
    {
        ToolDefinition definition = ToolDefinition.builder().name(capability.name())
                .description(capability.description()).inputSchema(capability.inputSchema()).build();
        return new ToolCallback()
        {
            @Override public ToolDefinition getToolDefinition() { return definition; }

            @Override public String call(String input) { return call(input, null); }

            @Override public String call(String input, ToolContext context)
            {
                Map<String, Object> arguments = OrdinaryToolArguments.read(input);
                Object result;
                try
                {
                    result = capability.invoke(arguments, null);
                }
                catch (ToolExecutionException ex)
                {
                    throw ex;
                }
                catch (Exception ex)
                {
                    throw new ToolExecutionException(definition, ex);
                }
                return RESULTS.convert(result, null);
            }
        };
    }

}
