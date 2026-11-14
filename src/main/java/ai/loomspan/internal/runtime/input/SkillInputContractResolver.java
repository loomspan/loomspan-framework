package ai.loomspan.internal.runtime.input;

import ai.loomspan.internal.schema.CanonicalSchema;
import ai.loomspan.internal.schema.StandardSchemaCompiler;
import ai.loomspan.internal.serialization.LoomspanJacksonCodecs;
import ai.loomspan.internal.skill.YamlSkillDefinition;
import java.util.Objects;

public class SkillInputContractResolver
{
    private final StandardSchemaCompiler compiler = new StandardSchemaCompiler();

    public SkillInputContractResolver() {}

    public SkillInputContract resolveJavaCapability(String inputSchema)
    {
        return new SkillInputContract(SkillInputContract.SkillInputContractKind.JAVA_REFLECTED,
                fromJsonSchema(inputSchema));
    }

    public SkillInputContract resolveYamlCapability(YamlSkillDefinition definition)
    {
        Objects.requireNonNull(definition, "definition must not be null");
        return definition.hasDeclaredInputSchema()
                ? new SkillInputContract(SkillInputContract.SkillInputContractKind.YAML_EXPLICIT, definition.inputSchema())
                : SkillInputContract.genericObject();
    }

    public SkillInputContract resolveFromToolSchema(String inputSchema)
    {
        if (inputSchema == null || inputSchema.isBlank()) return SkillInputContract.genericObject();
        CanonicalSchema schema = fromJsonSchema(inputSchema);
        var tree = schema.tree();
        // Only a genuinely shape-free object tool contract retains generic reference behavior.
        boolean generic = tree.path("properties").isEmpty() && tree.path("required").isEmpty()
                && (!tree.has("additionalProperties") || tree.path("additionalProperties").isBoolean()
                    && tree.path("additionalProperties").booleanValue())
                && tree.propertyNames().stream().allMatch(name -> java.util.Set.of(
                    "type", "properties", "required", "additionalProperties", "description", "$schema").contains(name));
        return generic ? SkillInputContract.genericObject()
                : new SkillInputContract(SkillInputContract.SkillInputContractKind.JAVA_REFLECTED, schema);
    }

    public String toJsonSchema(SkillInputContract contract)
    {
        return LoomspanJacksonCodecs.defaults().schemaTree().writeValueAsString(
                Objects.requireNonNull(contract, "contract must not be null").schema().providerTree());
    }

    public CanonicalSchema fromJsonSchema(String inputSchema)
    {
        if (inputSchema == null || inputSchema.isBlank()) return SkillInputContract.genericObject().schema();
        return compiler.compile(inputSchema, StandardSchemaCompiler.Role.INPUT, StandardSchemaCompiler.Origin.GENERATED);
    }
}
