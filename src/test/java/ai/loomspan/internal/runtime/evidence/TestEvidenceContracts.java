package ai.loomspan.internal.runtime.evidence;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TestEvidenceContracts
{
    private TestEvidenceContracts()
    {
    }

    public static EvidenceContract compiled(Map<String, String> expressions)
    {
        EvidenceExpressionParser parser = new EvidenceExpressionParser();
        Map<String, EvidenceExpression> parsed = new LinkedHashMap<>();
        expressions.forEach((claim, expression) ->
        {
            parsed.put(claim, parser.parse(expression));
        });
        return EvidenceContract.compiled(parsed);
    }
}
