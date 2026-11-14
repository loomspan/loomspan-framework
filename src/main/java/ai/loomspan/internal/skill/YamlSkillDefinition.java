package ai.loomspan.internal.skill;

import ai.loomspan.internal.core.SkillIdentity;

import tools.jackson.databind.ObjectMapper;
import ai.loomspan.internal.runtime.evidence.EvidenceContract;
import org.springframework.core.io.Resource;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Stable typed catalog entry for a YAML skill manifest and its resolved execution configuration.
 */
public final class YamlSkillDefinition
{
    public enum Form { MODEL, TYPESAFE, EXECUTOR }
    private final Form form;
    public Form form() { return form; }
    public String executor() { return manifest.getExecutor(); }
    public java.util.Map<String, Object> config() { return manifest.getConfig(); }
    private final Resource resource;
    private final YamlSkillManifest manifest;
    private final EffectiveSkillExecutionConfiguration executionConfiguration;
    private final EvidenceContract evidenceContract;
    private final YamlSkillSource source;
    private final List<AllowedSkillConstraint> allowedSkillConstraints;
    private final SkillIdentity identity;
    private final SkillIdentity outputFromIdentity;
    private final List<ai.loomspan.internal.runtime.input.ChildInputBinding> preparedOutputBindings;
    public Resource resource() { return resource; }
    public EffectiveSkillExecutionConfiguration executionConfiguration() { return executionConfiguration; }
    public EvidenceContract evidenceContract() { return evidenceContract; }
    public YamlSkillSource source() { return source; }
    public List<AllowedSkillConstraint> allowedSkillConstraints() { return allowedSkillConstraints; }
    public SkillIdentity identity() { return identity; }
    public SkillIdentity outputFromIdentity() { return outputFromIdentity; }
    private static final ObjectMapper COPY_MAPPER =
            ai.loomspan.internal.serialization.LoomspanJacksonCodecs.defaults().applicationConversion();

    public YamlSkillDefinition(Form form, String namespace, Resource resource, YamlSkillManifest manifest,
            EffectiveSkillExecutionConfiguration executionConfiguration, EvidenceContract evidenceContract,
            YamlSkillSource source, List<AllowedSkillConstraint> allowedSkillConstraints)
    {
        manifest = copyManifest(manifest);
        allowedSkillConstraints = allowedSkillConstraints == null
                ? manifest.getAllowedSkills().stream().map(AllowedSkillConstraint::from).toList()
                : List.copyOf(allowedSkillConstraints);
        evidenceContract = evidenceContract == null ? EvidenceContract.empty() : evidenceContract;
        source = source == null
                ? new YamlSkillSource(resource, resource.getDescription(), new byte[0])
                : source;
        this.form = java.util.Objects.requireNonNull(form, "form must not be null");
        if (form == Form.EXECUTOR) java.util.Objects.requireNonNull(manifest.getExecutor(), "executor reference must not be null");
        if ((form == Form.EXECUTOR) == (executionConfiguration != null))
            throw new IllegalArgumentException("executor definitions require absent model execution configuration; model/TypeSafe definitions require execution configuration");
        this.resource = java.util.Objects.requireNonNull(resource);
        this.manifest = manifest;
        this.executionConfiguration = executionConfiguration;
        this.evidenceContract = evidenceContract;
        this.source = source;
        this.allowedSkillConstraints = allowedSkillConstraints;
        this.identity = new SkillIdentity(namespace, manifest.getName());
        this.outputFromIdentity = manifest.getOutputFrom() == null ? null
                : SkillIdentity.parse(manifest.getOutputFrom().getSkill());
        this.preparedOutputBindings = compileOutputBindings();
    }

    public YamlSkillDefinition(Form form, String namespace, Resource resource,
            YamlSkillManifest manifest,
            EffectiveSkillExecutionConfiguration executionConfiguration)
    {
        this(form, namespace, resource, manifest, executionConfiguration, EvidenceContract.empty(), null, null);
    }

    public YamlSkillDefinition(Form form, String namespace, Resource resource,
            YamlSkillManifest manifest,
            EffectiveSkillExecutionConfiguration executionConfiguration,
            EvidenceContract evidenceContract)
    {
        this(form, namespace, resource, manifest, executionConfiguration, evidenceContract, null, null);
    }

