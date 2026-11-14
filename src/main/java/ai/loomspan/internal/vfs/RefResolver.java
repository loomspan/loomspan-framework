package ai.loomspan.internal.vfs;

import ai.loomspan.internal.core.LoomspanSession;
import ai.loomspan.internal.runtime.input.SkillInputContract;
import java.util.Map;

public interface RefResolver
{
    Object resolveArgument(Object value, LoomspanSession session);

    @SuppressWarnings("unchecked")
    default Map<String, Object> resolveArguments(Map<String, Object> arguments, LoomspanSession session,
            SkillInputContract contract)
    {
        return (Map<String, Object>) InputReferenceTraversal.resolve(arguments == null ? Map.of() : arguments,
                contract.schema().tree(), contract.isGeneric(), value -> resolveArgument(value, session));
    }
}
