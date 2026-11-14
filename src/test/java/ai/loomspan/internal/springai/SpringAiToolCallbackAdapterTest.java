package ai.loomspan.internal.springai;

import ai.loomspan.internal.runtime.tool.BoundCapability;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.execution.DefaultToolCallResultConverter;
import org.springframework.ai.tool.execution.ToolExecutionException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real callback codec; capability is mocked because construction enters binding projection. */
class SpringAiToolCallbackAdapterTest
{
    @Test void adaptsUnconstrainedSchemaVerbatimToSpringAiToolCallback()
    {
        String schema = """
                {"type":"object","properties":{"options":{"type":"object","additionalProperties":{}}},
                 "required":["options"],"additionalProperties":false}
                """;
        var capability = capability(schema);
        var callback = adapt(capability);
        assertThat(callback.getToolDefinition().name()).isEqualTo("a_b-rankTransportOptions");
        assertThat(callback.getToolDefinition().description()).isEqualTo("description exact");
        assertThat(callback.getToolDefinition().inputSchema()).isEqualTo(schema);
        assertThat(callback.getToolMetadata().returnDirect()).isFalse();
        assertThat(callback.getToolMetadata().requiresConfirmation()).isFalse();
    }

    @Test void preservesNestedFractionsExponentsLargeIntegersAndNulls()
    {
        var capability = capability("{\"type\":\"object\"}");
        var received = new AtomicReference<Map<String, Object>>();
        when(capability.invoke(anyMap(), isNull())).thenAnswer(call -> {
            received.set(call.getArgument(0));
            return Map.of("success", true);
        });
        var callback = adapt(capability);
        String input = """
                {"fraction":0.12345678901234567890123456789,
                 "nested":{"values":[1.234567890123456789e-30,9.876543210987654321e30,null]},
                 "integer":1234567890123456789012345678901234567890}
                """;
        assertThat(new ObjectMapper().readTree(callback.call(input)).path("success").booleanValue()).isTrue();
        assertThat(received.get().get("fraction")).isEqualTo(new BigDecimal("0.12345678901234567890123456789"));
        assertThat(received.get().get("integer")).isEqualTo(new BigInteger("1234567890123456789012345678901234567890"));
        var nested = (Map<?, ?>) received.get().get("nested");
        assertThat(nested.get("values")).isEqualTo(java.util.Arrays.asList(
                new BigDecimal("1.234567890123456789e-30"), new BigDecimal("9.876543210987654321e30"), null));
        verify(capability, times(1)).invoke(anyMap(), isNull());
    }

    @ParameterizedTest @NullSource
    @ValueSource(strings = {"", " ", "{", "null", "\"\"", "\"text\"", "[]", "[{}]", "1", "1.25", "true", "false",
            "{} {}", "{} 1", "{} \"text\"", "{} true", "{} null", "{} trailing garbage"})
    void rejectsInvalidWholeDocumentsBeforeCapabilityInvocation(String input)
    {
        var capability = capability("{\"type\":\"object\"}");
        var callback = adapt(capability);
        assertThatThrownBy(() -> callback.call(input)).isInstanceOf(IllegalArgumentException.class);
        verify(capability, never()).invoke(any(), any());
    }

    @Test void acceptsEmptyObjectsWhitespaceNestedNullAndContextOverload()
    {
        var capability = capability("{\"type\":\"object\"}");
        var callback = adapt(capability);
        callback.call("{} \t\r\n");
        callback.call("{\"value\":null} \t", new ToolContext(Map.of("ignored", "same as previous adapter")));
        verify(capability).invoke(eq(Map.of()), isNull());
        var capture = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(capability, times(2)).invoke(capture.capture(), isNull());
        assertThat(capture.getAllValues().getLast()).containsEntry("value", null);
    }

    @Test void preservesSpringResultConversionAndExceptionSemantics()
    {
        var converter = new DefaultToolCallResultConverter();
        var capability = capability("{\"type\":\"object\"}");
        var callback = adapt(capability);
        for (Object value : new Object[]{"text result", null, Map.of("list", List.of(1, true))}) {
            when(capability.invoke(anyMap(), isNull())).thenReturn(value);
            assertThat(callback.call("{}")).isEqualTo(converter.convert(value, null));
        }
        var failure = new IllegalArgumentException("original invocation failure");
        when(capability.invoke(anyMap(), isNull())).thenThrow(failure);
        assertThatThrownBy(() -> callback.call("{}")).isInstanceOf(ToolExecutionException.class).hasCause(failure);
        var wrapped = new ToolExecutionException(callback.getToolDefinition(), failure);
        doThrow(wrapped).when(capability).invoke(anyMap(), isNull());
        assertThatThrownBy(() -> callback.call("{}")).isSameAs(wrapped);
    }

    static BoundCapability capability(String schema)
    {
        var capability = mock(BoundCapability.class);
        when(capability.name()).thenReturn("a_b-rankTransportOptions");
        when(capability.description()).thenReturn("description exact");
        when(capability.inputSchema()).thenReturn(schema);
        return capability;
    }

    static ToolCallback adapt(BoundCapability capability)
    {
        return SpringAiToolCallbackAdapter.adapt(List.of(capability)).getFirst();
    }
}