    public YamlSkillDefinition(Form form, String namespace, Resource resource,
            YamlSkillManifest manifest,
            EffectiveSkillExecutionConfiguration executionConfiguration,
            EvidenceContract evidenceContract,
            YamlSkillSource source)
    {
        this(form, namespace, resource, manifest, executionConfiguration, evidenceContract, source, null);
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

    public ai.loomspan.internal.schema.CanonicalSchema outputSchema()
    {
        return typesafe() ? typeSafeDeclaration().outputSchema() : manifest.compiledOutputSchema();
    }

    /** Exact direct child selected for runtime result forwarding, or null for synthesis. */
    public String outputFromSkill() {
        return outputFromIdentity == null ? null : outputFromIdentity.toString();
    }

    public List<ai.loomspan.internal.runtime.input.ChildInputBinding> outputBindings() { return preparedOutputBindings; }

    private List<ai.loomspan.internal.runtime.input.ChildInputBinding> compileOutputBindings() {
        return manifest.getOutputBindings().entrySet().stream().map(entry -> {
            var descriptor = entry.getValue();
            return new ai.loomspan.internal.runtime.input.ChildInputBinding(
                    ai.loomspan.internal.runtime.input.ObjectFieldPath.parse(entry.getKey(), false),
                    switch (descriptor.getFrom()) {
                        case "input" -> ai.loomspan.internal.runtime.input.ChildInputBinding.SourceKind.INPUT;
                        case "child_result" -> ai.loomspan.internal.runtime.input.ChildInputBinding.SourceKind.CHILD_RESULT;
                        default -> throw new IllegalArgumentException("unknown binding source kind");
                    }, ai.loomspan.internal.runtime.input.ObjectFieldPath.parse(descriptor.getPath(), true), descriptor.getSkill());
        }).toList();
    }

    public boolean isOutputBindingsDeclared() { return manifest.isOutputBindingsDeclared(); }

    public String prompt()
    {
        return manifest.getPrompt();
    }

    public ai.loomspan.internal.schema.CanonicalSchema inputSchema()
    {
        return manifest.compiledInputSchema();
    }

    public boolean hasDeclaredInputSchema()
    {
        return manifest.getInputSchema() != null;
    }



    public boolean hasGenericInputContract()
    {
        return !hasDeclaredInputSchema();
    }

    public boolean typesafe() { return form == Form.TYPESAFE; }

    public ai.loomspan.internal.typesafe.TypeSafeDeclaration typeSafeDeclaration() {
        if (!typesafe()) throw new IllegalStateException("Skill has no TypeSafe declaration");
        return manifest.compiledTypesafe();
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
        // Copy ordinary manifest fields without sending schema/native JSON through application conversion.
        YamlSkillManifest copy = new YamlSkillManifest();
        copy.setName(source.getName());
        copy.setDescription(source.getDescription());
        copy.setModel(source.getModel());
        copy.setPrompt(source.getPrompt());
        copy.setExecutor(source.getExecutor());
        copy.setThinkingLevel(source.getThinkingLevel());
        copy.setAllowedSkills(source.getAllowedSkills().stream()
                .map(value -> copyValue(value, YamlSkillManifest.AllowedSkillManifest.class)).toList());
        copy.setRbacRoles(source.getRbacRoles());
        copy.setPlanningMode(source.getPlanningMode());
        copy.setConcurrency(source.getConcurrency());
        copy.setMaxSteps(source.getMaxSteps());
        copy.setLinter(copyValue(source.getLinter(), YamlSkillManifest.LinterManifest.class));
        copy.setOutputFrom(copyValue(source.getOutputFrom(), YamlSkillManifest.OutputFromManifest.class));
        var bindings = new java.util.LinkedHashMap<String, YamlSkillManifest.InputBindingManifest>();
        source.getOutputBindings().forEach((name, value) ->
                bindings.put(name, copyValue(value, YamlSkillManifest.InputBindingManifest.class)));
        copy.setOutputBindings(bindings);
        copy.setOutputSchemaMaxRetries(source.getOutputSchemaMaxRetries());
        // Share the immutable compiled declaration while detaching its mutable authored JSON.
        copy.copyTypesafeFrom(source);
        copy.copySchemasFrom(source);
        if (source.isDeclared(YamlSkillManifest.Field.CONFIG)) copy.setConfig(source.getConfig());
        copy.declarationForm(source.declarationForm());
        copy.restoreDeclaredFields(source.declaredFields());
        return copy;
    }



    private static <T> T copyValue(T source, Class<T> type)
    {
        return source == null ? null : COPY_MAPPER.convertValue(source, type);
    }

}
