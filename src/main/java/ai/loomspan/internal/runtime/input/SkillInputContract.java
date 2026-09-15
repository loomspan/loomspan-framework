package ai.loomspan.internal.runtime.input;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record SkillInputContract(
        SkillInputContractKind kind,
        SkillInputSchemaNode schema)
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
                new SkillInputSchemaNode("object", Map.of(), List.of(), Boolean.TRUE, null, null, List.of(), null, null, false));
    }

    public boolean isGeneric()
    {
        return kind == SkillInputContractKind.GENERIC;
    }

    public boolean allowsEmptyInput()
    {
        return schema.required().isEmpty();
    }

    public enum SkillInputContractKind
    {
        JAVA_REFLECTED,
        YAML_EXPLICIT,
        GENERIC
    }
}
