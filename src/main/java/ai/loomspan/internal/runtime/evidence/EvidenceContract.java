package ai.loomspan.internal.runtime.evidence;

import tools.jackson.databind.JsonNode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class EvidenceContract
{
    private static final EvidenceContract EMPTY = new EvidenceContract(Map.of());

    private final Map<String, EvidenceExpression> expressionsByClaim;

    private EvidenceContract(Map<String, EvidenceExpression> expressionsByClaim)
    {
        this.expressionsByClaim = Collections.unmodifiableMap(new LinkedHashMap<>(expressionsByClaim));
    }

    public static EvidenceContract empty()
    {
        return EMPTY;
    }

    public static EvidenceContract compiled(Map<String, EvidenceExpression> expressionsByClaim)
    {
        return expressionsByClaim == null || expressionsByClaim.isEmpty()
                ? empty()
                : new EvidenceContract(expressionsByClaim);
    }

    public boolean isEmpty()
    {
        return expressionsByClaim.isEmpty();
    }

    public Set<String> claims()
    {
        return expressionsByClaim.keySet();
    }

    public EvidenceExpression expressionForClaim(String claimName)
    {
        return expressionsByClaim.get(claimName);
    }

    public String canonicalExpressionForClaim(String claimName)
    {
        EvidenceExpression expression = expressionForClaim(claimName);
        return expression == null ? null : expression.canonical();
    }

    public Set<String> presentClaims(JsonNode candidate)
    {
        if (candidate == null || !candidate.isObject())
        {
            return Set.of();
        }
        LinkedHashSet<String> presentClaims = new LinkedHashSet<>();
        candidate.propertyNames().forEach(fieldName ->
        {
            if (expressionsByClaim.containsKey(fieldName))
            {
                presentClaims.add(fieldName);
            }
        });
        return Collections.unmodifiableSet(presentClaims);
    }

    public Map<String, EvidenceExpression> expressionsByClaim()
    {
        return expressionsByClaim;
    }

}
