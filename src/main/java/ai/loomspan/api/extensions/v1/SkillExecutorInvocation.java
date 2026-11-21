package ai.loomspan.api.extensions.v1;

import ai.loomspan.internal.util.ImmutableContainers;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Framework-created v1 executor handoff. Business map/list containers are copied
 * and unmodifiable; other leaves, including resource handles, retain identity.
 * Lifecycle accessors observe the captured execution live, including its ancestors.
 * Applications receive this value through an annotated executor method.
 */
public final class SkillExecutorInvocation
{
    private final Map<String, Object> input;
    private final Map<String, Object> skillConfig;
    private final Map<String, Object> executorConfig;
    private final Map<String, Object> consumerExecutorConfig;
    private final String skillName;
    private final String executorConfigGenerationId;
    private final ExecutorDiagnostics diagnostics;
    private final BooleanSupplier cancelled;
    private final Supplier<Optional<Duration>> remaining;

    private SkillExecutorInvocation(Map<String, Object> input, Map<String, Object> skillConfig,
            Map<String, Object> executorConfig, Map<String, Object> consumerExecutorConfig, String skillName, String executorConfigGenerationId,
            BooleanSupplier cancelled, Supplier<Optional<Duration>> remaining, ExecutorDiagnostics diagnostics)
    {
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(skillConfig, "skillConfig must not be null");
        Objects.requireNonNull(executorConfig, "executorConfig must not be null");
        Objects.requireNonNull(consumerExecutorConfig, "consumerExecutorConfig must not be null");
        Objects.requireNonNull(skillName, "skillName must not be null");
        Objects.requireNonNull(executorConfigGenerationId, "executorConfigGenerationId must not be null");
        if (skillName.isBlank()) throw new IllegalArgumentException("skillName must not be blank");
        if (executorConfigGenerationId.isBlank()) throw new IllegalArgumentException("executorConfigGenerationId must not be blank");
        this.input = ImmutableContainers.copyMap(input);
        this.skillConfig = ImmutableContainers.copyMap(skillConfig);
        this.executorConfig = ImmutableContainers.copyMap(executorConfig);
        this.consumerExecutorConfig = ImmutableContainers.copyMap(consumerExecutorConfig);
        this.skillName = skillName;
        this.executorConfigGenerationId = executorConfigGenerationId;
        this.diagnostics = Objects.requireNonNull(diagnostics);
        this.cancelled = Objects.requireNonNull(cancelled);
        this.remaining = Objects.requireNonNull(remaining);
    }

    public Map<String, Object> input() { return input; }
    public Map<String, Object> skillConfig() { return skillConfig; }
    public Map<String, Object> executorConfig() { return executorConfig; }
    /** Selected configuration from the executing YAML leaf's namespace for a foreign executor.
     * Empty for absent entries, same-namespace execution or no selector; never merged with owner data. */
    public Map<String, Object> consumerExecutorConfig() { return consumerExecutorConfig; }
    public String skillName() { return skillName; }
    public String executorConfigGenerationId() { return executorConfigGenerationId; }

    /** Invocation-scoped bounded developer diagnostics; retained handles close with executor work. */
    public ExecutorDiagnostics diagnostics()
    {
        return diagnostics;
    }

    /** Live Framework cancellation of this execution or an ancestor; does not clear interruption. */
    public boolean isCancelled() { return cancelled.getAsBoolean(); }

    /**
     * Remaining execution time, limited by ancestors and Framework shutdown.
     * Empty means no deadline; expiry returns zero. Cleanup grace is excluded.
     * This observation neither cancels physical I/O nor grants permission to send.
     */
    public Optional<Duration> remainingTime() { return remaining.get(); }
}
