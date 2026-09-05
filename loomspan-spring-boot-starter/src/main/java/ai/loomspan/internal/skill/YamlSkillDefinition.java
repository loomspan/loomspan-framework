package ai.loomspan.internal.skill;

import tools.jackson.databind.ObjectMapper;
import ai.loomspan.internal.runtime.evidence.EvidenceContract;
import org.springframework.core.io.Resource;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Stable typed catalog entry for a YAML skill manifest and its resolved execution configuration.
 */
public record YamlSkillDefinition(
        Resource resource,
        YamlSkillManifest manifest,
        @Nullable EffectiveSkillExecutionConfiguration executionConfiguration,
        EvidenceContract evidenceContract,
        YamlSkillSource source,
        List<AllowedSkillConstraint> allowedSkillConstraints)
{
    private static final ObjectMapper COPY_MAPPER =
            ai.loomspan.internal.serialization.LoomspanJacksonCodecs.defaults().applicationConversion();

    public YamlSkillDefinition
    {
        manifest = copyManifest(manifest);
        allowedSkillConstraints = allowedSkillConstraints == null
                ? manifest.getAllowedSkills().stream().map(AllowedSkillConstraint::from).toList()
                : List.copyOf(allowedSkillConstraints);
        evidenceContract = evidenceContract == null ? EvidenceContract.empty() : evidenceContract;
        source = source == null
                ? new YamlSkillSource(resource, resource.getDescription(), new byte[0])
                : source;
        if (executionConfiguration == null)
        {
            throw new IllegalArgumentException("LLM-backed YAML skill definitions require an execution configuration");
        }
        if (manifest.isDeclared(YamlSkillManifest.Field.CONCURRENCY)
                && !Boolean.TRUE.equals(manifest.getPlanningMode()))
        {
            throw new IllegalArgumentException(
                    "LLM-backed YAML skill definitions may declare 'concurrency' only when planning_mode is explicitly true");
        }
        if (manifest.isDeclared(YamlSkillManifest.Field.CONCURRENCY)
                && manifest.getConcurrency() == null)
        {
            throw new IllegalArgumentException("LLM-backed YAML skill definitions require a non-null 'concurrency' value");
        }
    }

    public YamlSkillDefinition(Resource resource,
            YamlSkillManifest manifest,
            EffectiveSkillExecutionConfiguration executionConfiguration)
    {
        this(resource, manifest, executionConfiguration, EvidenceContract.empty(), null, null);
    }

    public YamlSkillDefinition(Resource resource,
            YamlSkillManifest manifest,
            EffectiveSkillExecutionConfiguration executionConfiguration,
            EvidenceContract evidenceContract)
    {
        this(resource, manifest, executionConfiguration, evidenceContract, null, null);
    }

    public YamlSkillDefinition(Resource resource,
            YamlSkillManifest manifest,
            EffectiveSkillExecutionConfiguration executionConfiguration,
            EvidenceContract evidenceContract,
            YamlSkillSource source)
    {
        this(resource, manifest, executionConfiguration, evidenceContract, source, null);
    }

    public List<String> allowedSkills()
    {
        return allowedSkillConstraints.stream().map(AllowedSkillConstraint::name).toList();
    }

    public List<String> rbacRoles()
    {
        return manifest.getRbacRoles();
    }

    public YamlSkillManifest.LinterManifest linter()
    {
        return copyValue(manifest.getLinter(), YamlSkillManifest.LinterManifest.class);
    }

    public YamlSkillManifest.OutputSchemaManifest outputSchema()
    {
        return copyValue(manifest.getOutputSchema(), YamlSkillManifest.OutputSchemaManifest.class);
    }

    public String prompt()
    {
        return manifest.getPrompt();
    }

    public YamlSkillManifest.InputSchemaManifest inputSchema()
    {
        return copyValue(manifest.getInputSchema(), YamlSkillManifest.InputSchemaManifest.class);
    }

    public boolean hasDeclaredInputSchema()
    {
        return manifest.getInputSchema() != null;
    }



    public boolean hasGenericInputContract()
    {
        return !hasDeclaredInputSchema();
    }

    public int outputSchemaMaxRetries()
    {
        return manifest.getOutputSchemaMaxRetries() == null ? 0 : manifest.getOutputSchemaMaxRetries();
    }



    /** Returns a defensive copy so catalog state cannot be mutated after registration. */
    public YamlSkillManifest manifest()
    {
        return copyManifest(manifest);
    }



    public EffectiveSkillExecutionConfiguration requireExecutionConfiguration()
    {
        if (executionConfiguration == null)
        {
            throw new IllegalStateException("LLM-backed execution requires an execution configuration");
        }
        return executionConfiguration;
    }

    public boolean planningModeEnabled(boolean defaultValue)
    {
        return manifest.getPlanningMode() == null ? defaultValue : manifest.getPlanningMode();
    }

    public boolean planningModeExplicitlyEnabled()
    {
        return Boolean.TRUE.equals(manifest.getPlanningMode());
    }

    public boolean concurrencyEnabled()
    {
        return planningModeExplicitlyEnabled() && !Boolean.FALSE.equals(manifest.getConcurrency());
    }

    public int maxSteps(int defaultValue)
    {
        return manifest.getMaxSteps() == null ? defaultValue : manifest.getMaxSteps();
    }

    private static YamlSkillManifest copyManifest(YamlSkillManifest source)
    {
        if (source == null)
        {
            throw new NullPointerException("manifest must not be null");
        }
        YamlSkillManifest copy = COPY_MAPPER.convertValue(source, YamlSkillManifest.class);
        copy.restoreDeclaredFields(source.declaredFields());
        return copy;
    }



    private static <T> T copyValue(T source, Class<T> type)
    {
        return source == null ? null : COPY_MAPPER.convertValue(source, type);
    }
}
