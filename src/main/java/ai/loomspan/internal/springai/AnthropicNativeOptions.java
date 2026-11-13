package ai.loomspan.internal.springai;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.OutputConfig;
import org.springframework.ai.anthropic.AnthropicChatOptions;

import java.util.Map;

/** Native format assembly shared by internal consumers; does not validate schemas. */
final class AnthropicNativeOptions
{
    private AnthropicNativeOptions() {}

    static AnthropicChatOptions withOutputSchema(AnthropicChatOptions defaults, Map<String, Object> schema)
    {
        JsonOutputFormat.Schema.Builder nativeSchema = JsonOutputFormat.Schema.builder();
        schema.forEach((name, value) -> nativeSchema.putAdditionalProperty(name, JsonValue.from(value)));
        OutputConfig.Builder output = defaults.getOutputConfig() == null
                ? OutputConfig.builder() : defaults.getOutputConfig().toBuilder();
        output.format(JsonOutputFormat.builder().schema(nativeSchema.build()).build());
        return defaults.mutate().outputConfig(output.build()).build();
    }
}
