package ai.loomspan.internal.runtime.input;

import ai.loomspan.internal.schema.CanonicalSchema;
import ai.loomspan.internal.schema.StandardSchemaCompiler;
import java.util.Objects;

public record SkillInputContract(
        SkillInputContractKind kind,
        CanonicalSchema schema)
{
    public SkillInputContract
    {
        kind = kind == null ? SkillInputContractKind.GENERIC : kind;
        schema = Objects.requireNonNull(schema, "schema must not be null");
    }

    public static SkillInputContract genericObject()
    {
        return new SkillInputContract(
                SkillInputContractKind.GENERIC,
                new StandardSchemaCompiler().compile("{\"type\":\"object\",\"additionalProperties\":true}",
                        StandardSchemaCompiler.Role.INPUT, StandardSchemaCompiler.Origin.GENERATED));
    }

    public boolean isGeneric()
    {
        return kind == SkillInputContractKind.GENERIC;
    }

    public boolean allowsEmptyInput()
    {
        return schema.tree().path("required").isEmpty();
    }

    public enum SkillInputContractKind
    {
        JAVA_REFLECTED,
        YAML_EXPLICIT,
        GENERIC
    }
}
