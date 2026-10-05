package ai.loomspan.internal.runtime.tool;

import ai.loomspan.internal.core.CapabilityMetadata;
import ai.loomspan.internal.core.MissionContext.CompletedTaskResult;
import ai.loomspan.internal.runtime.input.ChildInputBinding;
import ai.loomspan.internal.runtime.input.ChildInputBindingProjection;
import ai.loomspan.internal.runtime.input.SkillInputContract;
import java.util.List;
import org.springframework.lang.Nullable;

import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Objects;

public final class BoundCapability
{
    @FunctionalInterface
    public interface Invocation
    {
        Object invoke(Map<String, Object> arguments, @Nullable String linkedTaskId, List<CompletedTaskResult> sourceResults);
    }

    private final CapabilityMetadata metadata;
    private final Invocation invocation;
    private final ChildInputBindingProjection projection;
    private final List<ChildInputBinding> inputBindings;

    public BoundCapability(CapabilityMetadata metadata, List<ChildInputBinding> inputBindings, Invocation invocation)
    {
        this.metadata = Objects.requireNonNull(metadata, "metadata must not be null");
        this.inputBindings = List.copyOf(inputBindings);
        this.projection = new ChildInputBindingProjection(metadata.inputContract(), inputBindings);
        this.invocation = Objects.requireNonNull(invocation, "invocation must not be null");
    }

    public CapabilityMetadata metadata() { return metadata; }
    public String name() { return metadata.name(); }
    public String description() { return metadata.tool().description(); }
    public String outputSchema() { return metadata.tool().outputSchema(); }
    public String inputSchema() { return inputBindings.isEmpty() ? metadata.tool().inputSchema() : projection.inputSchema(); }
    public SkillInputContract argumentContract() { return projection.argumentContract(); }
    public List<ChildInputBinding> inputBindings() { return inputBindings; }
    public List<String> validateModelArguments(Map<String, Object> arguments) { return projection.validateModelArguments(arguments); }

    public Object invoke(Map<String, Object> arguments, @Nullable String linkedTaskId)
    {
        return invokeAssigned(arguments, linkedTaskId, List.of());
    }

    public Object invokeAssigned(Map<String, Object> arguments, @Nullable String linkedTaskId,
            List<CompletedTaskResult> sourceResults)
    {
        Map<String, Object> copiedArguments = arguments == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
        return invocation.invoke(copiedArguments, linkedTaskId, List.copyOf(sourceResults));
    }
}
